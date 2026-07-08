package sg.edu.nus.iss.wellness.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import sg.edu.nus.iss.wellness.dto.AccountDtos;
import sg.edu.nus.iss.wellness.error.ApiException;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.repository.ChatMessageRepository;
import sg.edu.nus.iss.wellness.repository.RecommendationRepository;
import sg.edu.nus.iss.wellness.repository.WellnessRecordRepository;
import sg.edu.nus.iss.wellness.service.CurrentUserService;
import sg.edu.nus.iss.wellness.service.DtoMapper;

/**
 * Privacy / data-control endpoints for the authenticated user (S-03).
 *
 * <ul>
 *   <li>{@code GET /api/account/export} — download a full JSON copy of the
 *       caller's data (profile, records, recommendations, chat history).</li>
 *   <li>{@code POST /api/account/deactivate} — reversible: hides the account
 *       and blocks sign-in but keeps all data; {@code POST /api/auth/reactivate}
 *       restores it.</li>
 *   <li>{@code DELETE /api/account} — permanent erasure of the account and all
 *       associated data, password-confirmed for local-password accounts.</li>
 * </ul>
 *
 * <p>A wrong password on delete returns {@code 400}, deliberately not
 * {@code 401/403}: the mobile client treats 401/403 as session-expiry and would
 * otherwise log the user out on a simple typo.
 *
 * @author Chua Wei Yi Justin, Tiong Zhong Cheng
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final WellnessRecordRepository wellnessRecords;
    private final RecommendationRepository recommendations;
    private final ChatMessageRepository chatMessages;
    private final sg.edu.nus.iss.wellness.repository.AppUserRepository users;

    public AccountController(CurrentUserService currentUserService,
                            PasswordEncoder passwordEncoder,
                            WellnessRecordRepository wellnessRecords,
                            RecommendationRepository recommendations,
                            ChatMessageRepository chatMessages,
                            sg.edu.nus.iss.wellness.repository.AppUserRepository users) {
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
        this.wellnessRecords = wellnessRecords;
        this.recommendations = recommendations;
        this.chatMessages = chatMessages;
        this.users = users;
    }

    @GetMapping("/export")
    public ResponseEntity<AccountDtos.AccountExport> export() {
        AppUser user = currentUserService.requireCurrentUser();

        AccountDtos.UserProfile profile = new AccountDtos.UserProfile(
                user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole() == null ? null : user.getRole().name(), user.getCreatedAt());

        var records = wellnessRecords.findByUserOrderByRecordDateDesc(user).stream()
                .map(DtoMapper::wellness).toList();
        var recs = recommendations.findByUserOrderByCreatedAtDesc(user).stream()
                .map(DtoMapper::recommendation).toList();
        List<AccountDtos.ChatExport> chats = chatMessages.findByUserOrderByCreatedAtDesc(user).stream()
                .map(message -> new AccountDtos.ChatExport(
                        message.getId(), message.getUserQuestion(), message.getAssistantAnswer(),
                        message.getSourceSummary(), message.getModelName(), message.getCreatedAt()))
                .toList();

        AccountDtos.AccountExport body = new AccountDtos.AccountExport(
                profile, records, recs, chats, Instant.now());

        String filename = "wellness-export-" + user.getId() + "-"
                + LocalDate.now(ZoneId.systemDefault()) + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    @PostMapping("/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate() {
        AppUser user = currentUserService.requireCurrentUser();
        user.setEnabled(false);
        users.save(user);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@Valid @RequestBody AccountDtos.DeleteAccountRequest request) {
        AppUser user = currentUserService.requireCurrentUser();

        String storedHash = user.getPasswordHash();
        if (storedHash == null || storedHash.isBlank()) {
            // SSO-only users have no local app password; the valid JWT is the
            // confirmation after the destructive Android dialog.
            eraseAccount(user);
            return;
        }

        String password = request.password();
        if (password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password confirmation is required");
        }
        if (!passwordEncoder.matches(password, storedHash)) {
            // 400 (not 401/403) so the client does not treat this as session expiry.
            throw new ApiException(HttpStatus.BAD_REQUEST, "Incorrect password");
        }
        eraseAccount(user);
    }

    private void eraseAccount(AppUser user) {
        // Remove children before the user to satisfy the user_id foreign keys.
        chatMessages.deleteByUser(user);
        recommendations.deleteByUser(user);
        wellnessRecords.deleteByUser(user);
        users.delete(user);
    }
}

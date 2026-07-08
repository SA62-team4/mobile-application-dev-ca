package sg.edu.nus.iss.wellness.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import sg.edu.nus.iss.wellness.dto.AuthDtos;
import sg.edu.nus.iss.wellness.error.ApiException;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.security.JwtService;
import sg.edu.nus.iss.wellness.service.DtoMapper;
import sg.edu.nus.iss.wellness.service.GoogleTokenVerifier;

/**
 * Handles account registration and stateless JWT login/logout.
 *
 * @author Kumaraguru Surya, Tiong Zhong Cheng, Chua Wei Yi Justin
 * 
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder,
                          JwtService jwtService, GoogleTokenVerifier googleTokenVerifier) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.UserResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        String email = request.email().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        AppUser user = new AppUser();
        user.setDisplayName(request.displayName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMapper.user(users.save(user)));
    }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        AppUser user = users.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!user.isEnabled()) {
            // Distinct 403 so the client can offer reactivation instead of a generic failure.
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is deactivated. Reactivate to continue.");
        }
        return new AuthDtos.LoginResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.expirySeconds(),
                DtoMapper.user(user)
        );
    }

    /**
     * Reactivates a previously deactivated account and logs the user back in.
     * Verifies the same credentials as login, then re-enables the account so all
     * retained data (records, recommendations, chat history) becomes accessible
     * again. Idempotent: an already-enabled account simply logs in.
     */
    @PostMapping("/reactivate")
    public AuthDtos.LoginResponse reactivate(@Valid @RequestBody AuthDtos.LoginRequest request) {
        AppUser user = users.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!user.isEnabled()) {
            user.setEnabled(true);
            user = users.save(user);
        }
        return new AuthDtos.LoginResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.expirySeconds(),
                DtoMapper.user(user)
        );
    }

    @PostMapping("/google")
    public AuthDtos.LoginResponse googleLogin(@Valid @RequestBody AuthDtos.GoogleAuthRequest request) {
        GoogleTokenVerifier.GoogleUserInfo info;
        try {
            info = googleTokenVerifier.verify(request.idToken());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid Google token: " + e.getMessage());
        }
        String email = info.email().toLowerCase();
        AppUser user = users.findByEmail(email).orElseGet(() -> {
            AppUser newUser = new AppUser();
            newUser.setEmail(email);
            newUser.setDisplayName(info.name() != null && !info.name().isBlank()
                    ? info.name().trim() : email);
            return users.save(newUser);
        });
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is disabled");
        }
        return new AuthDtos.LoginResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.expirySeconds(),
                DtoMapper.user(user)
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Stateless JWT logout is completed by the Android client clearing its stored token.
    }
}


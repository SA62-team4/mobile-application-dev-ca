package sg.edu.nus.iss.wellness.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.wellness.dto.AuthDtos;
import sg.edu.nus.iss.wellness.error.ApiException;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.security.JwtService;
import sg.edu.nus.iss.wellness.service.DtoMapper;

/**
 * Handles account registration and stateless JWT login/logout.
 *
 * @author SA62 Team
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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


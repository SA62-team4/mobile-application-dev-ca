package sg.edu.nus.iss.wellness.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs for authentication endpoints.
 *
 * @author SA62 Team
 */
public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank String displayName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record UserResponse(Long id, String displayName, String email) {
    }

    public record LoginResponse(String token, String tokenType, long expiresInSeconds, UserResponse user) {
    }
}


package sg.edu.nus.iss.wellness.model;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Application user account.
 *
 * @author Tiong Zhong Cheng, Chua Wei Yi Justin, Kumaraguru Surya
 */
@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_email", columnList = "email", unique = true))
public class AppUser implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, name = "display_name")
    private String displayName;

    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(nullable = false, name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == Role.PREMIUM_USER) {
            return List.of(
                    new SimpleGrantedAuthority(Role.USER.authority()),
                    new SimpleGrantedAuthority(Role.PREMIUM_USER.authority()));
        }
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash != null ? passwordHash : "";
    }

    @Override
    public String getUsername() {
        return getEmail();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role != null ? role : Role.USER;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

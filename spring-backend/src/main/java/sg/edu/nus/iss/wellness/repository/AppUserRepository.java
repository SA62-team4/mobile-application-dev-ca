package sg.edu.nus.iss.wellness.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sg.edu.nus.iss.wellness.model.AppUser;

import java.util.Optional;

/**
 * Repository for application users.
 *
 * @author SA62 Team
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
}


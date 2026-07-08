package sg.edu.nus.iss.wellness.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.Recommendation;

/**
 * Repository for agent-generated recommendations.
 *
 * @author Tiong Zhong Cheng, Chua Wei Yi Justin
 */
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByUserOrderByCreatedAtDesc(AppUser user);

    // Used by account deletion to erase all of a user's recommendations in one call.
    long deleteByUser(AppUser user);
}


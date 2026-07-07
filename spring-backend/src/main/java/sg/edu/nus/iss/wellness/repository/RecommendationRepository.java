package sg.edu.nus.iss.wellness.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.Recommendation;

import java.util.List;

/**
 * Repository for agent-generated recommendations.
 *
 * @author Tiong Zhong Cheng
 */
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByUserOrderByCreatedAtDesc(AppUser user);
}


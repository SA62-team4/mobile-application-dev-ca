package sg.edu.nus.iss.wellness.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.WellnessRecord;

/**
 * Repository for user-owned wellness records.
 *
 * @author Tiong Zhong Cheng, Chua Wei Yi Justin
 */
public interface WellnessRecordRepository extends JpaRepository<WellnessRecord, Long> {
    List<WellnessRecord> findByUserOrderByRecordDateDesc(AppUser user);
    List<WellnessRecord> findByUserAndRecordDateBetweenOrderByRecordDateDesc(AppUser user, LocalDate from, LocalDate to);
    List<WellnessRecord> findByUserAndRecordDateAfterOrderByRecordDateDesc(AppUser user, LocalDate from);
    Optional<WellnessRecord> findByIdAndUser(Long id, AppUser user);

    // Used by account deletion to erase all of a user's records in one call.
    long deleteByUser(AppUser user);
}


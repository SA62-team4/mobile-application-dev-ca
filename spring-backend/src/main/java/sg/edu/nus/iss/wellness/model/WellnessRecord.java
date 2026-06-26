package sg.edu.nus.iss.wellness.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * User-owned wellness observation.
 *
 * @author SA62 Team
 */
@Entity
@Table(name = "wellness_records", indexes = @Index(name = "idx_records_user_date", columnList = "user_id,record_date"))
public class WellnessRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, name = "record_date")
    private LocalDate recordDate;

    @Column(nullable = false, precision = 4, scale = 1, name = "sleep_hours")
    private BigDecimal sleepHours;

    @Column(name = "exercise_type")
    private String exerciseType;

    @Column(nullable = false, name = "exercise_minutes")
    private Integer exerciseMinutes;

    @Column(nullable = false, name = "mood_score")
    private Integer moodScore;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(nullable = false, name = "updated_at")
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
    public BigDecimal getSleepHours() { return sleepHours; }
    public void setSleepHours(BigDecimal sleepHours) { this.sleepHours = sleepHours; }
    public String getExerciseType() { return exerciseType; }
    public void setExerciseType(String exerciseType) { this.exerciseType = exerciseType; }
    public Integer getExerciseMinutes() { return exerciseMinutes; }
    public void setExerciseMinutes(Integer exerciseMinutes) { this.exerciseMinutes = exerciseMinutes; }
    public Integer getMoodScore() { return moodScore; }
    public void setMoodScore(Integer moodScore) { this.moodScore = moodScore; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}


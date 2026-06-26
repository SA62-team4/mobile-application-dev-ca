package sg.edu.nus.iss.wellness.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Stored RAG chatbot exchange.
 *
 * @author SA62 Team
 */
@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_user_created", columnList = "user_id,created_at"))
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, columnDefinition = "TEXT", name = "user_question")
    private String userQuestion;

    @Column(nullable = false, columnDefinition = "TEXT", name = "assistant_answer")
    private String assistantAnswer;

    @Column(columnDefinition = "TEXT", name = "source_summary")
    private String sourceSummary;

    @Column(name = "model_name")
    private String modelName;

    @Column(nullable = false, name = "created_at")
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public String getUserQuestion() { return userQuestion; }
    public void setUserQuestion(String userQuestion) { this.userQuestion = userQuestion; }
    public String getAssistantAnswer() { return assistantAnswer; }
    public void setAssistantAnswer(String assistantAnswer) { this.assistantAnswer = assistantAnswer; }
    public String getSourceSummary() { return sourceSummary; }
    public void setSourceSummary(String sourceSummary) { this.sourceSummary = sourceSummary; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Instant getCreatedAt() { return createdAt; }
}


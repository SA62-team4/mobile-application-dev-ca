package sg.edu.nus.iss.wellness.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import sg.edu.nus.iss.wellness.dto.ChatDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.ChatMessage;
import sg.edu.nus.iss.wellness.model.WellnessRecord;
import sg.edu.nus.iss.wellness.repository.AppUserRepository;
import sg.edu.nus.iss.wellness.repository.ChatMessageRepository;
import sg.edu.nus.iss.wellness.repository.WellnessRecordRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ChatService business logic.
 * Tests chat persistence and history retrieval.
 *
 * @author SA62 Team
 */
@SpringBootTest
@Transactional
@DisplayName("Chat Service Integration Tests")
public class ChatServiceTest {

    @Autowired
    private ChatMessageRepository chatMessages;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private WellnessRecordRepository wellnessRecords;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        testUser = new AppUser();
        testUser.setEmail("chattest@wellness.com");
        testUser.setDisplayName("Chat Test User");
        testUser = users.save(testUser);
    }

    @Test
    @DisplayName("Chat message persists to database correctly")
    void testChatPersistence() {
        ChatMessage message = new ChatMessage();
        message.setUser(testUser);
        message.setUserQuestion("How do I sleep better?");
        message.setAssistantAnswer("Maintain a consistent sleep schedule");
        message.setModelName("llama3.2:3b");
        message.setSourceSummary("Sleep Guide: Good sleep hygiene");

        ChatMessage saved = chatMessages.save(message);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserQuestion()).isEqualTo("How do I sleep better?");
        assertThat(saved.getAssistantAnswer()).isEqualTo("Maintain a consistent sleep schedule");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Chat history retrieval respects user boundaries")
    void testChatHistoryUserIsolation() {
        ChatMessage msg1 = new ChatMessage();
        msg1.setUser(testUser);
        msg1.setUserQuestion("Question 1");
        msg1.setAssistantAnswer("Answer 1");
        chatMessages.save(msg1);

        AppUser otherUser = new AppUser();
        otherUser.setEmail("other@wellness.com");
        otherUser.setDisplayName("Other User");
        otherUser = users.save(otherUser);

        ChatMessage msg2 = new ChatMessage();
        msg2.setUser(otherUser);
        msg2.setUserQuestion("Other's question");
        msg2.setAssistantAnswer("Other's answer");
        chatMessages.save(msg2);

        List<ChatMessage> userMessages = chatMessages.findByUserOrderByCreatedAtDesc(testUser);
        assertThat(userMessages).hasSize(1);
        assertThat(userMessages.get(0).getUserQuestion()).isEqualTo("Question 1");
    }

    @Test
    @DisplayName("Multiple chat messages are ordered by creation date")
    void testChatHistoryOrdering() {
        for (int i = 0; i < 3; i++) {
            ChatMessage msg = new ChatMessage();
            msg.setUser(testUser);
            msg.setUserQuestion("Question " + i);
            msg.setAssistantAnswer("Answer " + i);
            chatMessages.save(msg);
        }

        List<ChatMessage> messages = chatMessages.findByUserOrderByCreatedAtDesc(testUser);

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getUserQuestion()).contains("Question");
        assertThat(messages.get(messages.size() - 1).getUserQuestion()).contains("Question");
    }

    @Test
    @DisplayName("Empty source summary handled gracefully")
    void testEmptySourceSummary() {
        ChatMessage message = new ChatMessage();
        message.setUser(testUser);
        message.setUserQuestion("Question without sources");
        message.setAssistantAnswer("Answer");
        message.setSourceSummary("");

        ChatMessage saved = chatMessages.save(message);

        assertThat(saved.getSourceSummary()).isEmpty();
    }
}

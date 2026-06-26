package sg.edu.nus.iss.wellness.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.ChatMessage;

import java.util.List;

/**
 * Repository for RAG chatbot history.
 *
 * @author SA62 Team
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserOrderByCreatedAtDesc(AppUser user);
}


package sg.edu.nus.iss.wellness.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.model.ChatMessage;

/**
 * Repository for RAG chatbot history.
 *
 * @author Tiong Zhong Cheng, Chua Wei Yi Justin
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserOrderByCreatedAtDesc(AppUser user);

    // Used by account deletion to erase all of a user's chat history in one call.
    long deleteByUser(AppUser user);
}


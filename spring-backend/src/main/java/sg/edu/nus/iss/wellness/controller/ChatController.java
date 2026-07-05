package sg.edu.nus.iss.wellness.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.wellness.dto.ChatDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.service.ChatService;
import sg.edu.nus.iss.wellness.service.CurrentUserService;

import java.util.List;

/**
 * REST endpoints for chatbot interactions with RAG service.
 * Delegates business logic to ChatService.
 *
 * @author SA62 Team
 */
@RestController
@RequestMapping("/api/chat/messages")
public class ChatController {
    private final CurrentUserService currentUserService;
    private final ChatService chatService;

    public ChatController(CurrentUserService currentUserService,
                          ChatService chatService) {
        this.currentUserService = currentUserService;
        this.chatService = chatService;
    }

    /**
     * Ask a question and retrieve RAG-based response with wellness context.
     *
     * @param request the chat question request
     * @return chat response with answer and sources
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ChatDtos.ChatResponse ask(@Valid @RequestBody ChatDtos.ChatRequest request) {
        AppUser user = currentUserService.requireCurrentUser();
        return chatService.askQuestion(user, request.question());
    }

    /**
     * Retrieve chat history for the authenticated user.
     *
     * @return list of chat messages ordered by newest first
     */
    @GetMapping
    public List<ChatDtos.ChatResponse> list() {
        AppUser user = currentUserService.requireCurrentUser();
        return chatService.getChatHistory(user);
    }
}


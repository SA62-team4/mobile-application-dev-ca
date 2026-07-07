package sg.edu.nus.iss.wellness.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sg.edu.nus.iss.wellness.dto.ChatDtos;
import sg.edu.nus.iss.wellness.model.AppUser;
import sg.edu.nus.iss.wellness.service.ChatService;
import sg.edu.nus.iss.wellness.service.ChatStreamService;
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
    private final ChatStreamService chatStreamService;

    public ChatController(CurrentUserService currentUserService,
                          ChatService chatService,
                          ChatStreamService chatStreamService) {
        this.currentUserService = currentUserService;
        this.chatService = chatService;
        this.chatStreamService = chatStreamService;
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
     * Ask a question and stream the RAG-based answer token-by-token as Server-Sent Events.
     * The assembled exchange is persisted server-side once the stream completes, so a
     * subsequent history load reflects the same message.
     *
     * @param request the chat question request
     * @return an SSE stream of {@code sources}, {@code token}, and terminal {@code done}/{@code error} frames
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatDtos.ChatRequest request) {
        AppUser user = currentUserService.requireCurrentUser();
        return chatStreamService.stream(user, request.question());
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


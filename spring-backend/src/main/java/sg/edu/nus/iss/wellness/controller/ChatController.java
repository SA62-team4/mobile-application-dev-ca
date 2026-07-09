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
 * Chatbot REST endpoints.
 *
 * @author Tiong Zhong Cheng, Kumaraguru Surya
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

    /** Ask a question and return the RAG answer. */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ChatDtos.ChatResponse ask(@Valid @RequestBody ChatDtos.ChatRequest request) {
        AppUser user = currentUserService.requireCurrentUser();
        return chatService.askQuestion(user, request.question(),
                request.latitude(), request.longitude());
    }

    /** Stream a RAG answer as Server-Sent Events. */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatDtos.ChatRequest request) {
        AppUser user = currentUserService.requireCurrentUser();
        return chatStreamService.stream(user, request.question(),
                request.latitude(), request.longitude());
    }

    /** List chat history for the current user. */
    @GetMapping
    public List<ChatDtos.ChatResponse> list() {
        AppUser user = currentUserService.requireCurrentUser();
        return chatService.getChatHistory(user);
    }
}

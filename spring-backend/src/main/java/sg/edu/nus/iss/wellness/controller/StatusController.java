package sg.edu.nus.iss.wellness.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public status endpoint for quick browser checks during local development.
 *
 * @author SA62 Team
 */
@RestController
public class StatusController {
    @GetMapping("/")
    public Map<String, String> status() {
        return Map.of(
                "service", "wellness-backend",
                "status", "UP",
                "health", "/actuator/health"
        );
    }
}

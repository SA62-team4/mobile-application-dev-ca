package sg.edu.nus.iss.wellness.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import sg.edu.nus.iss.wellness.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the premium AI client: enable/disable gating and
 * fail-soft fallback semantics.
 *
 * @author Tang Chee Seng (with Claude)
 */
@DisplayName("Premium AI Client Tests")
class PremiumAiClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private AppProperties props(String url, String secret) {
        AppProperties p = new AppProperties();
        p.setPremiumAiUrl(url);
        p.setPremiumAiSecret(secret);
        return p;
    }

    @Test
    @DisplayName("Disabled when premium URL is blank")
    void disabledWhenNoUrl() {
        PremiumAiClient client = new PremiumAiClient(props("", "s"), RestClient.builder(), mapper);
        assertThat(client.isEnabled()).isFalse();
        // Streaming call short-circuits to false without touching the network.
        assertThat(client.premiumStreamChat("q", "c", "r", null, null, d -> {})).isFalse();
    }

    @Test
    @DisplayName("Enabled when premium URL is set")
    void enabledWhenUrlSet() {
        PremiumAiClient client = new PremiumAiClient(
                props("http://127.0.0.1:8000", "s"), RestClient.builder(), mapper);
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Unreachable node → premiumChat returns null (caller falls back)")
    void unreachableReturnsNull() {
        // Port 1 is unbindable → connection fails fast → caught → null.
        PremiumAiClient client = new PremiumAiClient(
                props("http://127.0.0.1:1", "s"), RestClient.builder(), mapper);
        assertThat(client.premiumChat("q", "c", "r", null, null)).isNull();
    }
}

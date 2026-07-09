package sg.edu.nus.iss.wellness.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link Role} authority formatting and the lenient {@code fromValue} parser.
 *
 * @author Tiong Zhong Cheng
 */
@DisplayName("Role Tests")
class RoleTest {

    @Test
    @DisplayName("authority() prefixes the role name with ROLE_")
    void authorityIsPrefixed() {
        assertThat(Role.USER.authority()).isEqualTo("ROLE_USER");
        assertThat(Role.PREMIUM_USER.authority()).isEqualTo("ROLE_PREMIUM_USER");
    }

    @Test
    @DisplayName("fromValue() parses a known role case-insensitively")
    void fromValueParsesKnownRole() {
        assertThat(Role.fromValue("premium_user")).isEqualTo(Role.PREMIUM_USER);
        assertThat(Role.fromValue("  USER  ")).isEqualTo(Role.USER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "unknown", "admin"})
    @DisplayName("fromValue() falls back to USER for missing or unrecognised values")
    void fromValueFallsBackToUser(String value) {
        assertThat(Role.fromValue(value)).isEqualTo(Role.USER);
    }
}

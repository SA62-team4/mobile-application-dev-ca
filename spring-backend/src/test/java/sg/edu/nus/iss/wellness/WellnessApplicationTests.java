package sg.edu.nus.iss.wellness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the application entry point remains available to tests.
 *
 * @author Tiong Zhong Cheng
 */
class WellnessApplicationTests {
    @Test
    void applicationClassExists() {
        assertThat(WellnessApplication.class.getName())
                .isEqualTo("sg.edu.nus.iss.wellness.WellnessApplication");
    }
}

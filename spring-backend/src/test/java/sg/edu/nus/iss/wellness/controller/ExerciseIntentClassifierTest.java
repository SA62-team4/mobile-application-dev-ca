package sg.edu.nus.iss.wellness.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import sg.edu.nus.iss.wellness.service.ExerciseIntentClassifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the exercise-intent keyword classifier.
 *
 * @author Tang Chee Seng (with Claude)
 */
@DisplayName("Exercise Intent Classifier Tests")
class ExerciseIntentClassifierTest {

    private final ExerciseIntentClassifier classifier = new ExerciseIntentClassifier();

    @ParameterizedTest
    @ValueSource(strings = {
        "Is it safe to exercise outside?", "Can I go for a run today?",
        "What's the weather like for cycling?", "How hot is it for a jog?"
    })
    @DisplayName("Exercise/weather questions are detected")
    void detectsExerciseQuestions(String q) {
        assertThat(classifier.isExerciseRelated(q)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "How can I improve my sleep?", "What should I eat for breakfast?",
        "Tell me about meditation."
    })
    @DisplayName("Non-exercise questions are not detected")
    void ignoresNonExerciseQuestions(String q) {
        assertThat(classifier.isExerciseRelated(q)).isFalse();
    }

    @Test
    @DisplayName("Null / blank is safe and returns false")
    void handlesNullAndBlank() {
        assertThat(classifier.isExerciseRelated(null)).isFalse();
        assertThat(classifier.isExerciseRelated("   ")).isFalse();
    }

    @Test
    @DisplayName("Whole-word match — 'runny nose' does not trigger on 'run'")
    void wordBoundaryPrecision() {
        // \b(run)\b matches "run" as a word; "running" also matches (separate keyword).
        assertThat(classifier.isExerciseRelated("I have a runny nose")).isFalse();
    }
}

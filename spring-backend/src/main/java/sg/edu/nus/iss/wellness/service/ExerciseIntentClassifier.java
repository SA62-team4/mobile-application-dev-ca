package sg.edu.nus.iss.wellness.service;

import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Keyword classifier: is a chatbot question about exercise / outdoor activity?
 * Used by premium routing to decide whether to send a question to the local
 * weather agent (which has weather tools) or the standard RAG chatbot.
 *
 * @author Tang Chee Seng
 */

@Component
public class ExerciseIntentClassifier {

    private static final Set<String> KEYWORDS = Set.of(
            "exercise", "workout", "run", "running", "jog", "jogging",
            "walk", "walking", "swim", "swimming", "cycle", "cycling",
            "gym", "outdoor", "outside", "weather", "temperature",
            "wbgt", "wet bulb", "heat", "hot", "humid",
            "sport", "training", "fitness", "cardio", "forecast", "rain",
            "strength", "cloudy", "sunny", "drizzle", "downpour", "yoga",
            "pilates", "hike", "hiking", "hyrox", "marathon", "stress", 
            "leisure", "leisurely", "competition", "competitive", "frisbee",
            "soccer", "football", "basketball", "softball", "track", "golf",
            "golfing", "gardening", "garden", "ultimate"
            );

    private static final Pattern PATTERN = Pattern.compile(
            KEYWORDS.stream()
                    .map(Pattern::quote)
                    .reduce((a, b) -> a + "|" + b)
                    .map(combined -> "\\b(" + combined + ")\\b")
                    .orElse("(?!)"),
            Pattern.CASE_INSENSITIVE);

    public boolean isExerciseRelated(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        return PATTERN.matcher(question).find();
    }
}
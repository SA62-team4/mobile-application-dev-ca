package sg.edu.nus.iss.wellness

/**
 * Canonical exercise type choices for wellness record forms.
 *
 * @author Abu Bakar Nasir
 */
object ExerciseTypeOptions {
    private const val NONE = "No exercise"

    val options = listOf(
        NONE,
        "Walking",
        "Running",
        "Cycling",
        "Swimming",
        "Strength training",
        "Yoga",
        "Sports",
        "Other"
    )

    private val aliases = mapOf(
        "walk" to "Walking",
        "walking" to "Walking",
        "run" to "Running",
        "running" to "Running",
        "jog" to "Running",
        "jogging" to "Running",
        "cycle" to "Cycling",
        "cycling" to "Cycling",
        "bike" to "Cycling",
        "biking" to "Cycling",
        "swim" to "Swimming",
        "swimming" to "Swimming",
        "gym" to "Strength training",
        "weights" to "Strength training",
        "strength" to "Strength training",
        "strength training" to "Strength training",
        "yoga" to "Yoga",
        "sport" to "Sports",
        "sports" to "Sports",
        "other" to "Other"
    )

    fun selectedIndexFor(storedValue: String?): Int {
        val normalized = storedValue?.trim().orEmpty().lowercase()
        val option = aliases[normalized] ?: if (normalized.isBlank()) NONE else "Other"
        return options.indexOf(option).takeIf { it >= 0 } ?: 0
    }

    fun requestValueAt(index: Int): String? {
        val option = options.getOrElse(index) { NONE }
        return option.takeUnless { it == NONE }
    }
}

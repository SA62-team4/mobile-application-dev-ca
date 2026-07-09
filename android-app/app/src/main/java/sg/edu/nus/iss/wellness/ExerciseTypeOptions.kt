package sg.edu.nus.iss.wellness

/**
 * Canonical exercise type choices for wellness record forms.
 *
 * @author Abu Bakar Nasir
 */
object ExerciseTypeOptions {
    private const val NONE = "No exercise"
    private const val WALKING = "Walking"
    private const val RUNNING = "Running"
    private const val CYCLING = "Cycling"
    private const val SWIMMING = "Swimming"
    private const val STRENGTH = "Strength training"
    private const val YOGA = "Yoga"
    private const val SPORTS = "Sports"
    private const val OTHER = "Other"

    val options = listOf(
        NONE, WALKING, RUNNING, CYCLING, SWIMMING, STRENGTH, YOGA, SPORTS, OTHER
    )

    private val aliases = mapOf(
        "walk" to WALKING,
        "walking" to WALKING,
        "run" to RUNNING,
        "running" to RUNNING,
        "jog" to RUNNING,
        "jogging" to RUNNING,
        "cycle" to CYCLING,
        "cycling" to CYCLING,
        "bike" to CYCLING,
        "biking" to CYCLING,
        "swim" to SWIMMING,
        "swimming" to SWIMMING,
        "gym" to STRENGTH,
        "weights" to STRENGTH,
        "strength" to STRENGTH,
        "strength training" to STRENGTH,
        "yoga" to YOGA,
        "sport" to SPORTS,
        "sports" to SPORTS,
        "other" to OTHER
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

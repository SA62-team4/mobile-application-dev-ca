package sg.edu.nus.iss.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the canonical exercise type list used by the record form.
 *
 * @author Abu Bakar Nasir
 */
class ExerciseTypeOptionsTest {
    @Test
    fun `blank exercise type maps to no exercise`() {
        assertEquals(0, ExerciseTypeOptions.selectedIndexFor(""))
        assertNull(ExerciseTypeOptions.requestValueAt(0))
    }

    @Test
    fun `legacy short exercise values map to canonical options`() {
        val runningIndex = ExerciseTypeOptions.options.indexOf("Running")
        val strengthIndex = ExerciseTypeOptions.options.indexOf("Strength training")

        assertEquals(runningIndex, ExerciseTypeOptions.selectedIndexFor("run"))
        assertEquals(strengthIndex, ExerciseTypeOptions.selectedIndexFor("gym"))
    }

    @Test
    fun `unknown stored exercise type maps to other`() {
        assertEquals(
            ExerciseTypeOptions.options.indexOf("Other"),
            ExerciseTypeOptions.selectedIndexFor("Pilates")
        )
    }
}

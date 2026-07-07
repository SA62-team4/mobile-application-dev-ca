package sg.edu.nus.iss.wellness.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import sg.edu.nus.iss.wellness.R
import sg.edu.nus.iss.wellness.api.WellnessRecordResponse

/**
 * Row adapter for the wellness records list, with per-row Edit/Delete callbacks.
 *
 * @author Abu Bakar Nasir
 */
class RecordsAdapter(
    context: Context,
    private val items: List<WellnessRecordResponse>,
    private val onEdit: (WellnessRecordResponse) -> Unit,
    private val onDelete: (WellnessRecordResponse) -> Unit
) : ArrayAdapter<WellnessRecordResponse>(context, 0, items) {

    private class ViewHolder(view: View) {
        val date: TextView = view.findViewById(R.id.recordDate)
        val summary: TextView = view.findViewById(R.id.recordSummary)
        val notes: TextView = view.findViewById(R.id.recordNotes)
        val editButton: Button = view.findViewById(R.id.editButton)
        val deleteButton: Button = view.findViewById(R.id.deleteButton)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder
        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.row_record, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val record = items[position]
        holder.date.text = record.recordDate
        holder.summary.text = "Sleep ${record.sleepHours}h | ${record.exerciseType ?: "No exercise"} ${record.exerciseMinutes}min | Mood ${record.moodScore}/5"
        holder.notes.text = record.notes.orEmpty().ifBlank { "No notes added." }
        holder.editButton.setOnClickListener { onEdit(record) }
        holder.deleteButton.setOnClickListener { onDelete(record) }

        return view
    }
}

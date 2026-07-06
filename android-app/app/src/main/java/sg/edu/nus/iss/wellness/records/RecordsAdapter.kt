package sg.edu.nus.iss.wellness.records

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import sg.edu.nus.iss.wellness.api.WellnessRecordResponse
import sg.edu.nus.iss.wellness.databinding.ItemWellnessRecordBinding

/**
 * ListView adapter for wellness record cards with edit/delete actions.
 *
 * @author SA62 Team
 */
class RecordsAdapter(
    context: Context,
    private val onEdit: (WellnessRecordResponse) -> Unit = {},
    private val onDelete: (WellnessRecordResponse) -> Unit = {}
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private var items: List<WellnessRecordResponse> = emptyList()

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): WellnessRecordResponse = items[position]

    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: ViewHolder
        val rowView: View
        if (convertView == null) {
            val binding = ItemWellnessRecordBinding.inflate(inflater, parent, false)
            holder = ViewHolder(binding)
            rowView = binding.root
            rowView.tag = holder
        } else {
            rowView = convertView
            holder = rowView.tag as ViewHolder
        }

        holder.bind(getItem(position), onEdit, onDelete)
        return rowView
    }

    fun submitList(list: List<WellnessRecordResponse>) {
        items = list
        notifyDataSetChanged()
    }

    private class ViewHolder(
        private val binding: ItemWellnessRecordBinding
    ) {
        fun bind(
            record: WellnessRecordResponse,
            onEdit: (WellnessRecordResponse) -> Unit,
            onDelete: (WellnessRecordResponse) -> Unit
        ) {
            binding.recordDateText.text = record.recordDate
            binding.sleepText.text = "Sleep: ${record.sleepHours} hours"
            binding.exerciseText.text = if (record.exerciseType.isNullOrBlank()) {
                "Exercise: -"
            } else {
                "Exercise: ${record.exerciseType} • ${record.exerciseMinutes} min"
            }
            binding.moodText.text = "Mood: ${record.moodScore}/5"
            binding.notesText.text = record.notes.orEmpty().ifBlank { "No notes added." }
            binding.editButton.setOnClickListener { onEdit(record) }
            binding.deleteButton.setOnClickListener { onDelete(record) }
            binding.root.setOnClickListener { onEdit(record) }
        }
    }
}

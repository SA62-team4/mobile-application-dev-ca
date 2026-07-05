package sg.edu.nus.iss.wellness.records

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import sg.edu.nus.iss.wellness.api.WellnessRecordResponse

/**
 * Simple RecordsAdapter that supports submitList and edit/delete callbacks.
 * Uses android.R.layout.simple_list_item_2 for a compact two-line item.
 */
class RecordsAdapter(
    private val onEdit: (WellnessRecordResponse) -> Unit,
    private val onDelete: (WellnessRecordResponse) -> Unit
) : RecyclerView.Adapter<RecordsAdapter.ViewHolder>() {

    private val items = mutableListOf<WellnessRecordResponse>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val subtitle: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = items[position]
        holder.title.text = record.recordDate
        holder.subtitle.text = "Sleep ${record.sleepHours}h · ${record.exerciseMinutes}min · Mood ${record.moodScore}/5"

        holder.itemView.setOnClickListener { onEdit(record) }
        holder.itemView.setOnLongClickListener {
            onDelete(record)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(list: List<WellnessRecordResponse>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}

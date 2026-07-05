package sg.edu.nus.iss.wellness.records

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import sg.edu.nus.iss.wellness.api.WellnessRecordResponse

/**
 * Simple RecordsAdapter to display WellnessRecordResponse items with edit/delete callbacks.
 */
class RecordsAdapter(
    private val onEdit: (WellnessRecordResponse) -> Unit = {},
    private val onDelete: (WellnessRecordResponse) -> Unit = {}
) : RecyclerView.Adapter<RecordsAdapter.ViewHolder>() {

    private var items: List<WellnessRecordResponse> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView? = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = items[position]
        holder.title?.text = "${record.recordDate} — Mood ${record.moodScore}/5"
        holder.itemView.setOnClickListener { onEdit(record) }
        holder.itemView.setOnLongClickListener {
            onDelete(record)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(list: List<WellnessRecordResponse>) {
        items = list
        notifyDataSetChanged()
    }
}

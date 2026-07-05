package sg.edu.nus.iss.wellness.records

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Minimal RecordsAdapter stub so the project compiles. Replace with real binding/logic later.
 */
class RecordsAdapter(private val items: List<Any> = emptyList()) : RecyclerView.Adapter<RecordsAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView? = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        (holder.title)?.text = items[position].toString()
    }

    override fun getItemCount(): Int = items.size
}

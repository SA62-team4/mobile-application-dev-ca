package sg.edu.nus.iss.wellness.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import sg.edu.nus.iss.wellness.R
import sg.edu.nus.iss.wellness.api.ChatResponse

/**
 * Row adapter for the chat history list. Each row holds one question/answer pair.
 *
 * T-405 enhancement: source snippets render as Material chips instead of a plain caption.
 *
 * @author SA62 Team
 * @author Tang Chee Seng (T-405: source snippets as Material chips)
 */
class ChatAdapter(
    context: Context,
    private val items: List<ChatResponse>
) : ArrayAdapter<ChatResponse>(context, 0, items) {

    private class ViewHolder(view: View) {
        val userMessage: TextView = view.findViewById(R.id.userMessage)
        val assistantMessage: TextView = view.findViewById(R.id.assistantMessage)
        val sourceChips: ChipGroup = view.findViewById(R.id.sourceChips)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder
        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.row_chat_message, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val message = items[position]
        holder.userMessage.text = message.question
        holder.assistantMessage.text = message.answer

        // Clear first: recycled rows carry the previous message's chips (the ghost-chip bug).
        holder.sourceChips.removeAllViews()
        val sources = message.sources.orEmpty()
        if (sources.isNotEmpty()) {
            holder.sourceChips.visibility = View.VISIBLE
            sources.forEach { source ->
                val chip = Chip(context)
                chip.text = source.title
                chip.isClickable = false
                chip.isCheckable = false
                holder.sourceChips.addView(chip)
            }
        } else {
            holder.sourceChips.visibility = View.GONE
        }

        return view
    }
}

package sg.edu.nus.iss.wellness.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.google.android.material.chip.ChipGroup
import sg.edu.nus.iss.wellness.R
import sg.edu.nus.iss.wellness.api.ChatResponse

/**
 * Row adapter for the chat history list. Each row holds one question/answer pair.
 *
 * @author Tiong Zhong Cheng, Tang Chee Seng, Abu Bakar Nasir
 */
class ChatAdapter(
    context: Context,
    initialItems: List<ChatResponse>
) : ArrayAdapter<ChatResponse>(context, 0, initialItems.toMutableList()) {

    private class ViewHolder(view: View) {
        val userMessage: TextView = view.findViewById(R.id.userMessage)
        val assistantMessage: TextView = view.findViewById(R.id.assistantMessage)
        val sourceChips: ChipGroup = view.findViewById(R.id.sourceChips)
    }

    /** Replace all rows and repaint. Used for history loads and live streaming updates. */
    fun submit(items: List<ChatResponse>) {
        clear()
        addAll(items)
        notifyDataSetChanged()
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

        val message = getItem(position) ?: return view
        holder.userMessage.text = message.question
        holder.assistantMessage.text = message.answer

        // Source snippets are intentionally not shown in the UI. Clear any chips a recycled
        // row still holds and keep the group hidden (the answer already reads standalone).
        holder.sourceChips.removeAllViews()
        holder.sourceChips.visibility = View.GONE

        return view
    }
}

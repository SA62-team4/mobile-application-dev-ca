package sg.edu.nus.iss.wellness.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import sg.edu.nus.iss.wellness.R
import sg.edu.nus.iss.wellness.api.ChatResponse

/**
 * Row adapter for the chat history list. Each row holds one question/answer pair.
 *
 * @author SA62 Team
 */
class ChatAdapter(
    context: Context,
    private val items: List<ChatResponse>
) : ArrayAdapter<ChatResponse>(context, 0, items) {

    private class ViewHolder(view: View) {
        val userMessage: TextView = view.findViewById(R.id.userMessage)
        val assistantMessage: TextView = view.findViewById(R.id.assistantMessage)
        val sourcesCaption: TextView = view.findViewById(R.id.sourcesCaption)
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

        val sources = message.sources.orEmpty()
        if (sources.isNotEmpty()) {
            holder.sourcesCaption.visibility = View.VISIBLE
            holder.sourcesCaption.text = "Sources: ${sources.joinToString { it.title }}"
        } else {
            holder.sourcesCaption.visibility = View.GONE
        }

        return view
    }
}

package sg.edu.nus.iss.wellness.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import sg.edu.nus.iss.wellness.R
import sg.edu.nus.iss.wellness.api.RecommendationResponse

/**
 * Row adapter for the recommendations list.
 *
 * @author SA62 Team
 */
class RecommendationAdapter(
    context: Context,
    private val items: List<RecommendationResponse>
) : ArrayAdapter<RecommendationResponse>(context, 0, items) {

    private class ViewHolder(view: View) {
        val title: TextView = view.findViewById(R.id.recTitle)
        val trendSummary: TextView = view.findViewById(R.id.recTrendSummary)
        val text: TextView = view.findViewById(R.id.recText)
        val actionsLabel: TextView = view.findViewById(R.id.recActionsLabel)
        val actionItems: TextView = view.findViewById(R.id.recActionItems)
        val createdAt: TextView = view.findViewById(R.id.recCreatedAt)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder
        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.row_recommendation, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val rec = items[position]
        holder.title.text = rec.title
        holder.trendSummary.text = rec.trendSummary
        holder.text.text = rec.recommendationText

        if (rec.actionItems.isNotEmpty()) {
            holder.actionsLabel.visibility = View.VISIBLE
            holder.actionItems.visibility = View.VISIBLE
            holder.actionItems.text = rec.actionItems.joinToString("\n") { "- $it" }
        } else {
            holder.actionsLabel.visibility = View.GONE
            holder.actionItems.visibility = View.GONE
        }

        if (rec.createdAt != null) {
            holder.createdAt.visibility = View.VISIBLE
            holder.createdAt.text = "Generated ${rec.createdAt}"
        } else {
            holder.createdAt.visibility = View.GONE
        }

        return view
    }
}

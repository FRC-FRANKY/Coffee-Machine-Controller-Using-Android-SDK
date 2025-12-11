package com.cuppa.iot.ui.adapters

import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.cuppa.iot.R
import com.cuppa.iot.domain.models.Notification

/**
 * RecyclerView Adapter for displaying a list of Notification items.
 * Handles binding Notification data to the card layout and managing click events.
 */
class NotificationAdapter(
    private var items: MutableList<Notification>, // Mutable list of notification items displayed by the adapter
    private val clickListener: (Notification) -> Unit // Callback function executed when an item is clicked
) : RecyclerView.Adapter<NotificationAdapter.NotifViewHolder>() {

    /**
     * ViewHolder holds and initializes the views for a single notification item card.
     */
    inner class NotifViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvNotificationTitle)
        val message: TextView = view.findViewById(R.id.tvNotificationMessage)
        val icon: ImageView = view.findViewById(R.id.ivNotificationIcon)
        val status: View = view.findViewById(R.id.viewStatus)
    }

    /**
     * Creates the ViewHolder by inflating the item layout.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotifViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification_card, parent, false)
        return NotifViewHolder(view)
    }

    /**
     * Binds the data from the Notification object to the views in the ViewHolder.
     */
    override fun onBindViewHolder(holder: NotifViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.message.text = item.message
        holder.icon.setImageResource(R.drawable.ic_brewing)
        holder.status.visibility = View.VISIBLE // You can toggle this based on read/unread

        // Highlight urgent notifications in red
        if (item.title.contains("cold", ignoreCase = true) || item.message.contains("cold", ignoreCase = true)) {
            holder.title.setTextColor(holder.itemView.context.getColor(R.color.red))
        } else {
            holder.title.setTextColor(holder.itemView.context.getColor(R.color.americano))
        }

        // --- Clickable Card Implementation ---
        holder.itemView.setOnClickListener {
            // Call presenter's click handler, which marks as read and removes the item.
            clickListener(item)
        }
    }

    /**
     * Returns the total number of items in the adapter's data set.
     */
    override fun getItemCount(): Int = items.size

    /**
     * Clears existing data, adds new data, and notifies the RecyclerView to refresh.
     */
    fun updateData(newData: List<Notification>) {
        items.clear()
        items.addAll(newData)
        notifyDataSetChanged()
    }

    /** Returns the Notification item at a specific position. */
    fun getItemAt(position: Int): Notification = items[position]

    /** Returns an immutable copy of all items currently in the adapter. */
    fun getAllItems(): List<Notification> = items.toList()
}
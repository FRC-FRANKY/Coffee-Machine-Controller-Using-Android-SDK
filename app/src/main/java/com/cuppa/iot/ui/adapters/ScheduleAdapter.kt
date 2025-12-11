package com.cuppa.iot.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.cuppa.iot.R
import com.cuppa.iot.domain.models.Schedule
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView Adapter for displaying a list of brewing schedules.
 * Provides callbacks for enabling/disabling, editing, and deleting schedules.
 * Uses DiffUtil for efficient list updates.
 */
class ScheduleAdapter(
    private var schedules: List<Schedule>,
    private val onToggle: (Schedule, Boolean) -> Unit,
    private val onEdit: (Schedule) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    /**
     * ViewHolder holds and initializes the views for a single schedule item card.
     */
    class ScheduleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvScheduleTime)
        val tvRepeat: TextView = view.findViewById(R.id.tvScheduleRepeat)
        val switchEnabled: Switch = view.findViewById(R.id.switchScheduleEnabled)
        val btnEdit: Button = view.findViewById(R.id.btnEdit)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    /**
     * Creates the ViewHolder by inflating the schedule item layout.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_card, parent, false)
        return ScheduleViewHolder(view)
    }

    /**
     * Binds the Schedule object data to the views and sets up click listeners.
     */
    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val schedule = schedules[position]

        holder.tvTime.text = to12Hour(schedule.time)
        holder.tvRepeat.text = formatRepeat(schedule.repeat)

        holder.switchEnabled.setOnCheckedChangeListener(null)
        holder.switchEnabled.isChecked = schedule.enabled
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggle(schedule, isChecked)
        }

        holder.btnEdit.setOnClickListener { onEdit(schedule) }
        holder.btnDelete.setOnClickListener { schedule.id?.let(onDelete) }
    }

    /**
     * Returns the total number of schedules in the data set.
     */
    override fun getItemCount() = schedules.size

    /**
     * Updates the adapter's data set using DiffUtil to efficiently calculate and animate changes.
     */
    fun updateData(newSchedules: List<Schedule>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = schedules.size
            override fun getNewListSize() = newSchedules.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                schedules[oldItemPosition].id == newSchedules[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                schedules[oldItemPosition] == newSchedules[newItemPosition]
        })
        schedules = newSchedules
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Converts a raw time string (e.g., "14:30") into 12-hour format (e.g., "2:30 PM").
     */
    private fun to12Hour(raw: String): String {
        return try {
            // Skip conversion if the string already contains AM/PM
            if (raw.contains("AM", true) || raw.contains("PM", true)) {
                raw
            } else {
                val df24 = SimpleDateFormat("HH:mm", Locale.getDefault())
                val df12 = SimpleDateFormat("h:mm a", Locale.getDefault())
                val parsed = df24.parse(raw)
                if (parsed != null) df12.format(parsed) else raw
            }
        } catch (_: Exception) {
            raw // Return raw if parsing fails
        }
    }

    /**
     * Formats the raw comma-separated repeat string (e.g., "Mon,Tue") into a user-friendly format.
     * Special cases: "Daily" or empty list ("One-time").
     */
    private fun formatRepeat(repeat: String): String {
        val days = repeat.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            repeat.equals("Daily", true) || days.size >= 7 -> "Daily"
            days.isEmpty() -> "One-time"
            else -> days.joinToString(", ")
        }
    }
}

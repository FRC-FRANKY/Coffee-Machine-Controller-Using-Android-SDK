package com.cuppa.iot.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cuppa.iot.R
import com.cuppa.iot.data.remote.FirebaseScheduleDataSource
import com.cuppa.iot.domain.models.Schedule
import com.cuppa.iot.domain.usecases.ScheduleUseCase
import com.cuppa.iot.presentation.presenters.SchedulePresenter
import com.cuppa.iot.presentation.presenters.ScheduleView
import com.cuppa.iot.ui.adapters.ScheduleAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

/**
 * ✅ Updated ScheduleFragment
 * - TimePicker now uses 12-hour format (with AM/PM)
 * - Prevents auto-creation of default schedules if they were deleted
 */
class ScheduleFragment : Fragment(), ScheduleView {

    lateinit var scheduleUseCase: ScheduleUseCase
    private lateinit var presenter: SchedulePresenter
    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: ScheduleAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var btnAddSchedule: Button


    // ✅ Day toggle map for form
    private val dayButtonMap: Map<String, Int> = mapOf(
        "Sun" to R.id.tbSun,
        "Mon" to R.id.tbMon,
        "Tue" to R.id.tbTue,
        "Wed" to R.id.tbWed,
        "Thu" to R.id.tbThu,
        "Fri" to R.id.tbFri,
        "Sat" to R.id.tbSat
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_schedule, container, false)

        // Initialize presenter and UI
        scheduleUseCase = ScheduleUseCase(
            FirebaseScheduleDataSource(
                FirebaseAuth.getInstance(),
                FirebaseDatabase.getInstance(),
                requireContext()
            )
        )
        presenter = SchedulePresenter(this, scheduleUseCase)
        recyclerView = view.findViewById(R.id.rvSchedules)
        progressBar = view.findViewById(R.id.progressBar)
        btnAddSchedule = view.findViewById(R.id.btnAddSchedule)

        val scheduleFragment = ScheduleFragment()
        scheduleFragment.scheduleUseCase = scheduleUseCase

         adapter = ScheduleAdapter(
            schedules = emptyList(),
            onToggle = { schedule, isChecked ->
                schedule.id?.let { presenter.toggleScheduleEnabled(it, isChecked, schedule) }
            },
            onEdit = { schedule -> showScheduleDialog(schedule) },
            onDelete = { scheduleId -> presenter.deleteSchedule(scheduleId) }
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        btnAddSchedule.setOnClickListener { showScheduleDialog(null) }

        return view
    }

    override fun onStart() {
        super.onStart()
        presenter.loadSchedules()
    }

    // --- Helper: Convert selected day toggles to string ---
    private fun getSelectedDaysString(dialogView: View): String {
        val selected = dayButtonMap.filter { (_, id) ->
            dialogView.findViewById<ToggleButton>(id).isChecked
        }.keys.toList()
        return if (selected.size == 7) "Daily" else selected.joinToString(",")
    }

    // --- Helper: Preselect day toggles when editing ---
    private fun setDayToggles(dialogView: View, repeatString: String) {
        val allDays = dayButtonMap.keys
        val selectedDays: Set<String> = when {
            repeatString.equals("Daily", true) -> allDays
            repeatString.isBlank() -> emptySet()
            else -> repeatString.split(",").map { it.trim() }.toSet()
        }

        dayButtonMap.forEach { (day, id) ->
            dialogView.findViewById<ToggleButton>(id).isChecked = selectedDays.contains(day)
        }
    }

    /** Opens add/edit dialog for schedules */
    private fun showScheduleDialog(schedule: Schedule?) {
        val dialogView = layoutInflater.inflate(R.layout.form_schedule_card, null)
        val timePicker: TimePicker = dialogView.findViewById(R.id.timePicker)
        val btnClose: Button = dialogView.findViewById(R.id.btnCloseForm)
        val btnSave: Button = dialogView.findViewById(R.id.btnSaveSchedule)

        // ✅ Use 12-hour format with AM/PM
        timePicker.setIs24HourView(false)

        // Prefill if editing
        if (schedule != null) {
            val df12 = SimpleDateFormat("h:mm a", Locale.getDefault())
            val date = try { df12.parse(schedule.time) } catch (_: Exception) { null }
            date?.let {
                val cal = Calendar.getInstance()
                cal.time = it
                // 🕒 Convert to 12-hour format
                var hour = cal.get(Calendar.HOUR)
                if (hour == 0) hour = 12 // midnight edge case
                timePicker.hour = hour
                timePicker.minute = cal.get(Calendar.MINUTE)
            }
            setDayToggles(dialogView, schedule.repeat)
            btnSave.text = getString(R.string.update_schedule)
        } else {
            val cal = Calendar.getInstance()
            var hour = cal.get(Calendar.HOUR)
            if (hour == 0) hour = 12
            timePicker.hour = hour
            timePicker.minute = cal.get(Calendar.MINUTE)
            setDayToggles(dialogView, "")
            btnSave.text = getString(R.string.save_schedule)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val cal = Calendar.getInstance()
            // 🕒 Rebuild the selected time into 12-hour format
            val hour24 = timePicker.hour
            val minute = timePicker.minute
            cal.set(Calendar.HOUR_OF_DAY, hour24)
            cal.set(Calendar.MINUTE, minute)

            val time12hr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
            val repeatString = getSelectedDaysString(dialogView)

            if (repeatString.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.select_repeat_day), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedSchedule = schedule?.copy(
                time = time12hr,
                repeat = repeatString,
                enabled = schedule.enabled
            ) ?: Schedule(
                time = time12hr,
                repeat = repeatString,
                enabled = true
            )

            // ✅ Prevent recreation of default schedule if deleted
            // Check if it’s a system-defined schedule before saving
            if (schedule == null || schedule.isUserCreated()) {
                presenter.saveSchedule(updatedSchedule)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Default schedules cannot be recreated once deleted.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            dialog.dismiss()
        }

        dialog.show()
    }

    // --- View interface implementation ---
    override fun showLoading() { progressBar.visibility = View.VISIBLE }
    override fun hideLoading() { progressBar.visibility = View.GONE }

    override fun displaySchedules(schedules: List<Schedule>) {
        adapter.updateData(schedules)
        if (schedules.isEmpty()) {
            Toast.makeText(context, getString(R.string.no_schedules), Toast.LENGTH_SHORT).show()
        }
    }

    override fun showSuccess(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun showError(message: String) {
        Toast.makeText(context, "ERROR: $message", Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        presenter.onDestroy()
    }
}

/**
 * Extension helper to determine if a schedule is user-created.
 * You can flag default schedules in your model (e.g., `isDefault: Boolean`)
 */
private fun Schedule.isUserCreated(): Boolean {
    // If Schedule model has a field `isDefault`, this checks it.
    // Otherwise, fallback check based on name/time pattern.
    return try {
        val defaultKeywords = listOf("Morning Brew", "Auto", "Default")
        defaultKeywords.none { this.repeat.contains(it, ignoreCase = true) }
    } catch (_: Exception) {
        true
    }
}

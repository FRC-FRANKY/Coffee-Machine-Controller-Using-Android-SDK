package com.cuppa.iot.ui.fragments

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
// New Imports for Permission Handling
import android.os.Build
import android.Manifest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
// End New Imports
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.cuppa.iot.R
import com.cuppa.iot.data.remote.FirebaseNotificationDataSource
import com.cuppa.iot.domain.models.Notification
import com.cuppa.iot.domain.usecases.NotificationUseCase
import com.cuppa.iot.presentation.presenters.NotificationPresenter
import com.cuppa.iot.presentation.presenters.NotificationView
import com.cuppa.iot.ui.adapters.NotificationAdapter
import com.cuppa.iot.utils.InAppNotificationHelper
import com.cuppa.iot.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class NotificationFragment : Fragment(), NotificationView {

    private lateinit var useCase: NotificationUseCase
    private lateinit var presenter: NotificationPresenter

    private lateinit var rvToday: RecyclerView
    private lateinit var rvWeek: RecyclerView
    private lateinit var adapterToday: NotificationAdapter
    private lateinit var adapterWeek: NotificationAdapter

    private lateinit var emptyStateText: TextView
    private lateinit var btnClearAll: Button

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var inAppNotificationHelper: InAppNotificationHelper

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    // 💡 NEW: Initialize the Activity Result Launcher
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        useCase = NotificationUseCase(
            FirebaseNotificationDataSource(
                FirebaseAuth.getInstance(),
                FirebaseDatabase.getInstance()
            )
        )

        presenter = NotificationPresenter(this, useCase)

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                // User denied permission. Inform them of the consequences.
                Toast.makeText(requireContext(), "Notifications denied. Real-time alerts will be disabled.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_notification, container, false)

        btnClearAll = view.findViewById(R.id.btnClearAll)
        emptyStateText = view.findViewById(R.id.tvEmptyState)

        rvToday = view.findViewById(R.id.rvTodayNotifications)
        rvWeek = view.findViewById(R.id.rvWeekNotifications)

        // Initialize notification helpers
        notificationHelper = NotificationHelper(requireContext())
        inAppNotificationHelper = InAppNotificationHelper(view)

        val clickListener: (Notification) -> Unit = { notif ->
            presenter.onNotificationClicked(notif)
            Toast.makeText(requireContext(), "Notification deleted", Toast.LENGTH_SHORT).show()
        }

        adapterToday = NotificationAdapter(mutableListOf(), clickListener)
        adapterWeek = NotificationAdapter(mutableListOf(), clickListener)

        rvToday.layoutManager = LinearLayoutManager(requireContext())
        rvWeek.layoutManager = LinearLayoutManager(requireContext())
        rvToday.adapter = adapterToday
        rvWeek.adapter = adapterWeek

        setupSwipeToDelete(rvToday)
        setupSwipeToDelete(rvWeek)

        btnClearAll.setOnClickListener {
            val allNotifs = adapterToday.getAllItems() + adapterWeek.getAllItems()
            if (allNotifs.isEmpty()) {
                Toast.makeText(requireContext(), "No notifications to clear", Toast.LENGTH_SHORT).show()
            } else {
                showClearAllConfirmation(allNotifs)
            }
        }

        return view
    }

    // Request permission in onStart and start listening
    override fun onStart() {
        super.onStart()
        requestPermission()
        presenter.startListening()
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ===================== VIEW IMPLEMENTATION ====================== //

    override fun showInstantNotification(eventType: String) {
        when (eventType) {
            "done" -> {
                inAppNotificationHelper.showBrewDone()
                notificationHelper.showBrewDoneNotification()
            }
            "coffee_cold" -> {
                inAppNotificationHelper.showCoffeeGoneCold()
                notificationHelper.showCoffeeGoneColdNotification()
            }
            "started" -> inAppNotificationHelper.showBrewStarted()
            "paused" -> inAppNotificationHelper.showBrewPaused()
            "resumed" -> inAppNotificationHelper.showBrewResumed()
            "cancelled" -> inAppNotificationHelper.showBrewCancelled()
            "offline" -> {
                inAppNotificationHelper.showOffline()
                notificationHelper.showOfflineNotification()
            }
        }
    }


    override fun showTodayNotifications(list: List<Notification>) {
        adapterToday.updateData(list)
        checkIfEmpty()
    }

    override fun showWeekNotifications(list: List<Notification>) {
        adapterWeek.updateData(list)
        checkIfEmpty()
    }

    override fun showError(message: String) {
        Toast.makeText(requireContext(), "ERROR: $message", Toast.LENGTH_SHORT).show()
    }

    private fun checkEmpty() {
        val empty = adapterToday.itemCount == 0 && adapterWeek.itemCount == 0
        emptyStateText.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun showClearAllConfirmation(notifications: List<Notification>) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear All Notifications")
            .setMessage("Are you sure you want to delete all ${notifications.size} notifications?")
            .setPositiveButton("Clear") { _, _ ->
                // Clear UI instantly
                adapterToday.updateData(emptyList())
                adapterWeek.updateData(emptyList())
                checkIfEmpty()

                // Then delete from Firebase in background
                presenter.clearAllNotifications(notifications)
                Toast.makeText(requireContext(), "All notifications cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun setupSwipeToDelete(recyclerView: RecyclerView) {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)  {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val adapter = recyclerView.adapter as NotificationAdapter
                val notification = adapter.getItemAt(viewHolder.adapterPosition)
                presenter.deleteNotification(notification)
                Toast.makeText(requireContext(), "Notification deleted", Toast.LENGTH_SHORT).show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun checkIfEmpty() {
        val hasTodayItems = adapterToday.itemCount > 0
        val hasWeekItems = adapterWeek.itemCount > 0
        val isEmpty = !hasTodayItems && !hasWeekItems

        val tvToday = view?.findViewById<TextView>(R.id.tvToday)
        val tvThisWeek = view?.findViewById<TextView>(R.id.tvThisWeek)

        if (isEmpty) {
            emptyStateText.visibility = View.VISIBLE
            rvToday.visibility = View.GONE
            rvWeek.visibility = View.GONE
            tvToday?.visibility = View.GONE
            tvThisWeek?.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE

            // Toggle visibility based on content
            rvToday.visibility = if (hasTodayItems) View.VISIBLE else View.GONE
            tvToday?.visibility = if (hasTodayItems) View.VISIBLE else View.GONE

            rvWeek.visibility = if (hasWeekItems) View.VISIBLE else View.GONE
            tvThisWeek?.visibility = if (hasWeekItems) View.VISIBLE else View.GONE
        }
    }
}
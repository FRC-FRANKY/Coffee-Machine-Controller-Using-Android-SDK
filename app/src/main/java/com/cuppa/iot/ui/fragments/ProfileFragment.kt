package com.cuppa.iot.ui.fragments

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cuppa.iot.R
import com.cuppa.iot.data.remote.FirebaseNotificationDataSource
import com.cuppa.iot.data.remote.FirebaseProfileDataSource
import com.cuppa.iot.data.remote.FirebaseScheduleDataSource
import com.cuppa.iot.domain.models.UserProfile
import com.cuppa.iot.domain.usecases.NotificationUseCase
import com.cuppa.iot.domain.usecases.ProfileUseCase
import com.cuppa.iot.domain.usecases.ScheduleUseCase
import com.cuppa.iot.presentation.presenters.ProfilePresenter
import com.cuppa.iot.presentation.presenters.ProfileView
import com.cuppa.iot.presentation.state.BrewStateViewModel
import com.cuppa.iot.ui.activities.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment(), ProfileView {

    private lateinit var presenter: ProfilePresenter

    private lateinit var tvUsername: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvIdNumber: TextView
    private lateinit var switchAutoStart: Switch
    private lateinit var switchTempAlert: Switch
    private lateinit var tvBrewTime: TextView
    private lateinit var btnEdit: Button
    private lateinit var btnLogout: Button
    private lateinit var tvTemp: TextView
    private lateinit var btnAdjustBrew: Button

    private var currentProfile: UserProfile? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Initialize presenter with use cases
        presenter = ProfilePresenter(
            ProfileUseCase(FirebaseProfileDataSource(FirebaseAuth.getInstance(), FirebaseDatabase.getInstance())),
            NotificationUseCase(FirebaseNotificationDataSource(FirebaseAuth.getInstance(), FirebaseDatabase.getInstance())),
            ScheduleUseCase(FirebaseScheduleDataSource(FirebaseAuth.getInstance(), FirebaseDatabase.getInstance(), requireContext()))
        )
        presenter.attachView(this)

        tvUsername = view.findViewById(R.id.tvUsername)
        tvEmail = view.findViewById(R.id.tvEmail)
        tvIdNumber = view.findViewById(R.id.tvIdNumber)
        switchAutoStart = view.findViewById(R.id.switchAutoStart)
        switchTempAlert = view.findViewById(R.id.switchTempAlert)
        tvBrewTime = view.findViewById(R.id.tvBrewTime)
        btnEdit = view.findViewById(R.id.btnEditProfile)
        btnLogout = view.findViewById(R.id.btnLogout)
        tvTemp = view.findViewById(R.id.tvTemperature)
        btnAdjustBrew = view.findViewById(R.id.btnAdjustBrew)

        btnEdit.setOnClickListener { showEditDialog() }
        btnLogout.setOnClickListener { showLogoutDialog() }
        btnAdjustBrew.setOnClickListener { showTimePicker() }

        switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            presenter.toggleAutoStart(isChecked)
        }

        switchTempAlert.setOnCheckedChangeListener { _, isChecked ->
            presenter.toggleTempAlert(isChecked)
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        presenter.loadProfile()
        presenter.observeTemperature()
        presenter.observeCoffeeStatus()
    }


    /** Handle logout via presenter */
    private fun handleLogout() {
        // Disable logout button to prevent double clicks
        btnLogout.isEnabled = false

        // Use presenter to handle logout
        presenter.logout()
    }


    private fun showLogoutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_logout_confirmation, null)
        val btnNo = dialogView.findViewById<Button>(R.id.btnNo)
        val btnYes = dialogView.findViewById<Button>(R.id.btnYes)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnNo.setOnClickListener { dialog.dismiss() }
        btnYes.setOnClickListener {
            dialog.dismiss()
            handleLogout() // Use the dedicated handler
        }

        dialog.show()
    }

    /** Edit username dialog only; email is read-only, ID displayed */

    private fun showEditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val edtUsername = dialogView.findViewById<EditText>(R.id.edtUsername)
        val edtEmail = dialogView.findViewById<EditText>(R.id.edtEmail)
        val tvIdNumberDisplay = dialogView.findViewById<TextView>(R.id.tvIdNumberDisplay)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)

        // Populate current data
        edtUsername.setText(tvUsername.text)
        edtEmail.setText(tvEmail.text)
        // Make email read-only
        edtEmail.isEnabled = false
        edtEmail.isFocusable = false
        edtEmail.isClickable = false
        tvIdNumberDisplay.text = currentProfile?.idNumber ?: "N/A"

        // Allow copying ID
        tvIdNumberDisplay.setOnLongClickListener {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("User ID", tvIdNumberDisplay.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "User ID copied to clipboard", Toast.LENGTH_SHORT).show()
            true
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newUsername = edtUsername.text.toString().trim()
            if (newUsername.isEmpty()) {
                Toast.makeText(requireContext(), "Username cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val email = currentProfile?.email ?: ""
            val idNumber = currentProfile?.idNumber ?: ""

            // Immediate UI feedback
            tvUsername.text = newUsername
            currentProfile = currentProfile?.copy(username = newUsername)

            presenter.saveProfile(newUsername, email, idNumber)
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * ✅ Shows a 12-hour AM/PM time picker pre-filled with the current brew time.
     * Ensures correct conversion between 24-hour & 12-hour display.
     */
    private fun showTimePicker() {
        val calendar = Calendar.getInstance()

        // ✅ Load saved brew time or fallback to 7:00 AM
        val currentTime = currentProfile?.brewDefaultTime ?: "7:00 AM"
        val sdf12 = SimpleDateFormat("h:mm a", Locale.getDefault())

        try {
            val parsedDate = sdf12.parse(currentTime)
            if (parsedDate != null) {
                val cal = Calendar.getInstance().apply { time = parsedDate }
                calendar.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
                calendar.set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // ✅ Always show 12-hour format
        val timePicker = TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
            val selectedCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, selectedHour)
                set(Calendar.MINUTE, selectedMinute)
            }

            // ✅ Format correctly in 12-hour style (handles AM/PM properly)
            val displayFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val formattedTime = displayFormat.format(selectedCal.time)

            // ✅ Update UI immediately
            tvBrewTime.text = formattedTime

            // ✅ Update local cache
            currentProfile = currentProfile?.copy(brewDefaultTime = formattedTime)

            // ✅ Push change to Firebase via presenter
            presenter.updateBrewTime(formattedTime)

        }, hour, minute, false) // false = use 12-hour view

        timePicker.show()
    }


    // ---- ProfileView Implementation ----
    override fun displayProfile(user: UserProfile) {
        currentProfile = user
        tvUsername.text = user.username
        tvEmail.text = user.email
        tvIdNumber.text = user.idNumber
        tvBrewTime.text = user.brewDefaultTime
        switchAutoStart.isChecked = user.autoStart
        switchTempAlert.isChecked = user.tempAlert
    }

    override fun updateTemperatureDisplay(temp: String) {
        tvTemp.text = "Plate Temp: $temp"
    }

    override fun navigateToLogin() {
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
    }

    override fun showLoading() {}
    override fun hideLoading() {}

    override fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun showSuccess(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun updateAutoStartSwitch(enabled: Boolean) {
        switchAutoStart.isChecked = enabled
    }

    override fun updateTempAlertSwitch(enabled: Boolean) {
        switchTempAlert.isChecked = enabled
    }

    override fun updateBrewTimeDisplay(time: String) {
        tvBrewTime.text = time
        currentProfile = currentProfile?.copy(brewDefaultTime = time)
    }

    override fun onDestroyView() {
        presenter.onDestroy()
        presenter.detachView()
        super.onDestroyView()
    }
}

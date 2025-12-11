package com.cuppa.iot.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.cuppa.iot.R
import com.cuppa.iot.data.remote.FirebaseBrewDataSource
import com.cuppa.iot.data.remote.FirebaseUserProfileDataSource
import com.cuppa.iot.domain.usecases.BrewUseCase
import com.cuppa.iot.domain.usecases.UserProfileUseCase
import com.cuppa.iot.presentation.presenters.HomePresenter
import com.cuppa.iot.presentation.presenters.HomeView
import com.cuppa.iot.presentation.state.BrewStateViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeFragment : Fragment(), HomeView {

    private lateinit var presenter: HomePresenter
    private lateinit var brewStateViewModel: BrewStateViewModel

    private lateinit var greetingText: TextView
    private lateinit var brewButton: Button
    private lateinit var brewStatus: TextView
    private lateinit var temperatureText: TextView
    private lateinit var timerText: TextView
    private lateinit var timerContainer: View
    private lateinit var coffeeIcon: ImageView

    private var idleJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // --- Initialize UI components ---
        greetingText = view.findViewById(R.id.tvGreeting)
        brewButton = view.findViewById(R.id.btnBrewCoffee)
        brewStatus = view.findViewById(R.id.tvBrewStatus)
        temperatureText = view.findViewById(R.id.tvTemperature)
        timerText = view.findViewById(R.id.tvTimer)
        timerContainer = view.findViewById(R.id.timerPickerContainer)
        coffeeIcon = view.findViewById(R.id.ivCoffeeIcon)

        // --- MANUAL DEPENDENCY INJECTION CHAIN ---
        val brewRepository = FirebaseBrewDataSource()
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val db = com.google.firebase.database.FirebaseDatabase.getInstance()
        val userProfileRepository = FirebaseUserProfileDataSource(auth, db)

        val brewUseCase = BrewUseCase(brewRepository)
        val userProfileUseCase = UserProfileUseCase(userProfileRepository)

        // 💡 Initialize State Manager and Presenter
        brewStateViewModel = ViewModelProvider(requireActivity())[BrewStateViewModel::class.java]
        presenter = HomePresenter(this, brewUseCase, userProfileUseCase, brewStateViewModel)

        // --- Bind LiveData to UI ---
        brewStateViewModel.timerText.observe(viewLifecycleOwner) { timerText.text = it }
        brewStateViewModel.brewStatus.observe(viewLifecycleOwner) { brewStatus.text = it }
        brewStateViewModel.buttonLabel.observe(viewLifecycleOwner) { brewButton.text = it }
        brewStateViewModel.iconState.observe(viewLifecycleOwner) { state ->
            if (state == null) return@observe

            when (state) {
                BrewStateViewModel.BrewIconState.BREWING -> {
                    coffeeIcon.setImageResource(R.drawable.ic_brewing)
                    coffeeIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.caramel1))
                }
                BrewStateViewModel.BrewIconState.COMPLETE -> {
                    coffeeIcon.setImageResource(R.drawable.ic_unbrew)
                    coffeeIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.coffee1))
                }
                BrewStateViewModel.BrewIconState.IDLE -> {
                    coffeeIcon.setImageResource(R.drawable.ic_unbrew)
                    coffeeIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.caramel1))
                }
            }
        }

        presenter.loadUserProfile()

        // --- Brew button click toggles start/pause/resume ---
        brewButton.setOnClickListener {
            presenter.onBrewButtonClicked()
        }

        // Allow user to choose brew timer duration
        val timerClickListener = View.OnClickListener { showTimerPicker() }
        timerText.setOnClickListener(timerClickListener)
        timerContainer.setOnClickListener(timerClickListener)

        return view
    }

    override fun onStart() {
        super.onStart()
        presenter.observeEsp32Status()
        presenter.observeCoffeeTemperature()
    }

    override fun onDestroyView() {
        presenter.onDestroy()   // <-- add this
        idleJob?.cancel()
        super.onDestroyView()
    }

    // --- HomeView Implementation ---
    override fun updateStatus(status: String) {
        brewStatus.text = status
    }

    override fun showGreeting(message: String) { greetingText.text = message }
    override fun updateButton(label: String) { brewButton.text = label }
    override fun updateStats(temp: Int, timer: String) {
        temperatureText.text = getString(R.string.temperature_format, temp)
        timerText.text = timer
    }

    override fun setBrewingIcon() {
        coffeeIcon.setImageResource(R.drawable.ic_brewing)
        coffeeIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.caramel1))
    }

    override fun setCompleteIcon() {
        coffeeIcon.setImageResource(R.drawable.ic_unbrew)
        coffeeIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.coffee1))
    }

    override fun updateTemperature(temp: String) {
        temperatureText.text = temp
    }

    // HomeView methods safely update UI only if fragment exists
    override fun showPopOutDialog(title: String, message: String) {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun showLoading() {}
    override fun hideLoading() {}
    override fun showError(message: String) {
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
    override fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showTimerPicker() {
        if (!isAdded) return
        val options = arrayOf("1 minute", "3 minutes", "5 minutes", "10 minutes")
        val minutes = arrayOf(1, 3, 5, 10)

        AlertDialog.Builder(requireContext())
            .setTitle("Select brew time")
            .setItems(options) { _, which ->
                val selected = minutes[which]
                brewStateViewModel.setBrewDurationMinutes(selected)
                showMessage("Brew timer set to $selected minute(s)")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}

package com.cuppa.iot.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.cuppa.iot.R
import com.cuppa.iot.presentation.state.BrewStateViewModel
import com.cuppa.iot.ui.fragments.HomeFragment
import com.cuppa.iot.ui.fragments.NotificationFragment
import com.cuppa.iot.ui.fragments.ProfileFragment
import com.cuppa.iot.ui.fragments.ScheduleFragment
import com.cuppa.iot.utils.InAppNotificationHelper
import com.cuppa.iot.utils.NotificationHelper
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * MainActivity hosts the main user interface with bottom navigation and a fragment container.
 * It uses a FragmentManager strategy of show()/hide() to preserve fragment state and brewing continuity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var brewStateViewModel: BrewStateViewModel
    private var activeFragment: Fragment? = null
    private lateinit var inAppNotificationHelper: InAppNotificationHelper
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize notification utilities
        inAppNotificationHelper = InAppNotificationHelper(findViewById(android.R.id.content))
        notificationHelper = NotificationHelper(this)

        // Avoid fragment duplication after configuration change (e.g., screen rotation)
        if (savedInstanceState != null) return

        brewStateViewModel = ViewModelProvider(this)[BrewStateViewModel::class.java] // Initialize the shared ViewModel

        // Initialize all four destination fragments
        val homeFragment = HomeFragment()
        val scheduleFragment = ScheduleFragment()
        val notificationFragment = NotificationFragment()
        val profileFragment = ProfileFragment()

        // Add all fragments to the container but hide the non-initial ones
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, profileFragment, "profile").hide(profileFragment)
            .add(R.id.fragmentContainer, notificationFragment, "notification").hide(notificationFragment)
            .add(R.id.fragmentContainer, scheduleFragment, "schedule").hide(scheduleFragment)
            .add(R.id.fragmentContainer, homeFragment, "home")
            .commit()

        activeFragment = homeFragment

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        // Set up listener for bottom navigation item selection
        bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment = when (item.itemId) {
                R.id.nav_home -> homeFragment
                R.id.nav_schedule -> scheduleFragment
                R.id.nav_notification -> notificationFragment
                R.id.nav_profile -> profileFragment
                else -> homeFragment
            }

            // Perform the show/hide transaction only if the selected fragment is different from the active one
            if (selectedFragment != activeFragment) {
                supportFragmentManager.beginTransaction()
                    .hide(activeFragment!!)
                    .show(selectedFragment)
                    .commit()
                activeFragment = selectedFragment // Update the active fragment reference
            }
            true // Indicate that the selection was handled
        }

        // Handle navigation intents (e.g., from the VerificationWatcherActivity)
        if (intent.getBooleanExtra("open_home", false)) {
            bottomNav.selectedItemId = R.id.nav_home
        }
    }
}

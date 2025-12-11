package com.cuppa.iot.ui.activities

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cuppa.iot.R
import com.cuppa.iot.data.remote.FirebaseAuthDataSource
import com.cuppa.iot.domain.usecases.AuthUseCase
import com.cuppa.iot.presentation.presenters.LoginPresenter
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth

/**
 * Android Activity responsible for handling user login with email/password and Google One Tap.
 * Implements the LoginView interface and acts as the entry point for the LoginPresenter.
 */
class LoginActivity : AppCompatActivity(), LoginView {

    private lateinit var presenter: LoginPresenter // The Presenter managing login business logic

    // Google One Tap specific fields
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest

    lateinit var authUseCase: AuthUseCase // Google One Tap specific fields

    // UI elements
    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvRegister: TextView
    private lateinit var tvForgotPassword: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnGoogleSignIn: SignInButton
    private lateinit var chkRemember: CheckBox

    private var isPasswordVisible = false // State for password visibility

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // MVP setup: Initialize Repository, UseCase, and Presenter
        val authRepository = FirebaseAuthDataSource(applicationContext)
        val authUseCase = AuthUseCase(authRepository)
        presenter = LoginPresenter(this, authUseCase)

        // Initialize UI components by ID
        edtEmail = findViewById(R.id.edtUsername)
        edtPassword = findViewById(R.id.edtPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvRegister = findViewById(R.id.tvRegister)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        progressBar = findViewById(R.id.progressBar)
        chkRemember = findViewById(R.id.chkRemember)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)

        // Load and pre-fill saved credentials if 'remember me' was checked last time
        val prefs = getSharedPreferences("authPrefs", MODE_PRIVATE)
        val savedEmail = prefs.getString("email", "")
        val savedPassword = prefs.getString("password", "")
        if (!savedEmail.isNullOrBlank() && !savedPassword.isNullOrBlank()) {
            edtEmail.setText(savedEmail)
            edtPassword.setText(savedPassword)
            chkRemember.isChecked = true
        }

        setupPasswordToggle()

        // Initialize Google One Tap client and request builder
        oneTapClient = Identity.getSignInClient(this)
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

        // --- Event Listeners ---
        btnGoogleSignIn.setOnClickListener { launchGoogleSignInIntent() }

        btnLogin.setOnClickListener {
            // Delegate login attempt to the presenter
            presenter.onLoginClicked(
                edtEmail.text.toString().trim(),
                edtPassword.text.toString(),
                chkRemember.isChecked
            )
        }

        tvForgotPassword.setOnClickListener {
            // Delegate password reset request to the presenter
            val email = edtEmail.text.toString().trim()
            presenter.onResetPasswordRequested(email)
        }

        tvRegister.setOnClickListener {
            // Navigate to the Register screen
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    /**
     * Sets up the functionality to toggle password visibility when the user taps the drawableEnd icon.
     */
    private fun setupPasswordToggle() {
        edtPassword.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = edtPassword.compoundDrawables[2]
                if (drawableEnd != null && event.rawX >= (edtPassword.right - drawableEnd.bounds.width())) {
                    isPasswordVisible = !isPasswordVisible
                    edtPassword.inputType = if (isPasswordVisible)
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    else
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    edtPassword.setSelection(edtPassword.text.length)
                    edtPassword.setCompoundDrawablesWithIntrinsicBounds(
                        0, 0,
                        if (isPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
                        0
                    )
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    override fun showLoading() { progressBar.visibility = ProgressBar.VISIBLE }
    override fun hideLoading() { progressBar.visibility = ProgressBar.GONE }
    override fun togglePasswordVisibility() { setupPasswordToggle() }
    override fun showMessage(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    override fun showError(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    override fun navigateToMain() {
        // Navigate to MainActivity and destroy the current activity
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * Initiates the Google One Tap sign-in process.
     * Starts the flow and handles the success/failure listeners.
     */
    override fun launchGoogleSignInIntent() {
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener { result ->
                // Launch the pending intent to show the Google One Tap UI
                signInLauncher.launch(IntentSenderRequest.Builder(result.pendingIntent).build())
            }
            .addOnFailureListener { e ->
                showError("Google Sign-In failed: ${e.localizedMessage}")
            }
    }

    /**
     * Activity Result Launcher for handling the result of the Google One Tap sign-in intent.
     */
    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                try {
                    val credential = oneTapClient.getSignInCredentialFromIntent(result.data) // Extract the sign-in credential from the result data
                    val idToken = credential.googleIdToken
                    // If a token is received, delegate authentication to the presenter
                    if (!idToken.isNullOrEmpty()) presenter.onGoogleSignInResult(idToken)
                    else showError("Google Sign-In failed: Missing token.")
                } catch (e: ApiException) {
                    // Handle API exceptions during credential extraction (e.g., user cancelled)
                    showError("Google Sign-In error: ${e.localizedMessage}")
                }
            }
        }

    override fun onStart() {
        super.onStart()
        // Check for an existing verified Firebase session and bypass login if found (Auto-login)
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && user.isEmailVerified) navigateToMain()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up presenter resources (cancel coroutines)
        presenter.onDestroy()
    }
}

/**
 * View contract for the Login screen (MVP View).
 * Defines methods the presenter uses to update the UI and trigger navigation.
 */
interface LoginView {
    fun showLoading()
    fun hideLoading()
    fun togglePasswordVisibility()
    fun showMessage(message: String)
    fun showError(message: String)
    fun navigateToMain()
    fun launchGoogleSignInIntent()
}

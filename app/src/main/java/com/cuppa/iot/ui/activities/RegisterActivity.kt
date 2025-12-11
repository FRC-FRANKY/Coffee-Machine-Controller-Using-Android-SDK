package com.cuppa.iot.ui.activities

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.cuppa.iot.R
import com.cuppa.iot.data.remote.FirebaseAuthDataSource
import com.cuppa.iot.domain.usecases.AuthUseCase
import com.cuppa.iot.presentation.presenters.RegisterPresenter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException

/**
 * Android Activity responsible for handling user registration with email/password and Google sign-up.
 * Implements the RegisterView interface and integrates with the RegisterPresenter.
 */
class RegisterActivity : AppCompatActivity(), RegisterView {

    private lateinit var presenter: RegisterPresenter // The Presenter managing registration logic

    lateinit var authUseCase: AuthUseCase // UseCase dependency (initialized below)

    // UI elements
    private lateinit var edtUsername: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var edtConfirmPassword: EditText
    private lateinit var btnSignup: Button
    private lateinit var tvLogin: TextView
    private lateinit var btnGoogleSignUp: SignInButton
    private lateinit var googleSignInClient: GoogleSignInClient // Client for standard Google Sign-In
    private lateinit var progressBar: ProgressBar

    // State for password visibility toggles
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    private val RC_SIGN_UP = 9002 // Request code for the Google Sign-In intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // MVP setup: Initialize Repository, UseCase, and Presenter
        val authRepository = FirebaseAuthDataSource(applicationContext)
        val authUseCase = AuthUseCase(authRepository)
        presenter = RegisterPresenter(this, authUseCase)

        // Initialize UI components
        edtUsername = findViewById(R.id.edtUsername)
        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        edtConfirmPassword = findViewById(R.id.edtCPassword)
        btnSignup = findViewById(R.id.btnSignup)
        tvLogin = findViewById(R.id.tvLogin)
        btnGoogleSignUp = findViewById(R.id.btnGoogleSignUp)
        progressBar = findViewById(R.id.progressBar)

        togglePasswordVisibility() // Initial setup of the password toggle touch listeners

        btnSignup.setOnClickListener {
            // Delegate email/password registration attempt to the presenter
            presenter.onRegisterClicked(
                username = edtUsername.text.toString().trim(),
                email = edtEmail.text.toString().trim(),
                password = edtPassword.text.toString(),
                confirmPassword = edtConfirmPassword.text.toString()
            )
        }

        // Configure Google Sign-In options to request an ID token (required for Firebase authentication)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnGoogleSignUp.setOnClickListener {
            // Sign out any previous Google account and then start the sign-in flow
            googleSignInClient.signOut().addOnCompleteListener {
                startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_UP)
            }
        }

        tvLogin.setOnClickListener {
            // Navigate to the Login screen
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    /**
     * Sets up the touch listener for the main password field to toggle visibility.
     */
    private fun setupPasswordToggle() {
        edtPassword.setOnTouchListener { _, event ->
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
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    /**
     * Sets up the touch listener for the confirm password field to toggle visibility.
     */
    private fun setupConfirmPasswordToggle() {
        edtConfirmPassword.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = edtConfirmPassword.compoundDrawables[2]
                if (drawableEnd != null && event.rawX >= (edtConfirmPassword.right - drawableEnd.bounds.width())) {
                    isConfirmPasswordVisible = !isConfirmPasswordVisible
                    edtConfirmPassword.inputType = if (isConfirmPasswordVisible)
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    else
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    edtConfirmPassword.setSelection(edtConfirmPassword.text.length)
                    edtConfirmPassword.setCompoundDrawablesWithIntrinsicBounds(
                        0, 0,
                        if (isConfirmPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
                        0
                    )
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    // --- RegisterView Implementation ---
    override fun togglePasswordVisibility() {
        setupPasswordToggle()
        setupConfirmPasswordToggle()
    }

    override fun showLoading() { progressBar.visibility = ProgressBar.VISIBLE }
    override fun hideLoading() { progressBar.visibility = ProgressBar.GONE }
    override fun showError(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    override fun navigateToMain() { startActivity(Intent(this, MainActivity::class.java)); finish() }
    override fun showEmailVerificationNotice() {
        // Show success message and navigate to the verification watcher screen
        Toast.makeText(this, "Verification email sent. Please check your inbox.", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, VerificationWatcherActivity::class.java))
        finish()
    }

    /**
     * Handles the result returned from the standard Google Sign-In intent.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_UP) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Extract the ID token and delegate to the presenter for Firebase authentication
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken.isNullOrEmpty()) {
                    showError("Google Sign-Up failed: ID Token is null.")
                    return
                }
                presenter.onGoogleSignUpResult(idToken)
            } catch (e: ApiException) {
                // Handle exceptions (e.g., user cancellation, network error)
                showError("Google Sign-Up failed: ${e.statusCode}")
            }
        }
    }

    // Clean up presenter resources (cancel coroutines)
    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }
}

/**
 * View contract for the Register screen (MVP View).
 */
interface RegisterView {
    fun showLoading()
    fun hideLoading()
    fun togglePasswordVisibility()
    fun showError(message: String)
    fun navigateToMain()
    fun showEmailVerificationNotice()
}
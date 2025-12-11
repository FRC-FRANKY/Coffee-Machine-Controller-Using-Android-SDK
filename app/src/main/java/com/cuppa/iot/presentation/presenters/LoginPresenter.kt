package com.cuppa.iot.presentation.presenters

import com.cuppa.iot.domain.usecases.AuthUseCase
import com.cuppa.iot.ui.activities.LoginView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Presenter for the Login Activity/Fragment.
 * Handles user input validation, authentication calls via UseCase, and navigation.
 */
class LoginPresenter(
    private val view: LoginView,
    private val authUseCase: AuthUseCase
) {
    private val job = Job() // Coroutine Job for managing the presenter's async lifecycle
    private val scope = CoroutineScope(job + Dispatchers.Main) // Coroutine scope tied to the job, ensuring all async work is on the Main thread (for UI)

    /**
     * Handles the standard email and password login attempt.
     * Performs input validation before calling the AuthUseCase.
     */
    fun onLoginClicked(email: String, password: String, rememberMe: Boolean) {

        // Input validation for non-empty fields
        if (email.isBlank()) {
            view.showError("Email must not be empty")
            return
        }
        if (password.isBlank()) {
            view.showError("Password must not be empty")
            return
        }

        view.showLoading()
        scope.launch {
            // Call the UseCase which handles Firebase login and rememberMe logic
            val result = try {
                authUseCase.login(email, password, rememberMe)
            } catch (e: Exception) {
                Result.failure<Unit>(e)
            }

            view.hideLoading()
            // Handle success (navigation) or failure (error message)
            if (result.isSuccess) {
                view.navigateToMain()
            } else {
                view.showError(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    /**
     * Handles the result of a successful Google Sign-In, using the received ID token.
     */
    fun onGoogleSignInResult(idToken: String) {
        view.showLoading()
        scope.launch {
            // Call the UseCase to authenticate with Firebase using the Google token
            val result = try {
                authUseCase.googleSignIn(idToken)
            } catch (e: Exception) {
                Result.failure<Unit>(e)
            }
            view.hideLoading()
            // Handle success or failure
            if (result.isSuccess) view.navigateToMain()
            else view.showError(result.exceptionOrNull()?.message ?: "Google Sign-In failed")
        }
    }

    /**
     * Handles a request to send a password reset email.
     * Performs basic email validation.
     */
    fun onResetPasswordRequested(email: String) {
        if (email.isBlank()) {
            view.showError("Email must not be empty")
            return
        }

        view.showLoading()
        scope.launch {
            // Call the UseCase to send the reset email
            val result = try {
                authUseCase.resetPassword(email)
            } catch (e: Exception) {
                Result.failure<Unit>(e)
            }
            view.hideLoading()
            // Handle success (inform user) or failure (show error)
            if (result.isSuccess) {
                view.showMessage("Password reset email sent. Please check your inbox.")
            } else {
                view.showError(result.exceptionOrNull()?.message ?: "Failed to send reset email.")
            }
        }
    }

    /**
     * Cancels the coroutine scope when the View is destroyed.
     */
    fun onDestroy() {
        scope.cancel()
    }
}

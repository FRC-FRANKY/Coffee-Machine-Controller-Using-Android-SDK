package com.cuppa.iot.presentation.presenters

import com.cuppa.iot.domain.usecases.AuthUseCase
import com.cuppa.iot.ui.activities.RegisterView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Presenter for the Register Activity/Fragment.
 * Handles input validation, account creation, Google sign-up, and email verification.
 */
class RegisterPresenter(
    private val view: RegisterView,
    private val authUseCase: AuthUseCase
) {
    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.Main) // Scope for asynchronous tasks

    /**
     * Handles the user clicking the register button.
     * Executes strict validation, checks for Google account linkage, performs registration,
     * and sends a verification email.
     */
    fun onRegisterClicked(username: String, email: String, password: String, confirmPassword: String) {
        when {
            username.length !in 5..14 -> {
                view.showError("Username must be 5–14 characters")
                return
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                view.showError("Invalid email format")
                return
            }
            password.length < 8 -> {
                view.showError("Password must be at least 8 characters")
                return
            }
            !password.matches(Regex(".*[A-Z].*")) -> {
                view.showError("Password must contain an uppercase letter")
                return
            }
            !password.matches(Regex(".*[a-z].*")) -> {
                view.showError("Password must contain a lowercase letter")
                return
            }
            !password.matches(Regex(".*\\d.*")) -> {
                view.showError("Password must contain a number")
                return
            }
            !password.matches(Regex(".*[!@#\$%^&*()].*")) -> {
                view.showError("Password must contain a special character")
                return
            }
            password != confirmPassword -> {
                view.showError("Passwords do not match")
                return
            }
        }

        view.showLoading()
        // 1. Check if the email is already linked to a Google account
        scope.launch {
            val linkedResult = try {
                authUseCase.isEmailLinkedToGoogle(email)
            } catch (e: Exception) {
                Result.failure<Boolean>(e)
            }

            if (linkedResult.isFailure) {
                view.hideLoading()
                view.showError("Failed to validate email: ${linkedResult.exceptionOrNull()?.message}")
                return@launch
            }

            // Prevent registration if email is Google-linked (forces user to use Google sign-in)
            if (linkedResult.getOrDefault(false)) {
                view.hideLoading()
                view.showError("This email is already linked to a Google account. Please sign in with Google.")
                return@launch
            }

            val registerResult = try {
                authUseCase.register(username, email, password)
            } catch (e: Exception) {
                Result.failure<Unit>(e)
            }

            // 2. Attempt standard email/password registration
            if (registerResult.isSuccess) {
                // 3. Registration successful, send verification email
                val verifyResult = try {
                    authUseCase.sendEmailVerification()
                } catch (e: Exception) {
                    Result.failure<Unit>(e)
                }
                view.hideLoading()
                // Show a warning if email sending failed, but proceed with the verification notice
                if (verifyResult.isFailure) {
                    view.showError("Registration succeeded, but sending verification email failed: ${verifyResult.exceptionOrNull()?.message}")
                }
                view.showEmailVerificationNotice() // Notify the user to check their email
            } else {
                // Registration failed (e.g., email already in use, weak password)
                view.hideLoading()
                view.showError(registerResult.exceptionOrNull()?.message ?: "Registration failed.")
            }
        }
    }

    /**
     * Handles the result of a successful Google Sign-Up/Sign-In flow using the ID token.
     */
    fun onGoogleSignUpResult(idToken: String) {
        view.showLoading()
        scope.launch {
            // Call the UseCase to authenticate with Firebase using the Google token
            val result = try {
                authUseCase.googleSignIn(idToken)
            } catch (e: Exception) {
                Result.failure<Unit>(e)
            }
            view.hideLoading()
            // Navigate to main screen on success
            if (result.isSuccess) view.navigateToMain()
            else view.showError(result.exceptionOrNull()?.message ?: "Google Sign-Up failed")
        }
    }

    /**
     * Cancels the coroutine scope when the View is destroyed to prevent memory leaks.
     */
    fun onDestroy() {
        scope.cancel()
    }
}

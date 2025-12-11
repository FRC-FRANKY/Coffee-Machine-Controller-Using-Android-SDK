package com.cuppa.iot.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.cuppa.iot.domain.repositories.AuthRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed implementation of AuthRepository.
 * Handles all user authentication-related operations using Firebase Auth and stores user
 * profile/settings data in Firebase Realtime Database.
 */

class FirebaseAuthDataSource(private val context: Context) : AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance() // Firebase Authentication instance for all auth operations (login, register, link, etc.)
    private val db = FirebaseDatabase.getInstance().reference // Reference to the root of the Firebase Realtime Database
    private val prefs: SharedPreferences =
        context.getSharedPreferences("authPrefs", Context.MODE_PRIVATE) // Shared Preferences to save and retrieve local user credentials (e.g., for auto-login)

    /**
     * Attempts to sign in a user with email and password.
     * Only succeeds if the email is verified.
     */
    override suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            // Signs the user in with Firebase Auth
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null && user.isEmailVerified) {
                Result.success(Unit)
            } else {
                // ensure user isn't kept signed in if not verified
                auth.signOut()
                Result.failure(Exception("Please verify your email before logging in."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a new user with email and password, initializes their profile and settings in
     * the Realtime Database, and sends a verification email.
     */
    override suspend fun register(username: String, email: String, password: String): Result<Unit> {
        return try {
            // Creates the user in Firebase Auth
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                val userRef = db.child("users").child(user.uid) // Reference to the new user's node in the Realtime Database

                // ✅ Generate random 8-digit ID
                val idNumber = (10000000..99999999).random().toString()

                // ✅ Create user profile data
                // Sets the initial profile data (username, email, ID, default settings) in DB
                val userProfile = mapOf(
                    "username" to username,
                    "email" to email,
                    "idNumber" to idNumber,
                    "autoStart" to false,
                    "tempAlert" to false,
                    "brewDefaultTime" to "07:00 AM",
                    "profilePic" to (user.photoUrl?.toString() ?: "")
                )
                userRef.child("profile").setValue(userProfile).await()

                // ✅ Initialize default settings
                // Sets the initial detailed settings in DB
                val settings = mapOf(
                    "brewScheduler" to mapOf("enabled" to false),
                    "temperatureAlert" to mapOf("enabled" to false, "threshold" to 80)
                )
                userRef.child("settings").setValue(settings).await()

                // ✅ Send verification email
                user.sendEmailVerification().await()

                Result.success(Unit)
            } else {
                Result.failure(Exception("User creation failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a verification email to the currently logged-in user.
     */
    override suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user logged in"))
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a password reset email to the given email address.
     */
    override suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs in with Firebase using a Google ID token.
     * Initializes user profile and settings if it's a new user or if the data doesn't exist.
     */
    override suspend fun firebaseAuthWithGoogle(idToken: String): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null) // Creates a Google Auth credential from the ID token
            val authResult = auth.signInWithCredential(credential).await() // Signs in with the credential
            val firebaseUser = auth.currentUser
            val isNew = authResult.additionalUserInfo?.isNewUser == true // Checks if it's a newly created user
            if (firebaseUser != null) {
                val userRef = db.child("users").child(firebaseUser.uid)

                val profileSnapshot = userRef.child("profile").get().await()
                // If new user OR profile data is missing, create default profile
                if (isNew || !profileSnapshot.exists()) {
                    val profile = mapOf(
                        "username" to (firebaseUser.displayName ?: "User"),
                        "email" to (firebaseUser.email ?: ""),
                        "idNumber" to ((10000000..99999999).random().toString()),
                        "autoStart" to false,
                        "tempAlert" to false,
                        "brewDefaultTime" to "07:00 AM",
                        "profilePic" to (firebaseUser.photoUrl?.toString() ?: "")
                    )
                    userRef.child("profile").setValue(profile).await()
                }

                val settingsSnapshot = userRef.child("settings").get().await()
                // If settings data is missing, create default settings
                if (!settingsSnapshot.exists()) {
                    val settings = mapOf(
                        "brewScheduler" to mapOf("enabled" to false),
                        "temperatureAlert" to mapOf("enabled" to false, "threshold" to 80)
                    )
                    userRef.child("settings").setValue(settings).await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves the user's email and password securely to Shared Preferences.
     */
    override suspend fun saveCredentials(email: String, password: String) {
        prefs.edit().putString("email", email).putString("password", password).apply()
    }

    /**
     * Removes the saved user credentials from Shared Preferences.
     */
    override suspend fun clearSavedCredentials() {
        prefs.edit().remove("email").remove("password").apply()
    }

    /**
     * Checks Firebase Auth to see if the given email is already linked to a Google account.
     */
    override suspend fun isEmailLinkedToGoogle(email: String): Result<Boolean> {
        return try {
            val signInMethods = auth.fetchSignInMethodsForEmail(email).await() // Fetches all sign-in methods for the email
            val isLinked = signInMethods.signInMethods?.contains(GoogleAuthProvider.PROVIDER_ID) ?: false // Checks if Google provider is among the linked methods
            Result.success(isLinked)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Links a Google account (via ID token) to the currently logged-in user.
     */
    override suspend fun linkGoogleToEmailAccount(idToken: String): Result<Unit> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null) // Creates Google Auth credential
            val user = auth.currentUser ?: return Result.failure(Exception("No user logged in")) // Links the credential to the current user
            user.linkWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Links an Email/Password account to the currently logged-in user.
     */
    override suspend fun linkEmailToGoogleAccount(email: String, password: String): Result<Unit> {
        return try {
            val credential = EmailAuthProvider.getCredential(email, password) // Creates Email/Password Auth credential
            val user = auth.currentUser ?: return Result.failure(Exception("No user logged in"))
            // Links the credential to the current user
            user.linkWithCredential(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.cuppa.iot.domain.models

/**
 * Domain model for User profile data.
 * Represents the business logic layer for user information.
 */
data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val idNumber: String = "",
    val autoStart: Boolean = false,
    val tempAlert: Boolean = false,
    val brewDefaultTime: String = "7:00 AM",
    val profilePic: String = ""
)

/**
 * Domain model for Brew status.
 * Represents the business logic layer for brewing operations.
 */
data class BrewStatus(
    val status: String, // "brewing", "done", "cold", "idle"
    val temperature: Double?,
    val isBrewing: Boolean
)

/**
 * Domain model for Brew command.
 * Represents the business logic layer for brew operations.
 */
data class BrewCommand(
    val shouldStart: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Domain model for Notification .
 * Represents the business logic layer for notification.
 */
data class Notification(
    val id: String = "", // Add a default ID for the database key
    val title: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val category: String = "", // "Today", "This Week"
    val isRead: Boolean = false // Optional: For visual marking, though removal is better for simple "read"
)

/**
 * Data class representing a user-defined auto-brew schedule.
 * Matches the validation rules in cuppaRule.txt.
 */
data class Schedule(
    val id: String? = null, // Firebase generated unique key ($scheduleId)
    val time: String = "00:00", // e.g., "07:00" (24-hour format)
    val repeat: String = "Daily", // e.g., "Mon, Tue, Wed" or "Daily"
    val enabled: Boolean = true // Toggle state to activate/deactivate the schedule
)
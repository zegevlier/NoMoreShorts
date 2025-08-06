package me.zegs.nomoreshorts.utils

import java.util.regex.Pattern

/**
 * Utility class for input validation with user-friendly error messages
 */
object ValidationUtils {

    // Validation constants
    const val MIN_CHANNEL_NAME_LENGTH = 3
    const val MAX_CHANNEL_NAME_LENGTH = 30
    const val MAX_SWIPE_LIMIT = 10000
    const val MIN_SWIPE_LIMIT = 0
    const val MAX_TIME_LIMIT = 1440 // 24 hours in minutes
    const val MIN_TIME_LIMIT = 1
    const val MAX_RESET_PERIOD = 10080 // 1 week in minutes
    const val MIN_RESET_PERIOD = 1

    /**
     * Represents a validation result with success status and error message
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String = "",
        val warningMessage: String = ""
    ) {
        companion object {
            fun success(warningMessage: String = "") = ValidationResult(true, "", warningMessage)
            fun error(errorMessage: String) = ValidationResult(false, errorMessage)
        }
    }

    /**
     * Validates YouTube channel handles with comprehensive checks
     */
    fun validateChannelName(channelName: String, existingChannels: List<String> = emptyList()): ValidationResult {
        val trimmedName = channelName.trim()

        return when {
            trimmedName.isEmpty() ->
                ValidationResult.error("Channel handle cannot be empty")

            existingChannels.contains(trimmedName) ->
                ValidationResult.error("This channel is already in your list")

            // https://support.google.com/youtube/answer/11585688
            // Is between 3-30 characters
            trimmedName.length < MIN_CHANNEL_NAME_LENGTH ->
                ValidationResult.error("Channel handle must be at least $MIN_CHANNEL_NAME_LENGTH characters")

            trimmedName.length > MAX_CHANNEL_NAME_LENGTH ->
                ValidationResult.error("Channel handle must be less than $MAX_CHANNEL_NAME_LENGTH characters")

            // We are not going to validate the entire list of supported alphabets, but we will check for some common invalid characters
            // We also have to allow e.g. arabic, cyrillic, etc. characters as they are valid in YouTube handles
            // Thus, we rely on checking for any known-invalid characters.
            // Invalid characters include: !@#$%^&*()+=[]{}|;:'",<>?/\`~ and whitespace
            Pattern.matches("[!@#$%^&*()+=\\[\\]{}|;:'\",<>?\\\\`~\\s]", trimmedName) ->
                ValidationResult.error("Channel handle cannot contain special characters or whitespace")


            // Separators - underscores (_), hyphens (-), periods (.), and Latin middle dots (·) - are not allowed at the beginning or end of a handle
            trimmedName.startsWith("_") || trimmedName.startsWith("-") || trimmedName.startsWith(".") || trimmedName.startsWith("·") ||
                    trimmedName.endsWith("_") || trimmedName.endsWith("-") || trimmedName.endsWith(".") || trimmedName.endsWith("·") ->
                ValidationResult.error("Channel handle cannot start or end with separators (_ - . ·)")

            else -> {
                ValidationResult.success()
            }
        }
    }

    /**
     * Validates swipe limit with range checking
     */
    fun validateSwipeLimit(value: String): ValidationResult {
        if (value.isBlank()) {
            return ValidationResult.error("Swipe limit cannot be empty")
        }

        val count = value.toIntOrNull()
        return when {
            count == null -> ValidationResult.error("Please enter a valid number")
            count < MIN_SWIPE_LIMIT -> ValidationResult.error("Swipe limit cannot be negative")
            count > MAX_SWIPE_LIMIT -> ValidationResult.error("Swipe limit cannot exceed $MAX_SWIPE_LIMIT")
            else -> ValidationResult.success()
        }
    }

    /**
     * Validates time limit with range checking
     */
    fun validateTimeLimit(value: String): ValidationResult {
        if (value.isBlank()) {
            return ValidationResult.error("Time limit cannot be empty")
        }

        val minutes = value.toIntOrNull()
        return when {
            minutes == null -> ValidationResult.error("Please enter a valid number")
            minutes < MIN_TIME_LIMIT -> ValidationResult.error("Time limit must be at least $MIN_TIME_LIMIT minute")
            minutes > MAX_TIME_LIMIT -> ValidationResult.error("Time limit cannot exceed $MAX_TIME_LIMIT minutes (24 hours)")
            else -> ValidationResult.success()
        }
    }

    /**
     * Validates reset period with range checking
     */
    fun validateResetPeriod(value: String): ValidationResult {
        if (value.isBlank()) {
            return ValidationResult.error("Reset period cannot be empty")
        }

        val minutes = value.toIntOrNull()
        return when {
            minutes == null -> ValidationResult.error("Please enter a valid number")
            minutes < MIN_RESET_PERIOD -> ValidationResult.error("Reset period must be at least $MIN_RESET_PERIOD minute")
            minutes > MAX_RESET_PERIOD -> ValidationResult.error("Reset period cannot exceed $MAX_RESET_PERIOD minutes (1 week)")
            else -> ValidationResult.success()
        }
    }

    /**
     * Sanitizes user input by removing potential harmful characters
     */
    fun sanitizeInput(input: String): String {
        return input.trim()
            .replace(Regex("[<>\"'&]"), "") // Remove potentially harmful characters
            .take(1000) // Limit length to prevent memory issues
    }
}

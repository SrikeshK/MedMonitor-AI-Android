package com.medmonitor.util

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log

object SoundUtil {
    private const val TAG = "SOUND_DEBUG"

    /**
     * Safely retrieves a sound URI from the raw resources or falls back to the system default.
     * Maps user-friendly names to actual resource file names.
     */
    fun getSoundUri(context: Context, soundName: String?): Uri {
        Log.d(TAG, "Requested sound: $soundName")

        val resourceName = when (soundName) {
            "Alarm", "alarm_sound" -> "alarm_sound"
            "Notification Tone 1", "tone1" -> "tone1"
            "Notification Tone 2", "tone2" -> "tone2"
            "Soft Bell", "soft_bell" -> "soft_bell"
            else -> null
        }

        if (resourceName == null) {
            Log.d(TAG, "No specific sound requested or 'Default' selected. Using system default.")
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        // The files in res/raw currently have .mp3.mp3 extensions (e.g., tone1.mp3.mp3)
        // Android resource names for these would be "tone1_mp3" if dots are replaced by underscores.
        // However, we will try the requested name first, and then fallback.
        
        var resId = context.resources.getIdentifier(
            resourceName,
            "raw",
            context.packageName
        )

        // Special handling for the double extension seen in this project (.mp3.mp3)
        if (resId == 0) {
            val fallbackResourceName = "${resourceName}_mp3"
            resId = context.resources.getIdentifier(
                fallbackResourceName,
                "raw",
                context.packageName
            )
        }

        Log.d(TAG, "Resolved resId: $resId")

        return if (resId != 0) {
            Uri.parse("android.resource://${context.packageName}/$resId")
        } else {
            Log.e(TAG, "Sound file NOT FOUND ($resourceName), using default")
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    /**
     * Test function to verify all sounds
     */
    fun verifyAllSounds(context: Context) {
        val testSounds = listOf("alarm_sound", "tone1", "tone2", "soft_bell", "invalid_sound")
        testSounds.forEach { 
            getSoundUri(context, it)
        }
    }
}

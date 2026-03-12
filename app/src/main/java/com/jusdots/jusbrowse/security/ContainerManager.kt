package com.jusdots.jusbrowse.security

import android.content.Context
import android.annotation.SuppressLint

object ContainerManager {
    val AVAILABLE_CONTAINERS = listOf("default", "work", "personal", "banking", "sandbox")

    /**
     * Set the profile for a GeckoSession if supported.
     * Profile isolation via contextId ensures separate cookies, cache, and storage.
     */
    fun applyContainer(session: org.mozilla.geckoview.GeckoSession, containerId: String?) {
        // In GeckoView, container isolation is handled during session creation via contextId.
        // This method remains as a shell if needed for dynamic changes.
    }

    /**
     * Get display name for a container
     */
    fun getContainerName(containerId: String): String {
        return when (containerId) {
            "work" -> "Work"
            "personal" -> "Personal"
            "banking" -> "Banking"
            "sandbox" -> "Sandbox"
            else -> "Default"
        }
    }
}


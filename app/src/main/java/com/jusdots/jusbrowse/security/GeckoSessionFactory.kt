package com.jusdots.jusbrowse.security

import com.jusdots.jusbrowse.BrowserApplication
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Factory for creating and initializing GeckoSessions.
 */
object GeckoSessionFactory {

    fun createSession(
        isPrivate: Boolean = false,
        containerId: String? = null
    ): GeckoSession {
        val settingsBuilder = GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .useTrackingProtection(true)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .suspendMediaWhenInactive(true)
            .allowJavascript(true)
            .userAgentOverride("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:148.0) Gecko/20100101 Firefox/148.0")



        // Container isolation via contextual identity
        if (containerId != null) {
            settingsBuilder.contextId(containerId)
        }

        val session = org.mozilla.geckoview.GeckoSession(settingsBuilder.build())
        
        // Setup Native Feature Protection (EasyPrivacy equivalent)
        session.settings.useTrackingProtection = true
        // Note: Strict Native blocking
        val runtimeSettings = BrowserApplication.runtime?.settings
        
        // Open the session with the global runtime
        val runtime = BrowserApplication.runtime 
            ?: throw IllegalStateException("GeckoRuntime not initialized")
        
        session.open(runtime)
        return session
    }
}

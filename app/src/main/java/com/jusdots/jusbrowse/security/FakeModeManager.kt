package com.jusdots.jusbrowse.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.webkit.JavascriptInterface
import java.util.Properties

/**
 * Manages Fake Mode state and current persona.
 * Enforces strict isolation: switching personas wipes storage.
 */
object FakeModeManager {

    private const val PREF_NAME = "fake_mode_prefs"
    private const val KEY_PERSONA_ID = "active_persona_id"

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _currentPersona = MutableStateFlow<FakePersona?>(null)
    val currentPersona: StateFlow<FakePersona?> = _currentPersona.asStateFlow()

    private val secureRandom = java.security.SecureRandom()

    private var sessionBatteryLevel: Double = 0.85
    private var sessionBatteryCharging: Boolean = true
    private var sessionIsFlagship: Boolean = true
    private var networkTimezone: String? = null
    private var sessionStartTime: Long = System.currentTimeMillis()
    var sessionSeed: Long = System.currentTimeMillis()
        private set
    var sessionLocalIp: String = "192.168.1.1"
        private set

    // Per-session randomized bridge names to prevent browser fingerprinting
    var bridgeNameSurgical: String = generateBridgeName()
        private set
    var bridgeNamePrivacy: String = generateBridgeName()
        private set

    var isLanguageNormalizationEnabled: Boolean = true

    private fun generateBridgeName(): String {
        val chars = ('a'..'z') + ('A'..'Z')
        return (1..12).map { chars[secureRandom.nextInt(chars.size)] }.joinToString("")
    }

    /**
     * Calculate battery level with linear drift (0.5% per 10 minutes)
     */
    fun getDriftedBatteryLevel(): Double {
        val elapsedMinutes = (System.currentTimeMillis() - sessionStartTime) / 60000.0
        val drift = (elapsedMinutes / 10.0) * 0.005
        return (sessionBatteryLevel - drift).coerceAtLeast(0.01)
    }

    /**
     * Randomize session-specific values (Battery, Performance Tier)
     * Called when fake mode is enabled or initialized.
     */
    private fun randomizeSessionState() {
        // Battery between 20% and 98%
        sessionBatteryLevel = 0.20 + (secureRandom.nextDouble() * 0.78)
        // Charging 50/50
        sessionBatteryCharging = secureRandom.nextBoolean()
        // Toggle Flagship vs Budget for this session
        sessionIsFlagship = secureRandom.nextBoolean()
        networkTimezone = null // Reset for new session
        sessionStartTime = System.currentTimeMillis()
        sessionSeed = secureRandom.nextLong()
        sessionLocalIp = NetworkUtils.getWeightedRandomLocalIp()
        // Rotate bridge names per session
        bridgeNameSurgical = generateBridgeName()
        bridgeNamePrivacy = generateBridgeName()
    }

    /**
     * Trigger an async fetch of the current network timezone.
     */
    fun syncTimezoneWithNetwork(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            networkTimezone = NetworkUtils.fetchCurrentTimezone()
        }
    }

    /**
     * Initialize logic (load saved persona)
     */
    fun init(context: Context) {
        val savedId = getSavedPersonaId(context)
        
        if (savedId != null) {
            val savedPersona = PersonaRepository.getPersonaById(savedId)
            if (savedPersona != null) {
                randomizeSessionState() 
                // Load the persona from the same group but matching current session's tier
                val persona = PersonaRepository.getPersonaInGroup(savedPersona.groupId, sessionIsFlagship)
                _currentPersona.value = persona
                _isEnabled.value = true
            }
        }
    }

    /**
     * Helper to get saved ID synchronously for Application.onCreate
     */
    fun getSavedPersonaId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PERSONA_ID, null)
    }

    /**
     * Enable Fake Mode with specific persona.
     * RESTARTS APP to apply new Data Directory Suffix.
     */
    fun enableFakeMode(context: Context, persona: FakePersona) {
        if (_currentPersona.value?.id != persona.id) {
            randomizeSessionState() 
            // Save the specific persona selected (it will be used as a group anchor on next init)
            saveState(context, persona.id)
            // Restart to apply namespace
            restartApp(context)
        }
    }

    /**
     * Disable Fake Mode and clear persona.
     * RESTARTS APP to return to default storage.
     */
    fun disableFakeMode(context: Context) {
        if (_isEnabled.value) {
            saveState(context, null)
            restartApp(context)
        }
    }
    
    /**
     * Trigger a hard app restart to ensure new WebView process binding
     */
    private fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = android.content.Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    private fun saveState(context: Context, personaId: String?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PERSONA_ID, personaId).commit()
    }

    /**
     * WIPES EVERYTHING: Cookies, LocalStorage, Cache, Web Databases.
     * Essential for persona isolation.
     */
    private fun clearWebViewData(context: Context) {
        // GeckoView handles storage via separate runtime/context contexts.
        // Legacy WebView cleanup removed.
    }

    /**
     * Get User-Agent for current state
     */
    fun getUserAgent(): String? {
        return if (_isEnabled.value) {
            _currentPersona.value?.userAgent
        } else null
    }
    
    /**
     * Get headers to inject for interceptors
     */
    fun getHeaders(): Map<String, String> {
        return _currentPersona.value?.headers ?: emptyMap()
    }


    /**
     * Bridge for receiving telemetry from WebView
     */
    class PrivacyBridge {
        @JavascriptInterface
        fun reportSuspicion(points: Int, reason: String) {
            SuspicionScorer.reportSuspiciousActivity(points)
        }
    }
}

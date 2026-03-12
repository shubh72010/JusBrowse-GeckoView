package com.jusdots.jusbrowse

import android.app.Application
import androidx.room.Room
import com.jusdots.jusbrowse.data.database.BrowserDatabase
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.jusdots.jusbrowse.data.repository.PreferencesRepository
import org.json.JSONObject
import kotlinx.coroutines.*
import org.mozilla.geckoview.WebExtension

class BrowserApplication : Application() {
    
    companion object {
        @Volatile
        private var instance: BrowserApplication? = null

        @Volatile
        var runtime: GeckoRuntime? = null
            private set
        
        fun getInstance(): BrowserApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
        
        val database: BrowserDatabase by lazy {
            val app = getInstance()
            val context = app.applicationContext
            val personaId = com.jusdots.jusbrowse.security.FakeModeManager.getSavedPersonaId(context)
            val dbName = if (personaId != null) "browser_database_$personaId" else "browser_database"
            
            Room.databaseBuilder(
                context,
                BrowserDatabase::class.java,
                dbName
            ).fallbackToDestructiveMigration().build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Only initialize GeckoRuntime and WebExtensions in the main process to prevent ANRs in child processes
        val currentProcessName = if (android.os.Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.find { it.pid == android.os.Process.myPid() }?.processName ?: ""
        }
        
        if (currentProcessName == packageName) {
            
            val deviceMode = com.jusdots.jusbrowse.security.MemorySurgeon.getDeviceMode(this)
            
            val prefs = com.jusdots.jusbrowse.data.repository.PreferencesRepository(this)
            val isFollianActive = kotlinx.coroutines.runBlocking { prefs.follianMode.first() }
            
            // Base privacy settings
            val baseArgs = mutableListOf(
                "--pref", "toolkit.telemetry.enabled=false",
                "--pref", "datareporting.policy.dataSubmissionEnabled=false",
                "--pref", "intl.accept_languages=en-US, en",
                "--pref", "privacy.trackingprotection.enabled=true",
                "--pref", "privacy.trackingprotection.pbmode.enabled=true",
                "--pref", "browser.safebrowsing.malware.enabled=false",
                "--pref", "browser.safebrowsing.phishing.enabled=false",
                "--pref", "dom.security.https_only_mode=true",
                "--pref", "network.prefetch-next=false",
                "--pref", "network.dns.disablePrefetch=true",
                "--pref", "network.trr.mode=3",
                "--pref", "network.trr.uri=https://cloudflare-dns.com/dns-query",
                "--pref", "privacy.use_utc_timezone=true",
                "--pref", "device.sensors.enabled=false",
                "--pref", "dom.enable_performance=false",
                "--pref", "dom.w3c_pointer_events.enabled=false",
                "--pref", "webgl.override-unmasked-vendor=Google Inc. (Intel)",
                "--pref", "webgl.override-unmasked-renderer=ANGLE (Intel, Intel(R) UHD Graphics 630 (0x00003E92) Direct3D11 vs_5_0 ps_5_0, D3D11)"

            )



            if (isFollianActive) {
                // Follian Protocol: Maximum Native Stealth (Tor "Safest" Level)
                baseArgs.addAll(listOf(
                    "--pref", "privacy.resistFingerprinting=true",
                    "--pref", "privacy.resistFingerprinting.letterboxing=true",
                    "--pref", "javascript.enabled=false",
                    "--pref", "webgl.disabled=true",
                    "--pref", "media.peerconnection.enabled=false",
                    "--pref", "dom.webaudio.enabled=false",
                    "--pref", "dom.gamepad.enabled=false"
                ))
            } else {
                // Standard/Persona Engine settings (JusFake active)
                baseArgs.addAll(listOf(
                    "--pref", "privacy.resistFingerprinting=false",
                    "--pref", "privacy.resistFingerprinting.letterboxing=false"
                ))
            }


            if (deviceMode == com.jusdots.jusbrowse.security.MemorySurgeon.DeviceMode.LOW_SPEC) {
                // Strict memory caches for Low Spec
                baseArgs.addAll(listOf(
                    "--pref", "browser.cache.memory.capacity=10240", // Limit RAM cache to 10MB
                    "--pref", "browser.sessionhistory.max_entries=10", // Keep less history in memory
                    "--pref", "javascript.options.mem.max=100", // Soft cap JS heap
                    "--pref", "image.mem.max_decoded_image_kb=20480" // 20MB decoded image cap
                ))
            } else {
                // Standard/Aggressive caching for High Spec
                baseArgs.addAll(listOf(
                    "--pref", "browser.cache.memory.capacity=51200", // 50MB RAM cache
                    "--pref", "browser.sessionhistory.max_entries=50",
                    "--pref", "network.http.max-connections=100" // More concurrent net conns
                ))
            }

            // Initialize GeckoRuntime with strict privacy settings
            val settings = GeckoRuntimeSettings.Builder()
                .aboutConfigEnabled(true)
                .arguments(baseArgs.toTypedArray())
                .build()

            settings.setBaselineFingerprintingProtection(true)
            settings.setBaselineFingerprintingProtectionOverrides("+JSDateTimeUTC,+CanvasRandomization")




            runtime = GeckoRuntime.create(this, settings)

            // Register privacy WebExtension
            setupWebExtensions(runtime!!)
        }
    }

    private fun setupWebExtensions(runtime: org.mozilla.geckoview.GeckoRuntime) {
        val extensionId = "jusbrowse-privacy@jusdots.com"
        val extensionPath = "resource://android/assets/extensions/jusbrowse-privacy/"
        
        runtime.webExtensionController.ensureBuiltIn(extensionPath, extensionId)
            .accept({ extension ->
                Log.d("BrowserApplication", "WebExtension registered: ${extension?.id}")
                // Register message delegate for this extension
                extension?.setMessageDelegate(com.jusdots.jusbrowse.security.BrowserMessageDelegate(this@BrowserApplication), "jusbrowse")
            }, { throwable ->
                Log.e("BrowserApplication", "Failed to register WebExtension", throwable)
            })
    }
}

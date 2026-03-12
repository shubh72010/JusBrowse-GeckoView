package com.jusdots.jusbrowse.security

import android.webkit.WebView

/**
 * Follian Mode - Hard JavaScript Kill
 * 
 * Disables ALL JavaScript execution at the WebView level.
 * This WILL break most modern websites. That's the point.
 * 
 * Designed for:
 * - Maximum privacy (no tracking scripts)
 * - Reading-focused content consumption
 * - Hostile network environments
 */
object FollianBlocker {
    
    /**
     * Apply Follian Mode to a WebView.
     * This completely disables JavaScript execution.
     */
    fun applyToWebView(webView: WebView) {
        webView.settings.apply {
            // Core JS kill
            javaScriptEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            
            // Block related technologies
            // Note: These are already mostly blocked when JS is off,
            // but we're explicit about intent
            allowFileAccess = false
            allowContentAccess = false
        }
    }
}


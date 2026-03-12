"use strict";

// ═══════════════════════════════════════════════════
// AGGRESSIVE AD & TRACKER SHIELD
// ═══════════════════════════════════════════════════

// High-priority hardcoded domains for zero-latency blocking
// These are matched as suffixes (e.g., "doubleclick.net" catches "*.doubleclick.net")
const HARDCODED_BLOCKED = new Set([
    // Google / DoubleClick / Analytics
    "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagmanager.com", "tagmanager.google.com", "google-analytics.com", "googleanalytics.com", "adservice.google.com", "tpc.googlesyndication.com", "pagead2.googlesyndication.com", "imasdk.googleapis.com", "youtubei.googleapis.com", "app-measurement.com", "dai.google.com",
    // Amazon / AWS S3 Tracker Buckets
    "amazon-adsystem.com", "aax.amazon-adsystem.com", "aan.amazon.com", "device-metrics-us.amazon.com", "device-metrics-us-2.amazon.com", "mads-eu.amazon.com", "advertising-api-eu.amazon.com", "adtago.s3.amazonaws.com", "analyticsengine.s3.amazonaws.com", "advice-ads.s3.amazonaws.com", "affiliationjs.s3.amazonaws.com",
    // Facebook / Instagram
    "facebook.net", "facebook.com", "instagram.com", "connect.facebook.net", "graph.facebook.com", "tr.facebook.com", "graph.instagram.com", "i.instagram.com",
    // Microsoft / Bing
    "bingads.microsoft.com", "ads.microsoft.com", "adnxs.com", "bat.bing.com", "clarity.ms", "scorecardresearch.com",
    // Yahoo / Oath
    "yahooinc.com", "ads.yahoo.com", "gemini.yahoo.com", "adtech.yahooinc.com",
    // Taboola / Outbrain
    "taboola.com", "outbrain.com", "trc.taboola.com", "cdn.taboola.com", "widgets.outbrain.com", "log.outbrain.com", "odb.outbrain.com",
    // Criteo
    "criteo.com", "criteo.net", "bidder.criteo.com", "static.criteo.net",
    // Yandex
    "yandex.ru", "metrika.yandex.ru", "mc.yandex.ru", "adfox.yandex.ru", "appmetrica.yandex.ru",
    // OEM Clusters (Xiaomi, Apple, Huawei, Samsung, Oppo, Realme, LG)
    "miui.com", "xiaomi.com", "mistat.xiaomi.com", "hicloud.com", "apple.com", "metrics.icloud.com", "metrics.mzstatic.com", "iadsdk.apple.com", "api-adservices.apple.com", "books-analytics-events.apple.com", "weather-analytics-events.apple.com", "notes-analytics-events.apple.com", "xp.apple.com", "samsungads.com", "smetrics.samsung.com", "oppomobile.com", "realme.com", "realmemobile.com", "lgsmartad.com", "lgappstv.com", "lge.com", "roku.com", "vizio.com", "huawei.com", "ads.huawei.com",
    // Social / Tracking Failures (Reddit, Pinterest, TikTok)
    "rereddit.com", "reddit.com", "widgets.pinterest.com", "ads-dev.pinterest.com", "pinterest.com", "byteoversea.com", "tiktok.com", "appspot.com", "sc-analytics.appspot.com", "quora.com", "vk.com", "snapchat.com",
    // Cryptominers & Malvertising
    "mineralt.io", "crypto-loot.org", "popcash.net", "onclickads.net", "greatis.com", "onclickads.net", "propellerclick.com", "popads.net",
    // Consent / Affiliate / A/B / Video
    "cookiebot.com", "cookielaw.org", "trustarc.com", "privacy-center.org", "privacy-mgmt.com", "usercentrics.eu", "impact.com", "partnerstack.com", "refersion.com", "skimresources.com", "viglink.com", "optimizely.com", "dynamicyield.com", "jwpsrv.com", "jwpcdn.com", "fwmrm.net", "connatix.com", "innovid.com", "tremorhub.com", "intercom.io", "driftt.com", "bnc.lt", "appsflyer.com", "adjust.com", "kochava.com", "control.kochava.com"
]);

const BLOCKED_DOMAINS = new Set(HARDCODED_BLOCKED);
const BLOCKED_PATHS = [];
let adBlockState = true;
let isBlocklistLoaded = false;

// Initialize the blocklist dynamically
async function loadBlocklist() {
    try {
        const response = await fetch(browser.runtime.getURL("adblock_list.txt"));
        const text = await response.text();
        const lines = text.split('\n');
        let count = 0;
        for (let line of lines) {
            line = line.trim();
            if (!line || line.startsWith('!') || line.startsWith('#')) continue;
            
            const cleanLine = line.split('$')[0].toLowerCase();
            if (cleanLine.startsWith('||') && cleanLine.endsWith('^')) {
                BLOCKED_DOMAINS.add(cleanLine.slice(2, -1));
                count++;
            } else if (cleanLine.startsWith('/')) {
                if (cleanLine.length > 3) {
                    BLOCKED_PATHS.push(cleanLine);
                    count++;
                }
            } else if (cleanLine.includes('.')) {
                BLOCKED_DOMAINS.add(cleanLine);
                count++;
            }
        }
        isBlocklistLoaded = true;
        console.log(`Privacy Engine: ${count} rules loaded. Base protection set is ready.`);
    } catch (e) {
        console.error("Critical: Failed to load adblock_list.txt", e);
    }
}

loadBlocklist();

// ═══ REQUEST BLOCKING ═══
browser.webRequest.onBeforeRequest.addListener(
    (details) => {
        // ALWAYS check blocking logic if adBlockState is on (default true)
        if (adBlockState === false) return {};

        try {
            // Don't block the app's own internal resources
            if (details.url.startsWith("resource:") || details.url.startsWith("chrome:") || details.url.startsWith("moz-extension:")) {
                return {};
            }

            const url = new URL(details.url);
            const host = url.hostname.toLowerCase();
            const path = url.pathname.toLowerCase();

            // Check domain blocklist or path rules
            if (isDomainBlocked(host) || isPathBlocked(path)) {
                // Report via native port to reach Kotlin
                if (appPort) {
                    try {
                        appPort.postMessage({
                            type: "report_blocked_tracker",
                            domain: host,
                            url: details.documentUrl || details.originUrl || details.url,
                            tabId: details.tabId
                        });
                    } catch (err) { }
                }
                
                // standard blocking (cancel: true is more effective than data: redirects for most tests)
                return { cancel: true };
            }
        } catch (e) { }
        return {};
    },
    { urls: ["<all_urls>"] },
    ["blocking"]
);

// ═══ HEADER STRIPPING (Privacy Hardening) ═══
const STRIP_HEADERS = new Set([
    "x-requested-with", "x-client-data", "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform", "sec-ch-ua-full-version-list"
]);

browser.webRequest.onBeforeSendHeaders.addListener(
    (details) => {
        const headers = details.requestHeaders.filter(h => !STRIP_HEADERS.has(h.name.toLowerCase())).map(header => {
            if (header.name.toLowerCase() === 'accept-language') {
                return { name: 'Accept-Language', value: 'en-US,en;q=0.9' };
            }
            return header;
        });
        return { requestHeaders: headers };
    },
    { urls: ["<all_urls>"] },
    ["blocking", "requestHeaders"]
);

// ═══ NATIVE SYNC ═══
let appPort = null;
function connectToNative() {
    try {
        appPort = browser.runtime.connectNative("jusbrowse");
        appPort.onMessage.addListener((message) => {
            if (message.type === "set_adblock") {
                adBlockState = message.enabled;
                browser.storage.local.set({ adBlockEnabled: adBlockState });
            } else if (message.type === "extract_media") {
                handleMediaExtraction();
            }
        });
        appPort.onDisconnect.addListener(() => {
            appPort = null;
            setTimeout(connectToNative, 2000);
        });
    } catch (e) {
        setTimeout(connectToNative, 2000);
    }
}
connectToNative();

// ═══ HELPERS ═══
function isDomainBlocked(host) {
    if (!host) return false;
    
    // Check full domain first
    if (BLOCKED_DOMAINS.has(host)) return true;
    
    // Suffix matching (catch subdomains)
    const parts = host.split(".");
    if (parts.length < 2) return false;
    
    // Try progressively shorter suffixes (e.g. a.b.c.com -> b.c.com -> c.com)
    for (let i = 1; i < parts.length - 1; i++) {
        const domain = parts.slice(i).join(".");
        if (BLOCKED_DOMAINS.has(domain)) return true;
    }
    return false;
}

function isPathBlocked(path) {
    if (!path) return false;
    for (const p of BLOCKED_PATHS) {
        if (path.includes(p)) return true;
    }
    return false;
}

async function handleMediaExtraction() {
    try {
        const tabs = await browser.tabs.query({ active: true, windowId: browser.windows.WINDOW_ID_CURRENT });
        if (!tabs || tabs.length === 0) return;
        const response = await browser.tabs.sendMessage(tabs[0].id, { type: "extractMedia" });
        if (response && appPort) {
            appPort.postMessage({ type: "media_extracted", media: response });
        }
    } catch (e) { }
}

// Ensure toggle persists
browser.storage.local.get("adBlockEnabled").then(res => {
    if (res.adBlockEnabled !== undefined) adBlockState = res.adBlockEnabled;
});

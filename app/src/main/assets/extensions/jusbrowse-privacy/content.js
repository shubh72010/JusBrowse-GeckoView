"use strict";

// ═══════════════════════════════════════════════════════
// JusBrowse Content Script — document_start
// Level 5: Native UA/Touch/TZ/Audio | JS: Screen/WebGL/Canvas
// ═══════════════════════════════════════════════════════

(function () {
    const pw = window.wrappedJSObject;
    if (pw.__jusBrowseProtected) return;
    pw.__jusBrowseProtected = true;

    // ── Session seed (crypto quality, stable per page load) ──
    const SESSION_SEED = (function () {
        const buf = new Uint32Array(2);
        crypto.getRandomValues(buf);
        return buf[0] ^ (buf[1] << 16);
    })();

    function seededRandom(index) {
        let x = (SESSION_SEED ^ (index * 1664525 + 1013904223)) >>> 0;
        x = (Math.imul(x ^ (x >>> 16), 0x45d9f3b)) >>> 0;
        x = (Math.imul(x ^ (x >>> 16), 0x45d9f3b)) >>> 0;
        return (x ^ (x >>> 16)) >>> 0;
    }
    function noise(i, mag) { return ((seededRandom(i) & 0xFF) / 255 - 0.5) * 2 * mag; }
    function clamp(v) { return Math.max(0, Math.min(255, Math.round(v))); }

    // ── Helper: define spoofed getter on page-realm object ──
    function defineGetter(obj, prop, value) {
        try {
            Object.defineProperty(obj, prop, {
                get: exportFunction(function () { return value; }, window),
                configurable: true,
                enumerable: true
            });
        } catch (e) { }
    }

    // ════════════════════════════════════════════════
    // 1. SCREEN SIZE — JS (FPP not reliable for screen)
    // ════════════════════════════════════════════════
    try {
        defineGetter(pw.screen, 'width', 1920);
        defineGetter(pw.screen, 'height', 1080);
        defineGetter(pw.screen, 'colorDepth', 24);
        defineGetter(pw.screen, 'pixelDepth', 24);
        defineGetter(pw.screen, 'availWidth', 1920);
        defineGetter(pw.screen, 'availHeight', 1040);
        defineGetter(pw, 'innerWidth', 1920);
        defineGetter(pw, 'innerHeight', 1080);
        defineGetter(pw, 'outerWidth', 1920);
        defineGetter(pw, 'outerHeight', 1080);
        defineGetter(pw, 'devicePixelRatio', 1);
    } catch (e) { }

    // ════════════════════════════════════════════════
    // 2. UA — native: GeckoSessionSettings.userAgentOverride() — Firefox 148
    // ════════════════════════════════════════════════

    // ════════════════════════════════════════════════
    // 3. PLATFORM
    // ════════════════════════════════════════════════
    try {
        defineGetter(pw.navigator, 'platform', 'Win32');
        defineGetter(pw.navigator, 'vendor', '');
    } catch (e) { }

    // ════════════════════════════════════════════════
    // 4. LANGUAGE
    // ════════════════════════════════════════════════
    try {
        defineGetter(pw.navigator, 'language', 'en-US');
        Object.defineProperty(pw.navigator, 'languages', {
            get: exportFunction(function () {
                return cloneInto(['en-US', 'en'], window);
            }, window),
            configurable: true
        });
    } catch (e) { }

    // ════════════════════════════════════════════════
    // 5. TIMEZONE — native: +JSDateTimeUTC FPP target
    // ════════════════════════════════════════════════

    // ════════════════════════════════════════════════
    // 6. TOUCH — native Android (5pts) — blends with mobile pool
    // maxTouchPoints NOT spoofed — 0+TouchEvent=true was unique hybrid
    // ════════════════════════════════════════════════

    // ════════════════════════════════════════════════
    // 7. HARDWARE
    // ════════════════════════════════════════════════
    try {
        defineGetter(pw.navigator, 'hardwareConcurrency', 4);
        defineGetter(pw.navigator, 'deviceMemory', 8);
    } catch (e) { }

    // ════════════════════════════════════════════════
    // 8. WEBGL — readPixels noise + getParameter hook
    // ════════════════════════════════════════════════
    try {
        function patchWebGL(CtxName) {
            if (!pw[CtxName]) return;
            const proto = pw[CtxName].prototype;

            const origGetParam = proto.getParameter;
            proto.getParameter = exportFunction(function (param) {
                if (param === 0x9245) return 'Google Inc. (Intel)';
                if (param === 0x9246) return 'ANGLE (Intel, Intel(R) UHD Graphics 630 (0x00003E92) Direct3D11 vs_5_0 ps_5_0, D3D11)';
                return origGetParam.call(this, param);
            }, window);

            const origReadPixels = proto.readPixels;
            proto.readPixels = exportFunction(function (x, y, w, h, fmt, type, pixels) {
                origReadPixels.call(this, x, y, w, h, fmt, type, pixels);
                if (pixels && pixels.length < 100000) {
                    for (let i = 0; i < pixels.length; i += 64) {
                        pixels[i] = (pixels[i] ^ (seededRandom(i) & 0x01)) & 0xFF;
                    }
                }
            }, window);
        }
        patchWebGL('WebGLRenderingContext');
        patchWebGL('WebGL2RenderingContext');
    } catch (e) { }

    // ════════════════════════════════════════════════
    // 9. WEBGL toDataURL BRIDGE
    // ════════════════════════════════════════════════
    try {
        const origToDataURL = pw.HTMLCanvasElement.prototype.toDataURL;
        pw.HTMLCanvasElement.prototype.toDataURL = exportFunction(function (type, quality) {
            if (this.width * this.height > 262144) {
                return origToDataURL.call(this, type, quality);
            }
            const gl = this.getContext('webgl') || this.getContext('experimental-webgl') || this.getContext('webgl2');
            if (!gl) return origToDataURL.call(this, type, quality);

            const w = this.width || 1;
            const h = this.height || 1;
            const pixels = new Uint8Array(w * h * 4);
            gl.readPixels(0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, pixels);

            for (let i = 0; i < pixels.length; i += 128) {
                pixels[i] = clamp(pixels[i] + (seededRandom(i + 9999) & 0x01));
            }

            const offscreen = document.createElement('canvas');
            offscreen.width = w;
            offscreen.height = h;
            const ctx = offscreen.getContext('2d');
            const imgData = ctx.createImageData(w, h);

            for (let row = 0; row < h; row++) {
                const srcRow = (h - 1 - row) * w * 4;
                const dstRow = row * w * 4;
                for (let col = 0; col < w * 4; col++) {
                    imgData.data[dstRow + col] = pixels[srcRow + col];
                }
            }
            ctx.putImageData(imgData, 0, 0);
            return origToDataURL.call(offscreen, type, quality);
        }, window);
    } catch (e) { }

    // ════════════════════════════════════════════════
    // 10. CANVAS 2D getImageData noise
    // ════════════════════════════════════════════════
    try {
        const canvasProto = pw.CanvasRenderingContext2D.prototype;
        const origGetImageData = canvasProto.getImageData;
        canvasProto.getImageData = exportFunction(function (x, y, w, h) {
            const imageData = origGetImageData.call(this, x, y, w, h);
            if (w * h <= 10000) {
                const d = imageData.data;
                for (let i = 0; i < d.length; i += 200) {
                    d[i] = clamp(d[i] + noise(i, 1));
                }
            }
            return imageData;
        }, window);
    } catch (e) { }

    // ════════════════════════════════════════════════
    // 11. AUDIOCONTEXT NOISE — JS fallback (FPP not active)
    // ════════════════════════════════════════════════
    try {
        const origGetChannelData = pw.AudioBuffer.prototype.getChannelData;
        pw.AudioBuffer.prototype.getChannelData = exportFunction(function () {
            const data = origGetChannelData.apply(this, arguments);
            for (let i = 0; i < data.length; i += 100) {
                data[i] += (seededRandom(i) & 0xFF) / 255 * 0.0000002 - 0.0000001;
            }
            return data;
        }, window);
    } catch (e) { }


    // ════════════════════════════════════════════════
    // 12. BATTERY
    // ════════════════════════════════════════════════
    try {
        if (pw.navigator.getBattery) {
            pw.navigator.getBattery = exportFunction(function () {
                return Promise.resolve(cloneInto({
                    charging: true, chargingTime: Infinity,
                    dischargingTime: Infinity, level: 1.0,
                    addEventListener: function () { },
                    removeEventListener: function () { }
                }, window, { cloneFunctions: true }));
            }, window);
        }
    } catch (e) { }

    // ════════════════════════════════════════════════
    // 15. WebRTC STUN block
    // ════════════════════════════════════════════════
    try {
        if (pw.RTCPeerConnection) {
            const origRTC = pw.RTCPeerConnection;
            pw.RTCPeerConnection = exportFunction(function (config, constraints) {
                if (config && config.iceServers) {
                    config.iceServers = config.iceServers.filter(
                        function (s) { return s.urls && s.urls.toString().includes('turn:'); }
                    );
                }
                return new origRTC(config, constraints);
            }, window);
            pw.RTCPeerConnection.prototype = origRTC.prototype;
        }
    } catch (e) { }

    // ════════════════════════════════════════════════
    // 16. MEDIA EXTRACTION (Airlock)
    // ════════════════════════════════════════════════
    browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
        if (message.type === 'extractMedia') {
            const runExtraction = () => {
                const media = {
                    images: [],
                    videos: [],
                    audio: []
                };

                document.querySelectorAll('img').forEach(function (img) {
                    if (img.src && !img.src.startsWith('data:') && img.src.startsWith('http')) {
                        media.images.push({
                            url: img.src,
                            title: img.alt || img.title || '',
                            metadata: img.naturalWidth + 'x' + img.naturalHeight
                        });
                    }
                });

                document.querySelectorAll('*').forEach(function (el) {
                    try {
                        const bg = window.getComputedStyle(el).backgroundImage;
                        if (bg && bg !== 'none' && bg.includes('url(')) {
                            const urlMatch = bg.match(/url\(["']?(.+?)["']?\)/);
                            if (urlMatch && urlMatch[1] && !urlMatch[1].startsWith('data:') && urlMatch[1].startsWith('http')) {
                                media.images.push({
                                    url: urlMatch[1],
                                    title: 'Background Image',
                                    metadata: ''
                                });
                            }
                        }
                    } catch (e) { }
                });

                document.querySelectorAll('video').forEach(function (video) {
                    if (video.src && video.src.startsWith('http')) {
                        media.videos.push({
                            url: video.src,
                            title: video.title || video.getAttribute('aria-label') || '',
                            metadata: video.duration ? Math.floor(video.duration) + 's' : ''
                        });
                    }
                    video.querySelectorAll('source').forEach(function (source) {
                        if (source.src && source.src.startsWith('http')) {
                            media.videos.push({
                                url: source.src,
                                title: video.title || video.getAttribute('aria-label') || '',
                                metadata: video.duration ? Math.floor(video.duration) + 's' : ''
                            });
                        }
                    });
                });

                document.querySelectorAll('audio').forEach(function (audio) {
                    if (audio.src && audio.src.startsWith('http')) {
                        media.audio.push({
                            url: audio.src,
                            title: audio.title || audio.getAttribute('aria-label') || '',
                            metadata: audio.duration ? Math.floor(audio.duration) + 's' : ''
                        });
                    }
                    audio.querySelectorAll('source').forEach(function (source) {
                        if (source.src && source.src.startsWith('http')) {
                            media.audio.push({
                                url: source.src,
                                title: audio.title || audio.getAttribute('aria-label') || '',
                                metadata: audio.duration ? Math.floor(audio.duration) + 's' : ''
                            });
                        }
                    });
                });

                const unique = (arr) => Array.from(new Set(arr.map(JSON.stringify))).map(JSON.parse);
                media.images = unique(media.images);
                media.videos = unique(media.videos);
                media.audio = unique(media.audio);

                sendResponse(media);
            };

            if (document.readyState === 'complete') {
                runExtraction();
            } else {
                window.addEventListener('load', runExtraction, { once: true });
            }
            return true; // Keep channel open for async response
        } else if (message.type === 'toggle_boomer') {
            if (message.enabled) {
                if (window.__boomerModeEnabled) return;
                window.__boomerModeEnabled = true;

                var style = document.getElementById('__boomer_hover_style');
                if (!style) {
                    style = document.createElement('style');
                    style.id = '__boomer_hover_style';
                    style.innerHTML = '.__boomer_hover { outline: 3px solid red !important; outline-offset: -3px; background-color: rgba(255,0,0,0.1) !important; transition: outline 0.1s ease; }';
                    document.head.appendChild(style);
                }

                if (!window.__boomerTouchStart) {
                    window.__boomerTouchStart = function (e) {
                        if (!window.__boomerModeEnabled) return;
                        var el = e.target;
                        var prev = document.querySelector('.__boomer_hover');
                        if (prev) prev.classList.remove('__boomer_hover');
                        el.classList.add('__boomer_hover');
                    };
                    window.__boomerTouchEnd = function (e) {
                        if (!window.__boomerModeEnabled) return;
                        e.preventDefault();
                        e.stopPropagation();
                        var el = e.target;
                        el.classList.remove('__boomer_hover');
                        el.remove(); // Boom!
                    };
                    document.addEventListener('touchstart', window.__boomerTouchStart, { passive: true, capture: true });
                    document.addEventListener('touchend', window.__boomerTouchEnd, { passive: false, capture: true });
                }
            } else {
                if (!window.__boomerModeEnabled) return;
                window.__boomerModeEnabled = false;
                var styleToRemove = document.getElementById('__boomer_hover_style');
                if (styleToRemove) styleToRemove.remove();
                var hoveredEls = document.querySelectorAll('.__boomer_hover');
                hoveredEls.forEach(function (el) {
                    el.classList.remove('__boomer_hover');
                });
                document.removeEventListener('touchstart', window.__boomerTouchStart, true);
                document.removeEventListener('touchend', window.__boomerTouchEnd, true);
            }
        }
    });

    console.log('[JusBrowse] L5: Native UA/Touch/TZ/Audio | JS: Screen/WebGL/Canvas/Airlock');
})();
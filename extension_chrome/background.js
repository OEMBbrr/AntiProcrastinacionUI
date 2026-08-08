let isLocked = false;
let remainingSeconds = 0;
let lockInterval = null;
let parentalEnabled = false;

let defaultDomains = [
    "facebook.com",
    "instagram.com",
    "tiktok.com",
    "youtube.com",
    "x.com",
    "twitter.com",
    "reddit.com"
];

const adultDomains = [
    // Pornhub dominios principales y TLDs internacionales
    "pornhub.com",
    "pornhub.net",
    "pornhub.org",
    "pornhub.club",
    "pornhubselect.com",
    "pornhubpremium.com",
    "pornhublive.com",
    "pornhubvids.com",
    "pornhubcasino.com",
    "pornhub.es",
    "pornhub.fr",
    "pornhub.de",
    "pornhub.it",
    "pornhub.cz",
    "pornhub.com.br",
    "phncdn.com",
    "phprcdn.com",
    "phncdn.net",
    
    // Red oficial de sitios hermanos de Pornhub (MindGeek / Aylo Network)
    "thumbzilla.com",
    "thumbzilla.net",
    "thumbzilla.org",
    "thumbzilla",
    "m.thumbzilla.com",
    "tza.co",
    "youporn.com",
    "redtube.com",
    "tube8.com",
    "spankwire.com",
    "gaytube.com",
    "extremetube.com",

    // Otros portales adultos principales
    "xvideos.com",
    "xnxx.com",
    "xhamster.com",
    "onlyfans.com",
    "chaturbate.com",
    "stripchat.com"
];

let customDomains = [...defaultDomains];

// Cargar configuración guardada
chrome.storage.local.get(['customDomains', 'parentalEnabled'], (res) => {
    if (res.customDomains && Array.isArray(res.customDomains)) {
        customDomains = res.customDomains;
    }
    if (res.parentalEnabled !== undefined) {
        parentalEnabled = res.parentalEnabled;
    }
    updateNetRules();
});

function getActiveRulesList() {
    let active = [...customDomains];
    if (parentalEnabled) {
        active = [...new Set([...active, ...adultDomains])];
    }
    return active;
}

function getActiveKeywords() {
    const rawList = getActiveRulesList();
    const keywords = new Set();

    rawList.forEach(item => {
        const lower = item.toLowerCase().trim();
        keywords.add(lower);

        // Extraer palabra clave de marca raíz ej. "instagram", "facebook", "pornhub", "tiktok", "youtube"
        const brandKey = lower.replace(/^https?:\/\//, '').replace(/^www\./, '').split('.')[0];
        if (brandKey && brandKey.length >= 3) {
            keywords.add(brandKey);
        }
    });

    return Array.from(keywords);
}

function updateNetRules() {
    chrome.declarativeNetRequest.getDynamicRules((existingRules) => {
        const removeIds = existingRules.map(r => r.id);
        const activeKeywords = getActiveKeywords();

        if (isLocked || parentalEnabled) {
            const rules = activeKeywords.map((kw, index) => ({
                id: index + 1,
                priority: 1,
                action: { type: 'redirect', redirect: { extensionPath: '/blocked.html' } },
                condition: { urlFilter: `*${kw}*`, resourceTypes: ['main_frame', 'sub_frame'] }
            }));

            chrome.declarativeNetRequest.updateDynamicRules({
                removeRuleIds: removeIds,
                addRules: rules
            });
        } else {
            chrome.declarativeNetRequest.updateDynamicRules({
                removeRuleIds: removeIds
            });
        }
    });
}

function isUrlBlocked(url) {
    if (!url || url.includes('/blocked.html') || url.startsWith('chrome://') || url.startsWith('chrome-extension://')) {
        return false;
    }
    const lowerUrl = url.toLowerCase();
    const keywords = getActiveKeywords();
    return keywords.some(kw => lowerUrl.includes(kw));
}

// Interceptor Temprano de Navegación de Enlaces Directos, Reels, Perfiles y Subrutas (webNavigation)
chrome.webNavigation.onBeforeNavigate.addListener((details) => {
    if (details.frameId !== 0) return;
    if ((isLocked || parentalEnabled) && isUrlBlocked(details.url)) {
        chrome.tabs.update(details.tabId, { url: chrome.runtime.getURL('/blocked.html') });
    }
});

// Fail-safe Tab Interceptor (Redirección instantánea basada en pestañas activas)
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
    const targetUrl = changeInfo.url || (tab && tab.url);
    if ((isLocked || parentalEnabled) && isUrlBlocked(targetUrl)) {
        chrome.tabs.update(tabId, { url: chrome.runtime.getURL('/blocked.html') });
    }
});

let connectedDeviceInfo = null;

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === 'getState') {
        sendResponse({ isLocked, remainingSeconds, customDomains, parentalEnabled, connectedDeviceInfo });
    } else if (request.action === 'startTimer') {
        isLocked = true;
        remainingSeconds = request.minutes * 60;
        updateNetRules();
        startBackgroundTimer();
        pushLockStateToFirebase(true, request.minutes);
        sendResponse({ success: true });
    } else if (request.action === 'stopTimer') {
        isLocked = false;
        remainingSeconds = 0;
        clearInterval(lockInterval);
        updateNetRules();
        pushLockStateToFirebase(false, 0);
        sendResponse({ success: true });
    } else if (request.action === 'grantTregua') {
        remainingSeconds = request.minutes * 60;
        sendResponse({ success: true });
    } else if (request.action === 'addDomain') {
        const cleanDomain = request.domain.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
        if (cleanDomain && !customDomains.includes(cleanDomain)) {
            customDomains.push(cleanDomain);
            chrome.storage.local.set({ customDomains });
            updateNetRules();
        }
        sendResponse({ success: true, customDomains });
    } else if (request.action === 'removeDomain') {
        customDomains = customDomains.filter(d => d !== request.domain);
        chrome.storage.local.set({ customDomains });
        updateNetRules();
        sendResponse({ success: true, customDomains });
    } else if (request.action === 'toggleParental') {
        parentalEnabled = request.enabled;
        chrome.storage.local.set({ parentalEnabled });
        updateNetRules();
        sendResponse({ success: true, parentalEnabled });
    } else if (request.action === 'setSyncKey') {
        firebaseSyncKey = request.syncKey;
        chrome.storage.local.set({ syncKey: firebaseSyncKey });
        pollFirebaseSync();
        sendResponse({ success: true, firebaseSyncKey });
    }
    return true;
});

function startBackgroundTimer() {
    clearInterval(lockInterval);
    lockInterval = setInterval(() => {
        if (remainingSeconds > 0) {
            remainingSeconds--;
        } else {
            isLocked = false;
            clearInterval(lockInterval);
            updateNetRules();
            pushLockStateToFirebase(false, 0);
        }
    }, 1000);
}

// ================================================================================
// FIREBASE REALTIME DATABASE SYNC CLIENT (SINGLE SOURCE OF TRUTH)
// ================================================================================
let firebaseSyncKey = "USER_DEFAULT_12345";
let firebaseDbUrl = "https://antiprocrastinacion-sync-default-rtdb.firebaseio.com";

function cleanSyncKey(key) {
    if (!key) return "USER_DEFAULT_12345";
    return key.toLowerCase().trim().replace(/[^a-z0-9_@.-]/g, '_');
}

chrome.storage.local.get(['syncKey', 'firebaseDbUrl'], (res) => {
    if (res.syncKey) firebaseSyncKey = cleanSyncKey(res.syncKey);
    if (res.firebaseDbUrl) firebaseDbUrl = res.firebaseDbUrl;
    pollFirebaseSync();
});

function pushLockStateToFirebase(locked, durationMinutes) {
    const expiresAt = locked ? (Date.now() + (durationMinutes * 60 * 1000)) : 0;
    const payload = {
        is_locked: locked,
        expires_at: expiresAt,
        updated_at: Date.now(),
        source_device: "chrome_extension"
    };

    const targetKey = cleanSyncKey(firebaseSyncKey);
    fetch(`${firebaseDbUrl}/users/${targetKey}/lock_state.json`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    }).catch(() => {});
}

function pollFirebaseSync() {
    const targetKey = cleanSyncKey(firebaseSyncKey);

    // 1. Obtener información de dispositivo y comprobar latido (Heartbeat)
    fetch(`${firebaseDbUrl}/users/${targetKey}/device_info.json`)
        .then(res => res.json())
        .then(deviceData => {
            if (deviceData && deviceData.brand && deviceData.last_ping) {
                const now = Date.now();
                // Latido válido si se ha recibido en los últimos 60 segundos
                if (now - deviceData.last_ping < 60000) {
                    connectedDeviceInfo = deviceData;
                } else {
                    connectedDeviceInfo = null; // Dispositivo desconectado (ping viejo)
                }
            } else {
                connectedDeviceInfo = null;
            }
        })
        .catch(() => { connectedDeviceInfo = null; });

    // 2. Obtener estado de bloqueo
    fetch(`${firebaseDbUrl}/users/${targetKey}/lock_state.json`)
        .then(res => res.json())
        .then(data => {
            if (data && typeof data.is_locked === 'boolean') {
                const cloudLocked = data.is_locked;
                const expiresAt = data.expires_at || 0;
                const now = Date.now();

                if (cloudLocked && now < expiresAt) {
                    isLocked = true;
                    remainingSeconds = Math.max(0, Math.floor((expiresAt - now) / 1000));
                    updateNetRules();
                    if (!lockInterval) startBackgroundTimer();
                } else if (!cloudLocked || now >= expiresAt) {
                    if (isLocked) {
                        isLocked = false;
                        remainingSeconds = 0;
                        clearInterval(lockInterval);
                        lockInterval = null;
                        updateNetRules();
                    }
                }
            }
        })
        .catch(() => {});
}

setInterval(pollFirebaseSync, 1500);
pollFirebaseSync();

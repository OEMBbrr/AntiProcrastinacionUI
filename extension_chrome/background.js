let isLocked = false;
let remainingSeconds = 0;
let lockInterval = null;
let parentalEnabled = false;

// ================================================================================
// FIREBASE AUTH & CONFIG
// ================================================================================
const FIREBASE_API_KEY = "AIzaSyBNw12V7mMCt76ZdP89NEYQwg_E2vshRto";
const FIREBASE_DB_URL = "https://antiprocrastinacion-26975-default-rtdb.firebaseio.com";
const WEB_CLIENT_ID = "927134130052-3lmelvvnfuk1dg8o71vosjdkf8s8k3p5.apps.googleusercontent.com";

let firebaseUid = null;
let firebaseIdToken = null;
let firebaseRefreshToken = null;
let userEmail = "";
let syncKey = "";

function cleanKey(key) {
    if (!key) return "USER_DEFAULT_12345";
    return key.toLowerCase().trim().replace(/[^a-z0-9_@.-]/g, '_');
}

function getTargetKey() {
    if (firebaseUid) return firebaseUid;
    if (syncKey) return cleanKey(syncKey);
    if (userEmail) return cleanKey(userEmail);
    return "USER_DEFAULT_12345";
}

// Cargar sesión guardada
chrome.storage.local.get(['firebaseUid', 'firebaseIdToken', 'firebaseRefreshToken', 'userEmail', 'syncKey'], (res) => {
    if (res.firebaseUid) firebaseUid = res.firebaseUid;
    if (res.firebaseIdToken) firebaseIdToken = res.firebaseIdToken;
    if (res.firebaseRefreshToken) firebaseRefreshToken = res.firebaseRefreshToken;
    if (res.userEmail) userEmail = res.userEmail;
    if (res.syncKey) syncKey = res.syncKey;
    pollFirebaseSync();
});

/**
 * Inicia sesión con Google usando chrome.identity API
 * Con fallback a correo / PIN si no hay token de extensión
 */
function signInWithGoogle() {
    return new Promise((resolve, reject) => {
        // 1. Intentar chrome.identity.getAuthToken primero
        chrome.identity.getAuthToken({ interactive: true }, (token) => {
            if (!chrome.runtime.lastError && token) {
                // Intercambiar Google Access Token con UserInfo API
                fetch(`https://www.googleapis.com/oauth2/v3/userinfo?access_token=${token}`)
                    .then(res => res.json())
                    .then(userInfo => {
                        if (userInfo && userInfo.email) {
                            userEmail = userInfo.email;
                            syncKey = cleanKey(userEmail);
                            firebaseUid = userInfo.sub || syncKey;
                            chrome.storage.local.set({ userEmail, syncKey, firebaseUid });
                            pushProfileToFirebase();
                            pollFirebaseSync();
                            resolve({ uid: firebaseUid, email: userEmail });
                        } else {
                            fallbackPromptLogin(resolve, reject);
                        }
                    })
                    .catch(() => fallbackPromptLogin(resolve, reject));
            } else {
                // 2. Fallback a WebAuthFlow o Login por Correo / PIN
                const redirectUrl = chrome.identity.getRedirectURL();
                const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?` +
                    `client_id=${WEB_CLIENT_ID}` +
                    `&response_type=token id_token` +
                    `&redirect_uri=${encodeURIComponent(redirectUrl)}` +
                    `&scope=${encodeURIComponent('openid email profile')}` +
                    `&nonce=${Math.random().toString(36).substr(2)}`;

                chrome.identity.launchWebAuthFlow(
                    { url: authUrl, interactive: true },
                    (responseUrl) => {
                        if (chrome.runtime.lastError || !responseUrl) {
                            // Fallback seguro por correo de Google o PIN
                            fallbackPromptLogin(resolve, reject);
                            return;
                        }

                        const hashParams = new URLSearchParams(responseUrl.split('#')[1]);
                        const idToken = hashParams.get('id_token');

                        if (!idToken) {
                            fallbackPromptLogin(resolve, reject);
                            return;
                        }

                        fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=${FIREBASE_API_KEY}`, {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({
                                postBody: `id_token=${idToken}&providerId=google.com`,
                                requestUri: redirectUrl,
                                returnIdpCredential: true,
                                returnSecureToken: true
                            })
                        })
                        .then(res => res.json())
                        .then(data => {
                            if (data.localId) {
                                firebaseUid = data.localId;
                                firebaseIdToken = data.idToken;
                                firebaseRefreshToken = data.refreshToken;
                                userEmail = data.email || "";
                                syncKey = cleanKey(userEmail || firebaseUid);

                                chrome.storage.local.set({
                                    firebaseUid,
                                    firebaseIdToken,
                                    firebaseRefreshToken,
                                    userEmail,
                                    syncKey
                                });

                                pushProfileToFirebase();
                                pollFirebaseSync();
                                resolve({ uid: firebaseUid, email: userEmail });
                            } else {
                                fallbackPromptLogin(resolve, reject);
                            }
                        })
                        .catch(() => fallbackPromptLogin(resolve, reject));
                    }
                );
            }
        });
    });
}

function fallbackPromptLogin(resolve, reject) {
    // Si falla el popup OAuth, devolver instrucción para ingresar correo o PIN
    resolve({ fallbackRequired: true });
}

function setManualSyncKey(key) {
    if (!key) return;
    const clean = cleanKey(key);
    syncKey = clean;
    userEmail = key.includes('@') ? key : userEmail;
    chrome.storage.local.set({ syncKey: clean, userEmail });
    pollFirebaseSync();
}

/**
 * Refresca el Firebase ID Token usando el refresh token
 */
function refreshFirebaseToken() {
    if (!firebaseRefreshToken) return Promise.resolve(null);

    return fetch(`https://securetoken.googleapis.com/v1/token?key=${FIREBASE_API_KEY}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `grant_type=refresh_token&refresh_token=${firebaseRefreshToken}`
    })
    .then(res => res.json())
    .then(data => {
        if (data.id_token) {
            firebaseIdToken = data.id_token;
            firebaseRefreshToken = data.refresh_token;
            chrome.storage.local.set({ firebaseIdToken, firebaseRefreshToken });
            return firebaseIdToken;
        }
        return null;
    })
    .catch(() => null);
}

/**
 * Hace un fetch a Firebase RTDB
 */
async function fetchDb(path, options = {}) {
    let url = `${FIREBASE_DB_URL}${path}`;
    if (firebaseIdToken) {
        url += `?auth=${firebaseIdToken}`;
    }
    let res = await fetch(url, options);
    if (res.status === 401 && firebaseRefreshToken) {
        const newToken = await refreshFirebaseToken();
        if (newToken) {
            url = `${FIREBASE_DB_URL}${path}?auth=${newToken}`;
            res = await fetch(url, options);
        }
    }
    return res;
}

// ================================================================================
// DOMINIOS Y BLOQUEO
// ================================================================================
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
    "pornhub.com", "pornhub.net", "pornhub.org", "pornhub.club",
    "pornhubselect.com", "pornhubpremium.com", "pornhublive.com",
    "pornhub.es", "pornhub.fr", "pornhub.de", "pornhub.it",
    "phncdn.com", "phprcdn.com", "phncdn.net",
    "thumbzilla.com", "youporn.com", "redtube.com", "tube8.com",
    "spankwire.com", "gaytube.com", "extremetube.com",
    "xvideos.com", "xnxx.com", "xhamster.com",
    "onlyfans.com", "chaturbate.com", "stripchat.com"
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
    let active = [];
    if (isLocked) {
        active = [...customDomains];
    }
    if (parentalEnabled) {
        active = [...new Set([...active, ...adultDomains])];
    }
    return active;
}

function extractKeywords(domainList) {
    const keywords = new Set();
    domainList.forEach(item => {
        const lower = item.toLowerCase().trim();
        keywords.add(lower);
        const brandKey = lower.replace(/^https?:\/\//, '').replace(/^www\./, '').split('.')[0];
        if (brandKey && brandKey.length >= 3) {
            keywords.add(brandKey);
        }
    });
    return Array.from(keywords);
}

function getActiveKeywords() {
    return extractKeywords(getActiveRulesList());
}

function updateNetRules() {
    chrome.declarativeNetRequest.getDynamicRules((existingRules) => {
        const removeIds = existingRules.map(r => r.id);
        const activeKeywords = getActiveKeywords();

        if (activeKeywords.length > 0) {
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

    if (isLocked) {
        const focusKeywords = extractKeywords(customDomains);
        if (focusKeywords.some(kw => lowerUrl.includes(kw))) return true;
    }

    if (parentalEnabled) {
        const parentalKeywords = extractKeywords(adultDomains);
        if (parentalKeywords.some(kw => lowerUrl.includes(kw))) return true;
    }

    return false;
}

chrome.webNavigation.onBeforeNavigate.addListener((details) => {
    if (details.frameId !== 0) return;
    if (isUrlBlocked(details.url)) {
        chrome.tabs.update(details.tabId, { url: chrome.runtime.getURL('/blocked.html') });
    }
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
    const targetUrl = changeInfo.url || (tab && tab.url);
    if (isUrlBlocked(targetUrl)) {
        chrome.tabs.update(tabId, { url: chrome.runtime.getURL('/blocked.html') });
    }
});

// ================================================================================
// MENSAJES DEL POPUP
// ================================================================================
let connectedDeviceInfo = null;

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === 'getState') {
        sendResponse({
            isLocked, remainingSeconds, customDomains, parentalEnabled,
            connectedDeviceInfo, firebaseUid, userEmail, syncKey
        });
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
    } else if (request.action === 'googleSignIn') {
        signInWithGoogle()
            .then(result => sendResponse({ success: true, ...result }))
            .catch(err => sendResponse({ success: false, error: err }));
        return true;
    } else if (request.action === 'setManualKey') {
        setManualSyncKey(request.key);
        sendResponse({ success: true, syncKey });
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
// FIREBASE REALTIME DATABASE SYNC
// ================================================================================

function pushProfileToFirebase() {
    const target = getTargetKey();
    fetchDb(`/users/${target}/profile.json`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: userEmail || target, role: "admin" })
    }).catch(() => {});
}

function pushLockStateToFirebase(locked, durationMinutes) {
    const target = getTargetKey();
    const expiresAt = locked ? (Date.now() + (durationMinutes * 60 * 1000)) : 0;
    const payload = {
        is_locked: locked,
        expires_at: expiresAt,
        updated_at: Date.now(),
        source_device: "chrome_extension"
    };

    fetchDb(`/users/${target}/lock_state.json`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    }).catch(() => {});
}

function pollFirebaseSync() {
    const target = getTargetKey();
    const now = Date.now();

    // 1. Enviar latido de la Extensión (extension_last_ping)
    fetchDb(`/users/${target}/device_info/extension_last_ping.json`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(now)
    }).catch(() => {});

    // 2. Obtener información de dispositivo Android y comprobar latido
    fetchDb(`/users/${target}/device_info.json`)
        .then(res => res ? res.json() : null)
        .then(deviceData => {
            if (deviceData && (deviceData.brand || deviceData.model)) {
                const androidPing = deviceData.android_last_ping || deviceData.last_ping || 0;
                if (now - androidPing < 30000) {
                    connectedDeviceInfo = deviceData;
                } else {
                    connectedDeviceInfo = null;
                }
            } else {
                connectedDeviceInfo = null;
            }
        })
        .catch(() => { connectedDeviceInfo = null; });

    // 3. Obtener estado de bloqueo
    fetchDb(`/users/${target}/lock_state.json`)
        .then(res => res ? res.json() : null)
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

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

// Cargar sesión guardada
chrome.storage.local.get(['firebaseUid', 'firebaseIdToken', 'firebaseRefreshToken', 'userEmail'], (res) => {
    if (res.firebaseUid) firebaseUid = res.firebaseUid;
    if (res.firebaseIdToken) firebaseIdToken = res.firebaseIdToken;
    if (res.firebaseRefreshToken) firebaseRefreshToken = res.firebaseRefreshToken;
    if (res.userEmail) userEmail = res.userEmail;
    if (firebaseUid) {
        pollFirebaseSync();
    }
});

/**
 * Inicia sesión con Google usando chrome.identity.launchWebAuthFlow()
 * Intercambia el código de autorización con Firebase Auth REST API
 */
function signInWithGoogle() {
    return new Promise((resolve, reject) => {
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
                    reject(chrome.runtime.lastError?.message || "Auth cancelada");
                    return;
                }

                // Extraer id_token de la URL de respuesta
                const hashParams = new URLSearchParams(responseUrl.split('#')[1]);
                const idToken = hashParams.get('id_token');
                const accessToken = hashParams.get('access_token');

                if (!idToken) {
                    reject("No se obtuvo id_token");
                    return;
                }

                // Intercambiar con Firebase Auth REST API
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

                        chrome.storage.local.set({
                            firebaseUid,
                            firebaseIdToken,
                            firebaseRefreshToken,
                            userEmail
                        });

                        // Escribir perfil
                        pushProfileToFirebase();
                        pollFirebaseSync();
                        resolve({ uid: firebaseUid, email: userEmail });
                    } else {
                        reject(data.error?.message || "Error Firebase Auth");
                    }
                })
                .catch(err => reject(err.message));
            }
        );
    });
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
 * Hace un fetch autenticado a Firebase RTDB, refrescando el token si es necesario
 */
async function authFetch(url, options = {}) {
    if (!firebaseIdToken) return null;

    let fullUrl = `${url}?auth=${firebaseIdToken}`;
    let res = await fetch(fullUrl, options);

    // Si el token expiró (401), refrescar e intentar de nuevo
    if (res.status === 401) {
        const newToken = await refreshFirebaseToken();
        if (newToken) {
            fullUrl = `${url}?auth=${newToken}`;
            res = await fetch(fullUrl, options);
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

// Obtener lista de dominios activos SEPARANDO enfoque y parental
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

    // Verificar sitios personalizados SOLO si está en modo enfoque
    if (isLocked) {
        const focusKeywords = extractKeywords(customDomains);
        if (focusKeywords.some(kw => lowerUrl.includes(kw))) return true;
    }

    // Verificar sitios adultos SOLO si control parental está activo
    if (parentalEnabled) {
        const parentalKeywords = extractKeywords(adultDomains);
        if (parentalKeywords.some(kw => lowerUrl.includes(kw))) return true;
    }

    return false;
}

// Interceptor de Navegación
chrome.webNavigation.onBeforeNavigate.addListener((details) => {
    if (details.frameId !== 0) return;
    if (isUrlBlocked(details.url)) {
        chrome.tabs.update(details.tabId, { url: chrome.runtime.getURL('/blocked.html') });
    }
});

// Fail-safe Tab Interceptor
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
            connectedDeviceInfo, firebaseUid, userEmail
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
        return true; // Keep message channel open for async response
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
// FIREBASE REALTIME DATABASE SYNC (CON AUTENTICACIÓN)
// ================================================================================

function pushProfileToFirebase() {
    if (!firebaseUid || !firebaseIdToken) return;
    authFetch(`${FIREBASE_DB_URL}/users/${firebaseUid}/profile.json`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: userEmail, role: "admin" })
    }).catch(() => {});
}

function pushLockStateToFirebase(locked, durationMinutes) {
    if (!firebaseUid || !firebaseIdToken) return;
    const expiresAt = locked ? (Date.now() + (durationMinutes * 60 * 1000)) : 0;
    const payload = {
        is_locked: locked,
        expires_at: expiresAt,
        updated_at: Date.now(),
        source_device: "chrome_extension"
    };

    authFetch(`${FIREBASE_DB_URL}/users/${firebaseUid}/lock_state.json`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    }).catch(() => {});
}

function pollFirebaseSync() {
    if (!firebaseUid || !firebaseIdToken) return;
    const now = Date.now();

    // 1. Enviar latido de la Extensión (extension_last_ping)
    authFetch(`${FIREBASE_DB_URL}/users/${firebaseUid}/device_info/extension_last_ping.json`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(now)
    }).catch(() => {});

    // 2. Obtener información de dispositivo Android y comprobar latido
    authFetch(`${FIREBASE_DB_URL}/users/${firebaseUid}/device_info.json`)
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
    authFetch(`${FIREBASE_DB_URL}/users/${firebaseUid}/lock_state.json`)
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

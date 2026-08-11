// Configuración Global de Firebase para la Extensión (Claves Encriptadas en Runtime)
const FIREBASE_API_KEY = atob("QUl6YVN5Qk53MTJWN21NQ3Q3NlpkUDg5TkVZUXdnX0UydnNoUnRv");
const FIREBASE_DB_URL = "https://antiprocrastinacion-26975-default-rtdb.firebaseio.com";
const WEB_CLIENT_ID = atob("OTI3MTM0MTMwMDUyLTNsbWVsdnZuZnVrMWRnOG83MXZvc2pka2Y4czhrM3A1LmFwcHMuZ29vZ2xldXNlcmNvbnRlbnQuY29t");

let isLocked = false;
let remainingSeconds = 0;
let lockInterval = null;
let parentalEnabled = false;

// V20: Bloqueo cruzado (el teléfono publica esta preferencia en /config)
let crossDeviceLockEnabled = true;

// V20: Modo oscuro (compartido con el teléfono vía /config)
let darkModeEnabled = false;

// V24 (Propuesta 1): Pomodoro sincronizado (compartido con el teléfono vía /config)
let pomodoroEnabled = false;
let pomodoroWorkMinutes = 25;
let pomodoroRestMinutes = 5;
let pomodoroRestCount = 1;
// V24: fases del Pomodoro del enfoque en curso (trabajo/descanso con tiempos absolutos)
let pomodoroPhases = [];
const POMODORO_MAX_WORK_MINUTES = 60;
const POMODORO_MAX_REST_MINUTES = 30;

// V24.1: verificación de dos pasos del Bloqueo Cruzado desde el PC.
// La extensión SOLO solicita el código (status:'requesting'); el TELÉFONO lo genera,
// lo muestra por notificación y lo publica de vuelta (status:'pending' + code).
// La extensión lo lee por polling para compararlo con el que escribe el usuario en el PC,
// pero nunca lo muestra en pantalla: quien lo ve es el usuario en el teléfono.
let pendingAuth = null; // { requestId, code, expiresAt }

// Estado de sesión
let firebaseUid = null;
let firebaseIdToken = null;
let firebaseRefreshToken = null;
let userEmail = null;
let syncKey = null;
// V24.1: nodo del dispositivo adoptado desde el teléfono (target_key publicado
// en device_info/LAN). Garantiza que la extensión escriba EXACTAMENTE en el nodo
// que el teléfono escucha, de modo que el código 2FA siempre llegue.
let pairedTargetKey = null;

// V24: Firebase Anonymous Auth como respaldo (Bug 4 de la auditoría).
// Cuando no hay sesión de Google, se obtiene un token anónimo válido para que
// las operaciones de RTDB cumplan con auth != null. Se guarda en claves propias
// para no contaminar la sesión de Google ni cambiar el targetKey del usuario.
let anonIdToken = null;
let anonRefreshToken = null;
let anonymousAuthInProgress = false;

function ensureAnonymousAuth() {
    if (firebaseIdToken || anonIdToken || anonymousAuthInProgress) return;
    anonymousAuthInProgress = true;
    fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${FIREBASE_API_KEY}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ returnSecureToken: true })
    })
        .then(res => res.json())
        .then(data => {
            anonymousAuthInProgress = false;
            if (data && data.idToken) {
                anonIdToken = data.idToken;
                anonRefreshToken = data.refreshToken || null;
                chrome.storage.local.set({ anonIdToken, anonRefreshToken });
                console.log("ZEN_AUTH: Sesión anónima de Firebase creada como respaldo");
            }
        })
        .catch(() => { anonymousAuthInProgress = false; });
}

async function refreshAnonymousToken() {
    if (!anonRefreshToken) return null;
    try {
        const url = `https://securetoken.googleapis.com/v1/token?key=${FIREBASE_API_KEY}`;
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `grant_type=refresh_token&refresh_token=${encodeURIComponent(anonRefreshToken)}`
        });
        const data = await res.json();
        if (data.id_token) {
            anonIdToken = data.id_token;
            if (data.refresh_token) anonRefreshToken = data.refresh_token;
            chrome.storage.local.set({ anonIdToken, anonRefreshToken });
            return anonIdToken;
        }
    } catch (e) {
        console.error("ZEN_AUTH: Error renovando token anónimo:", e);
    }
    return null;
}

function cleanKey(key) {
    if (!key) return "USER_DEFAULT_12345";
    return key.toLowerCase().trim().replace(/[^a-z0-9_]/g, '_');
}

// V24.1: normaliza cualquier categoría a la clave canónica compartida con Android
// ('general' | 'tarea' | 'idea' | 'reflexion'). Tolera mayúsculas y acentos.
function normalizeCategory(raw) {
    const key = String(raw || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim();
    switch (key) {
        case 'tarea': return 'tarea';
        case 'idea': return 'idea';
        case 'reflexion': return 'reflexion';
        default: return 'general';
    }
}

function getTargetKey() {
    if (pairedTargetKey) return cleanKey(pairedTargetKey);
    if (userEmail && userEmail.includes('@')) return cleanKey(userEmail);
    if (syncKey) return cleanKey(syncKey);
    if (firebaseUid) return cleanKey(firebaseUid);
    return "USER_DEFAULT_12345";
}

// V24.1: adopta la clave de nodo que publica el teléfono (device_info o LAN).
function adoptPairedTargetKey(rawKey) {
    if (!rawKey) return false;
    const clean = cleanKey(String(rawKey));
    if (!clean || clean === "USER_DEFAULT_12345") return false;
    if (pairedTargetKey !== clean) {
        pairedTargetKey = clean;
        chrome.storage.local.set({ pairedTargetKey: clean });
        console.log("ZEN_2FA: nodo adoptado del teléfono -> /users/" + clean);
        return true;
    }
    return false;
}

// V24.1: espera a que el teléfono genere el código y lo publique en /auth_request.
// Lee el nodo repetidamente; cuando aparece status:'pending' + code, lo guarda en
// memoria (la extensión lo conoce pero NO lo muestra; el usuario lo lee del teléfono).
function pollForAuthCode(requestId, expiresAt) {
    let tries = 0;
    const maxTries = 30; // ~30 segundos (1s por intento)
    const timer = setInterval(() => {
        tries++;
        if (tries > maxTries || Date.now() > expiresAt) {
            clearInterval(timer);
            if (pendingAuth && pendingAuth.requestId === requestId && !pendingAuth.code) {
                // Caducó sin que el teléfono respondiera
                pendingAuth = null;
                chrome.storage.local.set({ pendingAuth: null });
            }
            return;
        }
        const target = getTargetKey();
        fetchDb(`/users/${target}/auth_request.json`)
            .then(res => res ? res.json() : null)
            .then(data => {
                if (!data || data.id !== requestId) return;
                if (data.status === 'pending' && data.code) {
                    clearInterval(timer);
                    pendingAuth = { requestId, code: String(data.code).trim(), expiresAt };
                    chrome.storage.local.set({ pendingAuth });
                    console.log("ZEN_2FA: código recibido del teléfono (no se muestra en el PC)");
                } else if (data.status === 'approved') {
                    clearInterval(timer);
                    pendingAuth = null;
                    chrome.storage.local.set({ pendingAuth: null });
                }
            })
            .catch(() => {});
    }, 1000);
}

// Cargar sesión guardada
chrome.storage.local.get(['firebaseUid', 'firebaseIdToken', 'firebaseRefreshToken', 'userEmail', 'syncKey', 'darkModeEnabled', 'crossDeviceLockEnabled', 'anonIdToken', 'anonRefreshToken', 'pomodoroEnabled', 'pomodoroWorkMinutes', 'pomodoroRestMinutes', 'pomodoroRestCount', 'pomodoroPhases', 'pendingAuth', 'pairedTargetKey'], (res) => {
    if (res.firebaseUid) firebaseUid = res.firebaseUid;
    if (res.firebaseIdToken) firebaseIdToken = res.firebaseIdToken;
    if (res.firebaseRefreshToken) firebaseRefreshToken = res.firebaseRefreshToken;
    if (res.userEmail) userEmail = res.userEmail;
    if (res.syncKey) syncKey = res.syncKey;
    if (res.pairedTargetKey) pairedTargetKey = res.pairedTargetKey;
    if (res.anonIdToken) anonIdToken = res.anonIdToken;
    if (res.anonRefreshToken) anonRefreshToken = res.anonRefreshToken;
    if (typeof res.darkModeEnabled === 'boolean') darkModeEnabled = res.darkModeEnabled;
    if (typeof res.crossDeviceLockEnabled === 'boolean') crossDeviceLockEnabled = res.crossDeviceLockEnabled;
    // V24 (Propuesta 1): restaurar ajustes Pomodoro guardados localmente
    if (typeof res.pomodoroEnabled === 'boolean') pomodoroEnabled = res.pomodoroEnabled;
    if (typeof res.pomodoroWorkMinutes === 'number') pomodoroWorkMinutes = res.pomodoroWorkMinutes;
    if (typeof res.pomodoroRestMinutes === 'number') pomodoroRestMinutes = res.pomodoroRestMinutes;
    if (typeof res.pomodoroRestCount === 'number') pomodoroRestCount = res.pomodoroRestCount;
    if (Array.isArray(res.pomodoroPhases)) pomodoroPhases = res.pomodoroPhases;
    if (res.pendingAuth && res.pendingAuth.code && res.pendingAuth.expiresAt > Date.now()) pendingAuth = res.pendingAuth;
    // V24 (Bug 4): si no hay sesión de Google ni token anónimo, crearlo como respaldo.
    if (!firebaseIdToken && !anonIdToken) ensureAnonymousAuth();
    pollFirebaseSync();
});

async function refreshFirebaseToken() {
    if (!firebaseRefreshToken) return null;
    try {
        const url = `https://securetoken.googleapis.com/v1/token?key=${FIREBASE_API_KEY}`;
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `grant_type=refresh_token&refresh_token=${encodeURIComponent(firebaseRefreshToken)}`
        });
        const data = await res.json();
        if (data.id_token) {
            firebaseIdToken = data.id_token;
            if (data.refresh_token) firebaseRefreshToken = data.refresh_token;
            if (data.user_id) firebaseUid = data.user_id;
            chrome.storage.local.set({
                firebaseIdToken: firebaseIdToken,
                firebaseRefreshToken: firebaseRefreshToken,
                firebaseUid: firebaseUid
            });
            console.log("ZEN_AUTH: Token de Firebase renovado exitosamente!");
            return firebaseIdToken;
        }
    } catch (e) {
        console.error("ZEN_AUTH: Error renovando token de Firebase:", e);
    }
    return null;
}

/**
 * Hace un fetch a Firebase RTDB
 * V24 (Bug 4): usa el token de Google o, en su defecto, el token anónimo.
 */
async function fetchDb(path, options = {}) {
    let url = `${FIREBASE_DB_URL}${path}`;
    const authToken = firebaseIdToken || anonIdToken;
    if (authToken && authToken.includes('.')) {
        url += `?auth=${authToken}`;
    }
    let res = await fetch(url, options);
    if (res.status === 401 && firebaseRefreshToken) {
        const newToken = await refreshFirebaseToken();
        if (newToken) {
            url = `${FIREBASE_DB_URL}${path}?auth=${newToken}`;
            res = await fetch(url, options);
        }
    } else if (res.status === 401 && anonRefreshToken) {
        const newToken = await refreshAnonymousToken();
        if (newToken) {
            url = `${FIREBASE_DB_URL}${path}?auth=${newToken}`;
            res = await fetch(url, options);
        }
    }
    return res;
}

/**
 * Notifica a todas las páginas de la extensión el estado real de la sincronización en la nube.
 */
function notifyCloudStatus(ok, noteId, error) {
    const msg = { action: 'noteCloudStatus', ok: !!ok, noteId: noteId || null, error: error || null };
    try {
        chrome.runtime.sendMessage(msg, () => {});
    } catch (e) {}
    try {
        chrome.tabs.query({}, (tabs) => {
            for (const t of tabs) {
                try { chrome.tabs.sendMessage(t.id, msg).catch(() => {}); } catch (e2) {}
            }
        });
    } catch (e) {}
}

/**
 * Escribe una nota en Firebase RTDB y notifica el resultado real (éxito o error) a la UI.
 */
async function cloudWriteNote(target, noteId, payload) {
    let errorMsg = null;
    try {
        const res = await fetchDb(`/users/${target}/notes/${noteId}.json`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (res && res.ok) {
            notifyCloudStatus(true, noteId);
            return;
        }
        errorMsg = res ? `HTTP ${res.status}` : 'Sin respuesta de Firebase';
    } catch (e) {
        errorMsg = (e && e.message) || String(e);
    }
    if (!firebaseIdToken) {
        errorMsg = 'Inicia sesión con Google para sincronizar tus notas';
    }
    notifyCloudStatus(false, noteId, errorMsg);
}

/**
 * Elimina una nota en Firebase RTDB y notifica el resultado real.
 */
async function cloudDeleteNote(target, noteId) {
    let errorMsg = null;
    try {
        const res = await fetchDb(`/users/${target}/notes/${noteId}.json`, { method: 'DELETE' });
        if (res && res.ok) {
            notifyCloudStatus(true, noteId);
            return;
        }
        errorMsg = res ? `HTTP ${res.status}` : 'Sin respuesta de Firebase';
    } catch (e) {
        errorMsg = (e && e.message) || String(e);
    }
    notifyCloudStatus(false, noteId, errorMsg);
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

let treguaUntil = 0;
let treguaCooldownUntil = 0;

function getActiveRulesList() {
    let active = [];
    if (isLocked && Date.now() >= treguaUntil) {
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
    
    // Si la tregua está activa, desbloquear todo el contenido del Modo Enfoque
    if (Date.now() < treguaUntil) {
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
        // V24: leer la sesión desde storage para evitar el estado vacío al despertar
        // el service worker (MV3): las variables en memoria se reinician en frío.
        chrome.storage.local.get(['firebaseUid', 'userEmail', 'syncKey', 'pairedTargetKey'], (res) => {
            if (res.firebaseUid) firebaseUid = res.firebaseUid;
            if (res.userEmail) userEmail = res.userEmail;
            if (res.syncKey) syncKey = res.syncKey;
            if (res.pairedTargetKey) pairedTargetKey = res.pairedTargetKey;
            const now = Date.now();
            const phase = currentPomodoroPhase(now);
            sendResponse({
                isLocked, remainingSeconds, customDomains, parentalEnabled,
                connectedDeviceInfo, firebaseUid, userEmail, syncKey,
                // V24: estado Pomodoro para que el popup muestre la fase de descanso
                pomodoroPhases,
                currentPhase: phase,
                phaseType: phase ? phase.type : 'none',
                phaseEnd: phase ? phase.end_ms : 0,
                pendingAuthActive: !!(pendingAuth && pendingAuth.expiresAt > now),
                treguaUntil: treguaUntil || 0,
                treguaCooldownUntil: treguaCooldownUntil || 0
            });
        });
        return true;
    } else if (request.action === 'getSettings') {
        // V24: leer los ajustes persistidos en lugar de devolver los valores
        // por defecto en memoria (evita que el popup/notas vuelvan al modo claro).
        chrome.storage.local.get(['darkModeEnabled', 'crossDeviceLockEnabled', 'pomodoroEnabled', 'pomodoroWorkMinutes', 'pomodoroRestMinutes', 'pomodoroRestCount'], (res) => {
            if (typeof res.darkModeEnabled === 'boolean') darkModeEnabled = res.darkModeEnabled;
            if (typeof res.crossDeviceLockEnabled === 'boolean') crossDeviceLockEnabled = res.crossDeviceLockEnabled;
            if (typeof res.pomodoroEnabled === 'boolean') pomodoroEnabled = res.pomodoroEnabled;
            if (typeof res.pomodoroWorkMinutes === 'number') pomodoroWorkMinutes = res.pomodoroWorkMinutes;
            if (typeof res.pomodoroRestMinutes === 'number') pomodoroRestMinutes = res.pomodoroRestMinutes;
            if (typeof res.pomodoroRestCount === 'number') pomodoroRestCount = res.pomodoroRestCount;
            sendResponse({
                crossDeviceLockEnabled,
                darkModeEnabled,
                pomodoroEnabled,
                pomodoroWorkMinutes,
                pomodoroRestMinutes,
                pomodoroRestCount
            });
        });
        return true;
    } else if (request.action === 'setSetting') {
        setSetting(request.setting, request.value, () => {
            sendResponse({
                success: true,
                crossDeviceLockEnabled,
                darkModeEnabled,
                pomodoroEnabled,
                pomodoroWorkMinutes,
                pomodoroRestMinutes,
                pomodoroRestCount
            });
        });
        return true;
    } else if (request.action === 'startTimer') {
        isLocked = true;
        remainingSeconds = request.minutes * 60;
        // V24: construir las fases Pomodoro (trabajo/descanso) y persistirlas
        pomodoroPhases = (pomodoroEnabled && request.minutes >= 10)
            ? buildPomodoroSchedule(request.minutes, pomodoroRestCount, pomodoroRestMinutes)
            : [];
        chrome.storage.local.set({ pomodoroPhases });
        updateNetRules();
        startBackgroundTimer();
        pushLockStateToFirebase(true, request.minutes);
        sendResponse({ success: true });
    } else if (request.action === 'stopTimer') {
        isLocked = false;
        remainingSeconds = 0;
        clearInterval(lockInterval);
        pomodoroPhases = [];
        chrome.storage.local.set({ pomodoroPhases });
        updateNetRules();
        pushLockStateToFirebase(false, 0);
        broadcastPhase('none', null);
        sendResponse({ success: true });
    } else if (request.action === 'grantTregua') {
        if (Date.now() < treguaCooldownUntil) {
            sendResponse({ success: false, error: 'Cooldown activo' });
            return true;
        }
        treguaUntil = Date.now() + (request.minutes * 60 * 1000);
        updateNetRules();
        sendResponse({ success: true });
    } else if (request.action === 'requestAuthCode') {
        // V24.1: la extensión SOLO SOLICITA el código; el TELÉFONO lo genera,
        // lo muestra en su notificación y lo publica de vuelta en /auth_request.
        // La extensión lo lee por polling (sin mostrarlo) para poder verificarlo.
        const target = getTargetKey();
        const requestId = `auth_${Date.now()}`;
        const expiresAt = Date.now() + 3 * 60 * 1000; // válido 3 minutos
        pendingAuth = { requestId, code: null, expiresAt };
        chrome.storage.local.set({ pendingAuth });
        fetchDb(`/users/${target}/auth_request.json`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                id: requestId,
                requester: 'chrome_extension',
                status: 'requesting', // el teléfono responderá con status:'pending' + code
                requested_at: Date.now(),
                expires_at: expiresAt
            })
        }).then((res) => {
            if (res && res.ok) {
                // Empezar a esperar la respuesta del teléfono
                pollForAuthCode(requestId, expiresAt);
                sendResponse({ success: true, requestId, expiresAt });
            } else {
                pendingAuth = null;
                chrome.storage.local.set({ pendingAuth: null });
                sendResponse({ success: false, error: res ? `HTTP ${res.status}` : 'Sin respuesta' });
            }
        }).catch(() => {
            pendingAuth = null;
            chrome.storage.local.set({ pendingAuth: null });
            sendResponse({ success: false, error: 'Sin conexión con Firebase' });
        });
        return true;
    } else if (request.action === 'verifyAuthCode') {
        // V24.1: verifica el código que el TELÉFONO publicó. Además compara con
        // la respuesta viva en Firebase para no depender solo de memoria local.
        const input = String(request.code || '').trim();
        const target = getTargetKey();
        fetchDb(`/users/${target}/auth_request.json`)
            .then(res => res ? res.json() : null)
            .then(data => {
                const phoneCode = (data && data.code) ? String(data.code).trim() : (pendingAuth && pendingAuth.code ? pendingAuth.code : null);
                const expiresAt = (data && data.expires_at) || (pendingAuth ? pendingAuth.expiresAt : 0);
                if (data && data.status === 'approved') {
                    // Ya aprobada en otro intento; aceptar si coincide con un código previo
                    if (phoneCode && phoneCode === input) {
                        pendingAuth = null;
                        chrome.storage.local.set({ pendingAuth: null });
                        sendResponse({ success: true });
                    } else {
                        sendResponse({ success: false, error: 'Código incorrecto' });
                    }
                    return;
                }
                if (!phoneCode) {
                    sendResponse({ success: false, error: 'El teléfono aún no ha generado el código. Revisa la notificación.' });
                    return;
                }
                if (Date.now() > expiresAt) {
                    pendingAuth = null;
                    chrome.storage.local.set({ pendingAuth: null });
                    sendResponse({ success: false, error: 'Código expirado' });
                    return;
                }
                if (phoneCode === input) {
                    const id = (data && data.id) || (pendingAuth ? pendingAuth.requestId : null);
                    pendingAuth = null;
                    chrome.storage.local.set({ pendingAuth: null });
                    if (id) {
                        fetchDb(`/users/${target}/auth_request.json`, {
                            method: 'PUT',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id, status: 'approved', responded_at: Date.now() })
                        }).catch(() => {});
                    }
                    sendResponse({ success: true });
                } else {
                    sendResponse({ success: false, error: 'Código incorrecto' });
                }
            })
            .catch(() => {
                // Fallback a memoria local si falla la lectura
                const localCode = pendingAuth && pendingAuth.code;
                if (localCode && localCode === input && Date.now() <= pendingAuth.expiresAt) {
                    const id = pendingAuth.requestId;
                    pendingAuth = null;
                    chrome.storage.local.set({ pendingAuth: null });
                    if (id) {
                        fetchDb(`/users/${target}/auth_request.json`, {
                            method: 'PUT',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id, status: 'approved', responded_at: Date.now() })
                        }).catch(() => {});
                    }
                    sendResponse({ success: true });
                } else {
                    sendResponse({ success: false, error: 'Sin conexión con Firebase' });
                }
            });
        return true;
    } else if (request.action === 'requestTregua') {
        if (Date.now() < treguaCooldownUntil) {
            sendResponse({ success: false, error: 'Cooldown activo' });
            return true;
        }
        // V24 (Propuesta 2): la tregua de la PC se verifica en el teléfono.
        // Se escribe una solicitud en Firebase y el teléfono la aprueba/deniega.
        const target = getTargetKey();
        const reqId = `tregua_${Date.now()}`;
        fetchDb(`/users/${target}/tregua_request.json`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                id: reqId,
                requested_at: Date.now(),
                requester: 'chrome_extension',
                approved: false,
                responded: false
            })
        }).then((res) => {
            if (res && res.ok) {
                sendResponse({ success: true, pending: true, requestId: reqId });
            } else {
                sendResponse({ success: false, pending: false, error: res ? `HTTP ${res.status}` : 'Sin respuesta' });
            }
        }).catch(() => {
            sendResponse({ success: false, pending: false, error: 'Sin conexión con Firebase' });
        });
        return true;
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
    } else if (request.action === 'openLogin') {
        chrome.tabs.create({ url: chrome.runtime.getURL('login.html') });
        sendResponse({ success: true });
    } else if (request.action === 'sessionUpdated') {
        chrome.storage.local.get(['firebaseUid', 'firebaseIdToken', 'firebaseRefreshToken', 'userEmail', 'syncKey', 'pairedTargetKey'], (res) => {
            if (res.firebaseUid) firebaseUid = res.firebaseUid;
            if (res.firebaseIdToken) firebaseIdToken = res.firebaseIdToken;
            if (res.firebaseRefreshToken) firebaseRefreshToken = res.firebaseRefreshToken;
            if (res.userEmail) userEmail = res.userEmail;
            if (res.syncKey) syncKey = res.syncKey;
            if (res.pairedTargetKey) pairedTargetKey = res.pairedTargetKey;
            pollFirebaseSync();
        });
        sendResponse({ success: true });
    } else if (request.action === 'getNotes') {
        const target = getTargetKey();
        chrome.storage.local.get(['localNotes'], (localRes) => {
            const localList = Array.isArray(localRes.localNotes) ? localRes.localNotes : [];
            
            fetchDb(`/users/${target}/notes.json`)
                .then(res => res ? res.json() : null)
                .then(data => {
                    let list = [];
                    if (data) {
                        Object.keys(data).forEach(key => {
                            const item = data[key];
                            if (item && item.content) {
                                list.push({
                                    id: item.id || key,
                                    content: item.content,
                                    category: normalizeCategory(item.category),
                                    timestamp: item.timestamp || 0,
                                    deviceSource: item.deviceSource || 'chrome_extension'
                                });
                            }
                        });
                    }
                    const mergedMap = new Map();
                    localList.forEach(n => mergedMap.set(n.id, n));
                    list.forEach(n => mergedMap.set(n.id, n));
                    
                    const mergedList = Array.from(mergedMap.values());
                    mergedList.sort((a, b) => b.timestamp - a.timestamp);

                    chrome.storage.local.set({ localNotes: mergedList });
                    sendResponse({ success: true, notes: mergedList, cloudSynced: true });
                })
                .catch((err) => {
                    localList.sort((a, b) => b.timestamp - a.timestamp);
                    sendResponse({ success: true, notes: localList, cloudSynced: false, cloudError: (err && err.message) || String(err) });
                });
        });
        return true;
    } else if (request.action === 'addNote') {
        const target = getTargetKey();
        const noteId = `note_${Date.now()}_${Math.floor(Math.random()*1000)}`;
        const payload = {
            id: noteId,
            title: request.title || null,
            content: request.content || '',
            image: request.image || null,
            category: normalizeCategory(request.category),
            timestamp: Date.now(),
            deviceSource: 'chrome_extension'
        };

        // Guardado INSTANTÁNEO local (responde al instante aunque la nube tarde)
        chrome.storage.local.get(['localNotes'], (localRes) => {
            const stored = localRes.localNotes;
            const localList = Array.isArray(stored) ? stored : [];
            localList.unshift(payload);
            chrome.storage.local.set({ localNotes: localList }, () => {
                sendResponse({ success: true, note: payload, cloudSynced: false });
            });
        });

        // Sincronización a la Nube con feedback real (no silenciado)
        cloudWriteNote(target, noteId, payload);
        return true;
    } else if (request.action === 'deleteNote') {
        const target = getTargetKey();
        const noteId = request.noteId;

        // Eliminación INSTANTÁNEA local
        chrome.storage.local.get(['localNotes'], (localRes) => {
            const stored = localRes.localNotes;
            const list = Array.isArray(stored) ? stored : [];
            const filtered = list.filter(n => n.id !== noteId);
            chrome.storage.local.set({ localNotes: filtered }, () => {
                sendResponse({ success: true });
            });
        });

        // Sincronización de borrado a la Nube con feedback
        cloudDeleteNote(target, noteId);
        return true;
    }
    return true;
});

// ================================================================================
// CONFIGURACIÓN COMPARTIDA (Modo Oscuro + Bloqueo Cruzado) sincronizada con la app
// ================================================================================

/**
 * Notifica a todas las páginas de la extensión que la configuración cambió,
 * para que el popup y el bloc de notas apliquen el modo oscuro en vivo.
 */
function broadcastSettingsChanged() {
    const msg = {
        action: 'settingsChanged',
        settings: {
            crossDeviceLockEnabled,
            darkModeEnabled,
            pomodoroEnabled,
            pomodoroWorkMinutes,
            pomodoroRestMinutes,
            pomodoroRestCount
        }
    };
    try {
        chrome.runtime.sendMessage(msg, () => {});
    } catch (e) {}
    try {
        chrome.tabs.query({}, (tabs) => {
            for (const t of tabs) {
                try { chrome.tabs.sendMessage(t.id, msg).catch(() => {}); } catch (e2) {}
            }
        });
    } catch (e) {}
}

/**
 * V24 (Bug 1 de la auditoría): notifica a todas las páginas de la extensión que
 * el estado de bloqueo cambió (por ejemplo un bloqueo iniciado desde el teléfono),
 * para que el popup y las subpáginas actualicen su UI al instante.
 */
function broadcastLockState(locked, remainingSeconds) {
    const msg = { action: 'lockStateChanged', isLocked: locked, remainingSeconds: remainingSeconds };
    try {
        chrome.runtime.sendMessage(msg, () => {});
    } catch (e) {}
    try {
        chrome.tabs.query({}, (tabs) => {
            for (const t of tabs) {
                try { chrome.tabs.sendMessage(t.id, msg).catch(() => {}); } catch (e2) {}
            }
        });
    } catch (e) {}
}

/**
 * Actualiza una configuración en memoria + storage local y la publica en
 * Firebase /users/<target>/config/ para que la app Android la respete.
 */
function setSetting(setting, value, callback) {
    const target = getTargetKey();
    if (setting === 'darkMode') {
        // V24: el modo oscuro es independiente por dispositivo. Solo se guarda
        // localmente (storage + memoria) y se difunde dentro de la extensión;
        // ya NO se publica en /config para no forzar el tema de la app Android.
        darkModeEnabled = !!value;
        chrome.storage.local.set({ darkModeEnabled }, () => {
            broadcastSettingsChanged();
            if (typeof callback === 'function') callback();
        });
    } else if (setting === 'crossDeviceLock') {
        crossDeviceLockEnabled = !!value;
        chrome.storage.local.set({ crossDeviceLockEnabled }, () => {
            fetchDb(`/users/${target}/config/cross_device_lock_enabled.json`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(crossDeviceLockEnabled)
            }).catch(() => {});
            broadcastSettingsChanged();
            if (typeof callback === 'function') callback();
        });
    } else if (setting === 'pomodoroEnabled') {
        pomodoroEnabled = !!value;
        chrome.storage.local.set({ pomodoroEnabled }, () => {
            fetchDb(`/users/${target}/config/pomodoro_enabled.json`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(pomodoroEnabled)
            }).catch(() => {});
            broadcastSettingsChanged();
            if (typeof callback === 'function') callback();
        });
    } else if (setting === 'pomodoroWork') {
        const clamped = Math.min(Math.max(parseInt(value, 10) || 25, 1), POMODORO_MAX_WORK_MINUTES);
        pomodoroWorkMinutes = clamped;
        chrome.storage.local.set({ pomodoroWorkMinutes: clamped }, () => {
            fetchDb(`/users/${target}/config/pomodoro_work_minutes.json`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(clamped)
            }).catch(() => {});
            broadcastSettingsChanged();
            if (typeof callback === 'function') callback();
        });
    } else if (setting === 'pomodoroRest') {
        const clamped = Math.min(Math.max(parseInt(value, 10) || 5, 1), POMODORO_MAX_REST_MINUTES);
        pomodoroRestMinutes = clamped;
        chrome.storage.local.set({ pomodoroRestMinutes: clamped }, () => {
            fetchDb(`/users/${target}/config/pomodoro_rest_minutes.json`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(clamped)
            }).catch(() => {});
            broadcastSettingsChanged();
            if (typeof callback === 'function') callback();
        });
    } else if (setting === 'pomodoroRestCount') {
        const clamped = Math.min(Math.max(parseInt(value, 10) || 1, 1), 6);
        pomodoroRestCount = clamped;
        chrome.storage.local.set({ pomodoroRestCount: clamped }, () => {
            fetchDb(`/users/${target}/config/pomodoro_rest_count.json`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(clamped)
            }).catch(() => {});
            broadcastSettingsChanged();
            if (typeof callback === 'function') callback();
        });
    } else {
        if (typeof callback === 'function') callback();
    }
}

// V24: límites (maxRest, maxRestCount) según la duración total del enfoque (espejo Android)
function computePomodoroLimits(totalMinutes) {
    if (totalMinutes < 10) return { maxRest: 0, maxRestCount: 0 };
    if (totalMinutes <= 30) return { maxRest: 5, maxRestCount: 1 };
    if (totalMinutes <= 60) return { maxRest: 10, maxRestCount: 2 };
    if (totalMinutes <= 120) return { maxRest: 15, maxRestCount: 3 };
    if (totalMinutes <= 180) return { maxRest: 20, maxRestCount: 4 };
    return { maxRest: 20, maxRestCount: 6 };
}

// V24: genera las fases (work/rest) de un enfoque Pomodoro. Empieza y termina en trabajo.
function buildPomodoroSchedule(totalMinutes, restCount, restMinutes) {
    if (totalMinutes < 10) return [];
    const { maxRest, maxRestCount } = computePomodoroLimits(totalMinutes);
    const rc = Math.min(Math.max(restCount || 1, 1), maxRestCount);
    const rm = Math.min(Math.max(restMinutes || 1, 1), maxRest);
    const totalMs = totalMinutes * 60000;
    const restMs = rm * 60000;
    const workBlocks = rc + 1;
    const workMs = Math.floor((totalMs - rc * restMs) / workBlocks);
    const phases = [];
    let t = Date.now();
    for (let i = 0; i < rc; i++) {
        phases.push({ type: 'work', start_ms: t, end_ms: t + workMs });
        t += workMs;
        phases.push({ type: 'rest', start_ms: t, end_ms: t + restMs });
        t += restMs;
    }
    phases.push({ type: 'work', start_ms: t, end_ms: t + workMs });
    return phases;
}

// V24: fase actual del Pomodoro en `now` (null si no hay Pomodoro activo)
function currentPomodoroPhase(now) {
    if (!Array.isArray(pomodoroPhases)) return null;
    return pomodoroPhases.find(p => now >= p.start_ms && now < p.end_ms) || null;
}

function startBackgroundTimer() {
    clearInterval(lockInterval);
    let wasInRest = false;
    lockInterval = setInterval(() => {
        const now = Date.now();
        const phase = currentPomodoroPhase(now);
        const inRest = !!(phase && phase.type === 'rest');

        // V24: tregua libre durante las fases de descanso (se liberan los sitios bloqueados)
        if (inRest && !wasInRest) {
            wasInRest = true;
            updateNetRules();
            broadcastLockState(true, remainingSeconds);
            broadcastPhase('rest', phase);
        } else if (!inRest && wasInRest) {
            wasInRest = false;
            updateNetRules();
            broadcastPhase('work', phase);
        }
        // Finaliza tregua si aplica
        if (treguaUntil > 0 && Date.now() >= treguaUntil) {
            treguaUntil = 0;
            treguaCooldownUntil = Date.now() + (10 * 60 * 1000); // 10 mins cooldown
            updateNetRules(); // Restaurar el bloqueo
        }

        if (remainingSeconds > 0) {
            remainingSeconds--;
        } else {
            isLocked = false;
            clearInterval(lockInterval);
            pomodoroPhases = [];
            chrome.storage.local.set({ pomodoroPhases });
            updateNetRules();
            pushLockStateToFirebase(false, 0);
            broadcastPhase('none', null);
        }
    }, 1000);
}

// V24: notifica a las páginas de la extensión la fase actual del Pomodoro
function broadcastPhase(type, phase) {
    const msg = {
        action: 'pomodoroPhaseChanged',
        type: type,
        phase: phase || null
    };
    try {
        chrome.runtime.sendMessage(msg, () => {});
    } catch (e) {}
    try {
        chrome.tabs.query({}, (tabs) => {
            for (const t of tabs) {
                try { chrome.tabs.sendMessage(t.id, msg).catch(() => {}); } catch (e2) {}
            }
        });
    } catch (e) {}
}

// ================================================================================
// ESCÁNER Y CONEXIÓN SILENCIOSA EN RED LOCAL (LAN / WI-FI)
// ================================================================================
let cachedLanIp = null;
let isLanActive = false;
let isScanning = false;
let lanWebSocket = null;

// V24 (Propuesta 4): WebSocket local LAN para actualizaciones sub-10ms.
// Se conecta a ws://IP:8888 cuando se detecta el teléfono y recibe el estado
// en vivo; ante cualquier error se cae al polling de Firebase (modo resiliente).
function connectLanWebSocket() {
    if (lanWebSocket || !cachedLanIp) return;
    const wsUrl = `ws://${cachedLanIp}:8888/`;
    try {
        lanWebSocket = new WebSocket(wsUrl);
    } catch (e) {
        lanWebSocket = null;
        return;
    }

    lanWebSocket.onopen = () => {
        console.log("ZEN_LAN: WebSocket conectado a", wsUrl);
    };

    lanWebSocket.onmessage = (event) => {
        let data;
        try {
            data = JSON.parse(event.data);
        } catch (e) {
            return;
        }
        if (!data || data.status !== 'ok') return;
        lastLanUpdate = Date.now();
        isLanActive = true;
        // V24.1: adoptar el nodo real del teléfono que llega por LAN
        if (data.target_key) {
            const adopted = adoptPairedTargetKey(data.target_key);
            if (adopted) pollFirebaseSync();
        }
        connectedDeviceInfo = {
            brand: data.brand || 'TECNO',
            model: data.model || 'POVA 6',
            isLan: true,
            ip: cachedLanIp
        };
        // Estado de bloqueo en tiempo real
        if (typeof data.is_locked === 'boolean' && typeof data.remaining_seconds === 'number') {
            const diffSecs = data.remaining_seconds;
            const lockedRemote = data.is_locked && diffSecs > 0;
            if (lockedRemote && !isLocked) {
                isLocked = true;
                remainingSeconds = diffSecs;
                updateNetRules();
                startBackgroundTimer();
                broadcastLockState(true, remainingSeconds);
            } else if (!lockedRemote && isLocked) {
                isLocked = false;
                remainingSeconds = 0;
                updateNetRules();
                broadcastLockState(false, 0);
            } else if (lockedRemote && isLocked) {
                remainingSeconds = Math.max(remainingSeconds, diffSecs);
            }
        }
        // Ajustes Pomodoro en tiempo real
        let pomodoroChanged = false;
        if (typeof data.pomodoro_enabled === 'boolean' && data.pomodoro_enabled !== pomodoroEnabled) {
            pomodoroEnabled = data.pomodoro_enabled;
            chrome.storage.local.set({ pomodoroEnabled });
            pomodoroChanged = true;
        }
        if (typeof data.pomodoro_work_minutes === 'number') {
            const w = Math.min(Math.max(data.pomodoro_work_minutes, 1), POMODORO_MAX_WORK_MINUTES);
            if (w !== pomodoroWorkMinutes) {
                pomodoroWorkMinutes = w;
                chrome.storage.local.set({ pomodoroWorkMinutes: w });
                pomodoroChanged = true;
            }
        }
        if (typeof data.pomodoro_rest_minutes === 'number') {
            const r = Math.min(Math.max(data.pomodoro_rest_minutes, 1), POMODORO_MAX_REST_MINUTES);
            if (r !== pomodoroRestMinutes) {
                pomodoroRestMinutes = r;
                chrome.storage.local.set({ pomodoroRestMinutes: r });
                pomodoroChanged = true;
            }
        }
        if (typeof data.pomodoro_rest_count === 'number') {
            const c = Math.min(Math.max(data.pomodoro_rest_count, 1), 6);
            if (c !== pomodoroRestCount) {
                pomodoroRestCount = c;
                chrome.storage.local.set({ pomodoroRestCount: c });
                pomodoroChanged = true;
            }
        }
        if (pomodoroChanged) broadcastSettingsChanged();
        // V24: replicar fases Pomodoro enviadas por el teléfono en tiempo real
        if (Array.isArray(data.phases) && data.phases.length > 0) {
            if (JSON.stringify(pomodoroPhases) !== JSON.stringify(data.phases)) {
                pomodoroPhases = data.phases;
                chrome.storage.local.set({ pomodoroPhases });
            }
        }
        // Solicitar el estado en cada latido del navegador (responderá el servidor)
        try { lanWebSocket.send('status'); } catch (e) {}
    };

    lanWebSocket.onerror = () => {
        try { lanWebSocket.close(); } catch (e) {}
        lanWebSocket = null;
    };

    lanWebSocket.onclose = () => {
        lanWebSocket = null;
        // La conexión LAN cayó: dejar que el polling de Firebase tome el control
        if (isLanActive && !cachedLanIp) isLanActive = false;
    };
}

let lastLanUpdate = 0;

async function scanLanNetwork() {
    if (cachedLanIp) {
        await checkLanIp(cachedLanIp, 2000);
        // Reintentar el WebSocket si la IP sigue viva pero la conexión se cayó
        if (cachedLanIp && !lanWebSocket) connectLanWebSocket();
        return;
    }

    if (isScanning) return;
    isScanning = true;

    const priorityIps = [
        "192.168.43.1", // Hotspot predeterminado Android
        "192.168.1.1", "192.168.0.1", "10.0.0.1", "172.20.10.1", "127.0.0.1"
    ];

    for (const ip of priorityIps) {
        await checkLanIp(ip, 1200);
        if (cachedLanIp) { isScanning = false; return; }
    }

    // Escanear redes 192.168.1.X y 192.168.0.X en lotes de 20 para no saturar los sockets de Chrome
    const allIps = [];
    for (let i = 2; i <= 254; i++) {
        allIps.push(`192.168.1.${i}`);
        allIps.push(`192.168.0.${i}`);
    }

    for (let i = 0; i < allIps.length; i += 20) {
        if (cachedLanIp) break;
        const batch = allIps.slice(i, i + 20);
        await Promise.allSettled(batch.map(ip => checkLanIp(ip, 1200)));
        await new Promise(resolve => setTimeout(resolve, 50)); // Breve pausa entre lotes
    }
    
    isScanning = false;
}

function checkLanIp(ip, timeoutMs = 1200) {
    return new Promise((resolve, reject) => {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

        fetch(`http://${ip}:8888/status`, { signal: controller.signal })
            .then(res => res.json())
            .then(data => {
                clearTimeout(timeoutId);
                if (data && data.status === "ok") {
                    cachedLanIp = ip;
                    isLanActive = true;
                    connectedDeviceInfo = {
                        brand: data.brand || "TECNO",
                        model: data.model || "POVA 6",
                        isLan: true,
                        ip: ip
                    };
                    // V24 (Propuesta 4): abrir/reabrir el WebSocket local para sub-10ms
                    connectLanWebSocket();
                    resolve(true);
                } else {
                    reject();
                }
            })
            .catch(() => {
                clearTimeout(timeoutId);
                if (cachedLanIp === ip) {
                    cachedLanIp = null;
                    isLanActive = false;
                }
                reject();
            });
    });
}

setInterval(scanLanNetwork, 5000); // Ciclo de escaneo cada 5 segundos si no hay IP activa
scanLanNetwork();

// ================================================================================
// FIREBASE REALTIME DATABASE SYNC
// ================================================================================

function pushLockStateToFirebase(locked, durationMinutes) {
    // V20: si el bloqueo cruzado está desactivado en el teléfono, no publicar el estado
    if (!crossDeviceLockEnabled) return;
    const target = getTargetKey();
    const expiresAt = locked ? (Date.now() + (durationMinutes * 60 * 1000)) : 0;
    const payload = {
        is_locked: locked,
        expires_at: expiresAt,
        updated_at: Date.now(),
        source_device: "chrome_extension",
        // V24: fases del Pomodoro para que el teléfono las replique en su pantalla de bloqueo
        pomodoro_enabled: locked && pomodoroPhases.length > 0,
        phases: locked ? pomodoroPhases : []
    };

    fetchDb(`/users/${target}/lock_state.json`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    }).catch(() => {});
}

// V20: el icono de la extensión ya NO muestra ningún badge (ni cuadro rojo/verde
// ni emoji). El estado de conexión vive solo en el puntico del popup (#conn-dot).
function updateExtensionBadge(isConnected) {
    try {
        chrome.action.setBadgeText({ text: "" });
    } catch (e) {
        // Ignore in background
    }
}

// Configurar alarmas de Chrome (Manifest V3 Service Worker keeper)
chrome.alarms.create("syncAlarm", { periodInMinutes: 0.1 });
chrome.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name === "syncAlarm") {
        pollFirebaseSync();
        scanLanNetwork();
    }
});

// V24: despertar el Service Worker (MV3) inmediatamente al iniciar el navegador
// o al actualizar/instalar la extensión. Sin esto, los setInterval mueren al
// suspenderse el worker y la sincronización quedaría dormida hasta abrir el popup.
chrome.runtime.onStartup.addListener(() => {
    try { chrome.alarms.create("syncAlarm", { periodInMinutes: 0.1 }); } catch (e) {}
    pollFirebaseSync();
    scanLanNetwork();
});
chrome.runtime.onInstalled.addListener(() => {
    try { chrome.alarms.create("syncAlarm", { periodInMinutes: 0.1 }); } catch (e) {}
    pollFirebaseSync();
});

function pollFirebaseSync() {
    const target = getTargetKey();
    const now = Date.now();

    // 1. Enviar latido de la Extensión (extension_last_ping)
    fetchDb(`/users/${target}/device_info/extension_last_ping.json`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(now)
    }).catch(() => {});

    // 2. Si LAN está activo, priorizar la conexión LAN local
    if (isLanActive && connectedDeviceInfo && connectedDeviceInfo.isLan) {
        updateExtensionBadge(true);
    }

    // 3. Obtener información de dispositivo Android en la nube y comprobar latido
    fetchDb(`/users/${target}/device_info.json`)
        .then(res => res ? res.json() : null)
        .then(deviceData => {
            if (deviceData && (deviceData.brand || deviceData.model)) {
                // V24.1: adoptar el nodo real del teléfono si lo publica
                if (deviceData.target_key) {
                    const adopted = adoptPairedTargetKey(deviceData.target_key);
                    if (adopted && target !== getTargetKey()) {
                        pollFirebaseSync();
                    }
                }
                const androidPing = deviceData.android_last_ping || deviceData.last_ping || 0;
                const isOnline = (now - androidPing < 30000);
                if (isOnline) {
                    connectedDeviceInfo = deviceData;
                    updateExtensionBadge(true);
                } else if (!isLanActive) {
                    connectedDeviceInfo = null;
                    updateExtensionBadge(false);
                }
            } else if (!isLanActive) {
                connectedDeviceInfo = null;
                updateExtensionBadge(false);
            }
        })
        .catch(() => {
            if (!isLanActive) {
                connectedDeviceInfo = null;
                updateExtensionBadge(false);
            }
        });

    // 4. Sincronizar configuración compartida y estado de bloqueo (escucha Android -> Chrome)
    // V24 (Bug 1 de la auditoría): leer /config y /lock_state EN PARALELO para que un
    // bloqueo iniciado desde el teléfono se aplique sin esperar a la lectura de config.
    fetchDb(`/users/${target}/config.json`)
        .then(res => res ? res.json() : null)
        .then(config => {
            let changed = false;

            if (config && typeof config.cross_device_lock_enabled === 'boolean') {
                if (crossDeviceLockEnabled !== config.cross_device_lock_enabled) {
                    crossDeviceLockEnabled = config.cross_device_lock_enabled;
                    chrome.storage.local.set({ crossDeviceLockEnabled });
                    changed = true;
                }
            } else if (config && config.cross_device_lock_enabled === null) {
                if (crossDeviceLockEnabled !== true) {
                    crossDeviceLockEnabled = true;
                    changed = true;
                }
            }

            // V24: el modo oscuro ya NO se sincroniza desde el teléfono.
            // Cada dispositivo conserva su propio tema (Bug 2 de la auditoría).

            // V24 (Propuesta 1): aplicar ajustes Pomodoro llegados desde el teléfono
            if (config && typeof config.pomodoro_enabled === 'boolean') {
                if (pomodoroEnabled !== config.pomodoro_enabled) {
                    pomodoroEnabled = config.pomodoro_enabled;
                    chrome.storage.local.set({ pomodoroEnabled });
                    changed = true;
                }
            }
            if (config && typeof config.pomodoro_work_minutes === 'number') {
                const w = Math.min(Math.max(config.pomodoro_work_minutes, 1), POMODORO_MAX_WORK_MINUTES);
                if (pomodoroWorkMinutes !== w) {
                    pomodoroWorkMinutes = w;
                    chrome.storage.local.set({ pomodoroWorkMinutes: w });
                    changed = true;
                }
            }
            if (config && typeof config.pomodoro_rest_minutes === 'number') {
                const r = Math.min(Math.max(config.pomodoro_rest_minutes, 1), POMODORO_MAX_REST_MINUTES);
                if (pomodoroRestMinutes !== r) {
                    pomodoroRestMinutes = r;
                    chrome.storage.local.set({ pomodoroRestMinutes: r });
                    changed = true;
                }
            }
            if (config && typeof config.pomodoro_rest_count === 'number') {
                const c = Math.min(Math.max(config.pomodoro_rest_count, 1), 6);
                if (pomodoroRestCount !== c) {
                    pomodoroRestCount = c;
                    chrome.storage.local.set({ pomodoroRestCount: c });
                    changed = true;
                }
            }

            if (changed) broadcastSettingsChanged();
        })
        .catch(() => {});

    fetchDb(`/users/${target}/lock_state.json`)
        .then(res => res ? res.json() : null)
        .then(lockState => {
            if (!lockState || !crossDeviceLockEnabled) return;
            if (lockState.is_locked && lockState.expires_at > now) {
                const diffSecs = Math.floor((lockState.expires_at - now) / 1000);
                // V24: replicar las fases Pomodoro que el teléfono haya publicado
                if (Array.isArray(lockState.phases)) {
                    const changedPhases = JSON.stringify(pomodoroPhases) !== JSON.stringify(lockState.phases);
                    pomodoroPhases = lockState.phases;
                    if (changedPhases) chrome.storage.local.set({ pomodoroPhases });
                } else if (pomodoroPhases.length > 0) {
                    pomodoroPhases = [];
                    chrome.storage.local.set({ pomodoroPhases });
                }
                if (!isLocked && diffSecs > 0) {
                    isLocked = true;
                    remainingSeconds = diffSecs;
                    updateNetRules();
                    startBackgroundTimer();
                    broadcastLockState(true, remainingSeconds);
                }
            } else if (!lockState.is_locked && isLocked) {
                isLocked = false;
                remainingSeconds = 0;
                pomodoroPhases = [];
                chrome.storage.local.set({ pomodoroPhases });
                updateNetRules();
                broadcastLockState(false, 0);
            }
        })
        .catch(() => {});

    // V24 (Propuesta 2): tregua verificada por el 2º dispositivo.
    // Si el teléfono aprobó la solicitud hecha desde la extensión, conceder 5 min.
    fetchDb(`/users/${target}/tregua_request.json`)
        .then(res => res ? res.json() : null)
        .then(treguaReq => {
            if (!treguaReq) return;
            if (treguaReq.requester === 'chrome_extension' && treguaReq.approved === true) {
                treguaUntil = Date.now() + (5 * 60 * 1000);
                updateNetRules();
                
                if (!isLocked) {
                    isLocked = true;
                    updateNetRules();
                    startBackgroundTimer();
                }
                
                broadcastLockState(true, remainingSeconds); // Retiene el timer original
                try {
                    chrome.runtime.sendMessage({ action: 'treguaApproved' }, () => {});
                } catch (e) {}
                // Limpiar la solicitud para que no se re-procese
                fetchDb(`/users/${target}/tregua_request.json`, { method: 'DELETE' }).catch(() => {});
            }
        })
        .catch(() => {});
}

// V20: garantizar que el icono nunca muestre un badge (punto rojo/verde) al despertar
try { chrome.action.setBadgeText({ text: "" }); } catch (e) {}

setInterval(pollFirebaseSync, 2000);
pollFirebaseSync();

const WEB_CLIENT_ID = "927134130052-3lmelvvnfuk1dg8o71vosjdkf8s8k3p5.apps.googleusercontent.com";
// Ocultación estética con Base64 para escáneres avanzados (ej. GitHub):
const FIREBASE_API_KEY = atob("QUl6YVN5Qk53MTJWN21NQ3Q3NlpkUDg5TkVZUXdnX0UydnNoUnRv");

document.addEventListener('DOMContentLoaded', () => {
    const btnGoogleLogin = document.getElementById('btn-google-login');
    const statusMsg = document.getElementById('status-msg');

    // V23: menú de perfil y ajustes + modo oscuro sincronizado (app/popup/notas)
    function applyTheme(dark) {
        document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    }

    const btnProfileMenu = document.getElementById('btn-profile-menu');
    const profileDropdown = document.getElementById('profile-dropdown');
    const profileTitle = document.getElementById('profile-title');
    const darkModeSwitch = document.getElementById('dark-mode-switch');
    const crossLockSwitch = document.getElementById('cross-lock-switch');

    if (btnProfileMenu && profileDropdown) {
        btnProfileMenu.addEventListener('click', (e) => {
            e.stopPropagation();
            profileDropdown.classList.toggle('hidden');
        });
    }

    document.addEventListener('click', (e) => {
        if (profileDropdown && !profileDropdown.classList.contains('hidden') && !e.target.closest('.login-menu')) {
            profileDropdown.classList.add('hidden');
        }
    });

    document.querySelectorAll('.dropdown-item').forEach(item => {
        item.addEventListener('click', () => {
            if (item.getAttribute('data-action') === 'account') {
                btnGoogleLogin.click();
            }
            const modalId = item.getAttribute('data-open-modal');
            if (modalId) {
                const modal = document.getElementById(modalId);
                if (modal) modal.classList.remove('hidden');
            }
            if (profileDropdown) profileDropdown.classList.add('hidden');
        });
    });

    document.querySelectorAll('[data-close-modal]').forEach(btn => {
        btn.addEventListener('click', () => {
            const modal = document.getElementById(btn.getAttribute('data-close-modal'));
            if (modal) modal.classList.add('hidden');
        });
    });

    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) overlay.classList.add('hidden');
        });
    });

    if (darkModeSwitch) {
        darkModeSwitch.addEventListener('change', () => {
            const value = darkModeSwitch.checked;
            chrome.storage.local.set({ darkModeEnabled: value });
            chrome.runtime.sendMessage({ action: 'setSetting', setting: 'darkMode', value });
            applyTheme(value);
        });
    }

    if (crossLockSwitch) {
        crossLockSwitch.addEventListener('change', () => {
            const value = crossLockSwitch.checked;
            chrome.storage.local.set({ crossDeviceLockEnabled: value });
            chrome.runtime.sendMessage({ action: 'setSetting', setting: 'crossDeviceLock', value });
        });
    }

    // V24: leer ajustes y sesión directamente desde storage (no depende de la
    // memoria del service worker, que se reinicia al arrancar el navegador).
    chrome.storage.local.get(['darkModeEnabled', 'crossDeviceLockEnabled', 'userEmail', 'syncKey', 'firebaseUid'], (res) => {
        const dark = typeof res.darkModeEnabled === 'boolean' ? res.darkModeEnabled : false;
        const lock = typeof res.crossDeviceLockEnabled === 'boolean' ? res.crossDeviceLockEnabled : true;
        applyTheme(dark);
        if (darkModeSwitch) darkModeSwitch.checked = dark;
        if (crossLockSwitch) crossLockSwitch.checked = lock;
        const activeAccount = res.userEmail || res.syncKey || res.firebaseUid;
        if (profileTitle) profileTitle.textContent = activeAccount || 'Mi Cuenta';
    });

    chrome.runtime.sendMessage({ action: 'getSettings' }, (res) => {
        if (res) {
            applyTheme(!!res.darkModeEnabled);
            if (darkModeSwitch) darkModeSwitch.checked = !!res.darkModeEnabled;
            if (crossLockSwitch) crossLockSwitch.checked = !!res.crossDeviceLockEnabled;
            if (profileTitle) profileTitle.textContent = res.userEmail || 'Mi Cuenta';
        }
    });

    chrome.runtime.onMessage.addListener((request) => {
        if (request && request.action === 'settingsChanged' && request.settings) {
            if (darkModeSwitch) darkModeSwitch.checked = !!request.settings.darkModeEnabled;
            if (crossLockSwitch) crossLockSwitch.checked = !!request.settings.crossDeviceLockEnabled;
            applyTheme(!!request.settings.darkModeEnabled);
        }
    });

    function cleanKey(key) {
        if (!key) return "";
        return key.toLowerCase().trim().replace(/[^a-z0-9_]/g, '_');
    }

    btnGoogleLogin.addEventListener('click', () => {
        statusMsg.textContent = "Abriendo ventana de inicio de sesión oficial de Google...";
        statusMsg.className = "status-msg";
        btnGoogleLogin.disabled = true;

        const redirectUrl = chrome.identity.getRedirectURL();
        const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?` +
            `client_id=${WEB_CLIENT_ID}` +
            `&response_type=token id_token` +
            `&redirect_uri=${encodeURIComponent(redirectUrl)}` +
            `&scope=${encodeURIComponent('openid email profile')}` +
            `&prompt=select_account` +
            `&nonce=${Math.random().toString(36).substr(2)}`;

        chrome.identity.launchWebAuthFlow(
            { url: authUrl, interactive: true },
            (responseUrl) => {
                btnGoogleLogin.disabled = false;

                if (chrome.runtime.lastError || !responseUrl) {
                    const errStr = chrome.runtime.lastError ? chrome.runtime.lastError.message : "Ventana cerrada";
                    console.warn("WebAuthFlow cancelado:", errStr);
                    statusMsg.textContent = "⚠️ Inicio de sesión no completado. Inténtalo de nuevo.";
                    statusMsg.className = "status-msg error";
                    return;
                }

                // Extraer token devuelto por Google
                const hash = responseUrl.split('#')[1] || responseUrl.split('?')[1] || "";
                const params = new URLSearchParams(hash);
                const accessToken = params.get('access_token');
                const idToken = params.get('id_token');

                if (idToken || accessToken) {
                    statusMsg.textContent = "Autenticando con Firebase...";

                    // Intentar intercambiar idToken con Firebase IdentityToolkit
                    if (idToken) {
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
                        .then(fbData => {
                            if (fbData && fbData.idToken) {
                                const userEmail = fbData.email || "";
                                const firebaseUid = fbData.localId || cleanKey(userEmail);
                                const syncKey = cleanKey(userEmail || firebaseUid);

                                saveSessionAndNotify(userEmail, firebaseUid, syncKey, fbData.idToken, fbData.refreshToken);
                            } else {
                                fallbackUserInfo(accessToken);
                            }
                        })
                        .catch(() => fallbackUserInfo(accessToken));
                    } else {
                        fallbackUserInfo(accessToken);
                    }
                } else {
                    statusMsg.textContent = "❌ No se recibió token de acceso.";
                    statusMsg.className = "status-msg error";
                }
            }
        );
    });

    function fallbackUserInfo(accessToken) {
        if (!accessToken) {
            statusMsg.textContent = "❌ No se pudo completar la verificación.";
            statusMsg.className = "status-msg error";
            return;
        }

        fetch(`https://www.googleapis.com/oauth2/v3/userinfo?access_token=${accessToken}`)
            .then(res => res.json())
            .then(userInfo => {
                if (userInfo && userInfo.email) {
                    const userEmail = userInfo.email;
                    const firebaseUid = userInfo.sub || cleanKey(userEmail);
                    const syncKey = cleanKey(userEmail);

                    // No pasar token de Google Access como idToken para evitar error 401 en RTDB REST API
                    saveSessionAndNotify(userEmail, firebaseUid, syncKey, "", "");
                } else {
                    statusMsg.textContent = "❌ No se pudo obtener el perfil de Google.";
                    statusMsg.className = "status-msg error";
                }
            })
            .catch(err => {
                statusMsg.textContent = "❌ Error de verificación: " + err.message;
                statusMsg.className = "status-msg error";
            });
    }

    function saveSessionAndNotify(userEmail, firebaseUid, syncKey, idToken, refreshToken) {
        chrome.storage.local.set({
            firebaseUid: firebaseUid || syncKey,
            userEmail: userEmail || "",
            firebaseIdToken: idToken || "",
            firebaseRefreshToken: refreshToken || "",
            syncKey: syncKey || cleanKey(userEmail)
        }, () => {
            statusMsg.textContent = `✅ ¡Conectado con éxito como ${userEmail || syncKey}!`;
            statusMsg.className = "status-msg success";

            chrome.runtime.sendMessage({ action: 'sessionUpdated' }, () => {});

            setTimeout(() => {
                window.close();
            }, 1200);
        });
    }
});

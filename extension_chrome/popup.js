document.addEventListener('DOMContentLoaded', () => {
    let targetHours = 0;
    let targetMinutes = 10;
    let longPhrase = "Al tomar la firme decisión de dar por concluida mi actividad antes de tiempo, reconozco plenamente que la verdadera autodisciplina no consiste en buscar atajos hacia la comodidad, sino en honrar cada promesa que me hago a mí mismo.";
    let expectedMath = 0;
    let phraseStartTime = 0;

    const timerText = document.getElementById('timer-text');
    const statusPill = document.getElementById('status-pill');
    const setupControls = document.getElementById('setup-controls');
    const lockedControls = document.getElementById('locked-controls');

    const btnHoursPlus = document.getElementById('btn-hours-plus');
    const btnHoursMinus = document.getElementById('btn-hours-minus');
    const btnMinsPlus = document.getElementById('btn-mins-plus');
    const btnMinsMinus = document.getElementById('btn-mins-minus');
    const hoursText = document.getElementById('hours-text');
    const minsText = document.getElementById('mins-text');

    const btnStart = document.getElementById('btn-start');
    const btnFinishEarly = document.getElementById('btn-finish-early');
    const btnTregua = document.getElementById('btn-tregua');

    const siteTagsContainer = document.getElementById('site-tags-container');
    const newSiteInput = document.getElementById('new-site-input');
    const btnAddSite = document.getElementById('btn-add-site');
    const parentalSwitch = document.getElementById('parental-switch');

    const phraseModal = document.getElementById('phrase-modal');
    const phraseTargetText = document.getElementById('phrase-target-text');
    const phraseInput = document.getElementById('phrase-input');
    const btnClosePhrase = document.getElementById('btn-close-phrase');
    const btnVerifyPhrase = document.getElementById('btn-verify-phrase');

    const mathModal = document.getElementById('math-modal');
    const mathProblemText = document.getElementById('math-problem-text');
    const mathInput = document.getElementById('math-input');
    const btnCloseMath = document.getElementById('btn-close-math');
    const btnVerifyMath = document.getElementById('btn-verify-math');

    // Desactivar pegar por código JS y eventos
    if (phraseInput) {
        phraseInput.addEventListener('paste', (e) => e.preventDefault());
        phraseInput.addEventListener('copy', (e) => e.preventDefault());
        phraseInput.addEventListener('contextmenu', (e) => e.preventDefault());
    }

    function formatTime(totalSecs) {
        const hrs = Math.floor(totalSecs / 3600);
        const mins = Math.floor((totalSecs % 3600) / 60);
        const secs = totalSecs % 60;
        if (hrs > 0) {
            return `${hrs < 10 ? '0' + hrs : hrs}:${mins < 10 ? '0' + mins : mins}:${secs < 10 ? '0' + secs : secs}`;
        }
        return `${mins < 10 ? '0' + mins : mins}:${secs < 10 ? '0' + secs : secs}`;
    }

    function updateTimeDisplay() {
        if (hoursText) hoursText.textContent = targetHours < 10 ? '0' + targetHours : targetHours;
        if (minsText) minsText.textContent = targetMinutes < 10 ? '0' + targetMinutes : targetMinutes;

        const totalSecs = (targetHours * 3600) + (targetMinutes * 60);
        if (timerText) timerText.textContent = formatTime(totalSecs);
    }

    if (btnHoursPlus) {
        btnHoursPlus.addEventListener('click', () => {
            if (targetHours < 23) targetHours++;
            updateTimeDisplay();
        });
    }
    if (btnHoursMinus) {
        btnHoursMinus.addEventListener('click', () => {
            if (targetHours > 0) targetHours--;
            updateTimeDisplay();
        });
    }
    if (btnMinsPlus) {
        btnMinsPlus.addEventListener('click', () => {
            if (targetMinutes < 55) {
                targetMinutes += 5;
            } else if (targetHours < 23) {
                targetMinutes = 0;
                targetHours++;
            }
            updateTimeDisplay();
        });
    }
    if (btnMinsMinus) {
        btnMinsMinus.addEventListener('click', () => {
            if (targetMinutes >= 5) {
                targetMinutes -= 5;
            } else if (targetHours > 0) {
                targetMinutes = 55;
                targetHours--;
            }
            updateTimeDisplay();
        });
    }

    function renderSiteTags(customDomains) {
        if (!siteTagsContainer) return;
        siteTagsContainer.innerHTML = '';
        customDomains.forEach(domain => {
            const tag = document.createElement('span');
            tag.className = 'tag';
            tag.innerHTML = `${domain} <span class="del-tag" data-domain="${domain}">×</span>`;
            siteTagsContainer.appendChild(tag);
        });

        document.querySelectorAll('.del-tag').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const dom = e.target.getAttribute('data-domain');
                chrome.runtime.sendMessage({ action: 'removeDomain', domain: dom }, () => updateUI());
            });
        });
    }

    const syncStatusBadge = document.getElementById('sync-status-badge');

    function updateUI() {
        chrome.runtime.sendMessage({ action: 'getState' }, (response) => {
            if (!response) return;
            const { isLocked, remainingSeconds, customDomains, parentalEnabled, connectedDeviceInfo, firebaseUid, userEmail } = response;

            if (parentalSwitch) parentalSwitch.checked = !!parentalEnabled;
            if (customDomains) renderSiteTags(customDomains);

            // Actualizar Badge de Detección Real de Dispositivo
            if (syncStatusBadge) {
                if (connectedDeviceInfo && connectedDeviceInfo.brand) {
                    const devName = `${connectedDeviceInfo.brand} ${connectedDeviceInfo.model || ''}`.trim();
                    syncStatusBadge.textContent = `🟢 Conectado: ${devName}`;
                    syncStatusBadge.classList.add("connected");
                    syncStatusBadge.classList.remove("disconnected");
                } else {
                    syncStatusBadge.textContent = "🔴 Desvinculado";
                    syncStatusBadge.classList.remove("connected");
                    syncStatusBadge.classList.add("disconnected");
                }
            }

            // Actualizar botón de Google con email o clave activa
            if (btnGoogleLogin) {
                const activeAccount = userEmail || syncKey;
                if (activeAccount) {
                    btnGoogleLogin.innerHTML = `<span class="google-icon">G</span> ${activeAccount}`;
                    btnGoogleLogin.style.backgroundColor = '#34A853';
                } else {
                    btnGoogleLogin.innerHTML = `<span class="google-icon">G</span> Iniciar Sesión con Google`;
                    btnGoogleLogin.style.backgroundColor = '#4285F4';
                }
            }

            if (isLocked) {
                statusPill.textContent = "MODO ENFOQUE ACTIVO";
                statusPill.classList.add("status-locked");
                setupControls.classList.add("hidden");
                btnStart.classList.add("hidden");
                lockedControls.classList.remove("hidden");
                timerText.textContent = formatTime(remainingSeconds);
            } else {
                statusPill.textContent = "MODO LIBRE";
                statusPill.classList.remove("status-locked");
                setupControls.classList.remove("hidden");
                btnStart.classList.remove("hidden");
                lockedControls.classList.add("hidden");
                updateTimeDisplay();
            }
        });
    }

    const btnGoogleLogin = document.getElementById('btn-google-login');

    if (btnGoogleLogin) {
        btnGoogleLogin.addEventListener('click', () => {
            btnGoogleLogin.disabled = true;
            btnGoogleLogin.innerHTML = `<span class="google-icon">G</span> Conectando...`;
            
            chrome.runtime.sendMessage({ action: 'googleSignIn' }, (result) => {
                btnGoogleLogin.disabled = false;
                if (result && result.success && result.email) {
                    btnGoogleLogin.innerHTML = `<span class="google-icon">G</span> ${result.email}`;
                    btnGoogleLogin.style.backgroundColor = '#34A853';
                    updateUI();
                } else {
                    // Fallback directo a Prompt de correo de Google o Código PIN (ej. ZEN-1234)
                    const inputKey = prompt(
                        "🔒 VINCULACIÓN CON TELÉFONO:\n\nIngresa tu correo de Google o el Código PIN de tu teléfono (ej. ZEN-1234):",
                        "mi_cuenta_google@gmail.com"
                    );
                    if (inputKey && inputKey.trim()) {
                        chrome.runtime.sendMessage({ action: 'setManualKey', key: inputKey.trim() }, () => {
                            updateUI();
                        });
                    } else {
                        updateUI();
                    }
                }
            });
        });
    }

    btnAddSite.addEventListener('click', () => {
        const val = newSiteInput.value.trim();
        if (val) {
            chrome.runtime.sendMessage({ action: 'addDomain', domain: val }, () => {
                newSiteInput.value = '';
                updateUI();
            });
        }
    });

    if (parentalSwitch) {
        parentalSwitch.addEventListener('change', () => {
            chrome.runtime.sendMessage({ action: 'toggleParental', enabled: parentalSwitch.checked }, () => updateUI());
        });
    }

    btnStart.addEventListener('click', () => {
        const totalMinutes = (targetHours * 60) + targetMinutes;
        if (totalMinutes <= 0) return;
        chrome.runtime.sendMessage({ action: 'startTimer', minutes: totalMinutes }, () => {
            updateUI();
        });
    });

    btnFinishEarly.addEventListener('click', () => {
        phraseTargetText.textContent = longPhrase;
        phraseInput.value = "";
        phraseStartTime = Date.now();
        phraseModal.classList.remove('hidden');
    });

    btnClosePhrase.addEventListener('click', () => {
        phraseModal.classList.add('hidden');
    });

    btnVerifyPhrase.addEventListener('click', () => {
        const timeTakenSeconds = (Date.now() - phraseStartTime) / 1000;
        
        // Verificación Anti-Pegado (Nadie escribe 150 palabras en menos de 10 segundos)
        if (timeTakenSeconds < 10) {
            alert("⚠️ Intento de pegado detectado. Debes escribir el texto manualmente letra por letra para demostrar autodisciplina.");
            phraseInput.value = "";
            return;
        }

        if (phraseInput.value.trim() === longPhrase.trim()) {
            chrome.runtime.sendMessage({ action: 'stopTimer' }, () => {
                phraseModal.classList.add('hidden');
                updateUI();
            });
        } else {
            alert("El texto no coincide exactamente. Verifica acentos y signos de puntuación.");
        }
    });

    btnTregua.addEventListener('click', () => {
        const numA = Math.floor(Math.random() * 89) + 10;
        const numB = Math.floor(Math.random() * 89) + 10;
        expectedMath = numA + numB;
        mathProblemText.textContent = `¿Cuánto es ${numA} + ${numB}?`;
        mathInput.value = "";
        mathModal.classList.remove('hidden');
    });

    btnCloseMath.addEventListener('click', () => {
        mathModal.classList.add('hidden');
    });

    btnVerifyMath.addEventListener('click', () => {
        if (parseInt(mathInput.value.trim(), 10) === expectedMath) {
            chrome.runtime.sendMessage({ action: 'grantTregua', minutes: 5 }, () => {
                mathModal.classList.add('hidden');
                updateUI();
            });
        } else {
            alert("Respuesta incorrecta.");
        }
    });

    setInterval(updateUI, 1000);
    updateUI();
});

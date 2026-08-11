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
    const btnNotesToggle = document.getElementById('btn-notes-toggle');
    const notesBox = document.getElementById('notes-box');
    const noteInput = document.getElementById('note-input');
    const btnAddNote = document.getElementById('btn-add-note');
    const notesList = document.getElementById('notes-list');

    const btnGoogleLogin = document.getElementById('btn-google-login');
    const syncKeyInput = document.getElementById('sync-key-input');
    const btnSaveSync = document.getElementById('btn-save-sync');

    // V20.3: menú de perfil y ajustes (Mi Cuenta / Ajustes) en menú desplegable
    const btnProfileMenu = document.getElementById('btn-profile-menu');
    const profileDropdown = document.getElementById('profile-dropdown');
    const profileTitle = document.getElementById('profile-title');

    // Abrir/cerrar el menú desplegable de perfil
    if (btnProfileMenu && profileDropdown) {
        btnProfileMenu.addEventListener('click', (e) => {
            e.stopPropagation();
            profileDropdown.classList.toggle('hidden');
        });
    }

    // Cerrar el menú al hacer clic fuera de él
    document.addEventListener('click', (e) => {
        if (profileDropdown && !profileDropdown.classList.contains('hidden') && !e.target.closest('.profile-menu-wrap')) {
            profileDropdown.classList.add('hidden');
        }
    });

    // Items del menú: abren su modal y cierran el desplegable
    document.querySelectorAll('.dropdown-item').forEach(item => {
        item.addEventListener('click', () => {
            const modal = document.getElementById(item.getAttribute('data-open-modal'));
            if (modal) modal.classList.remove('hidden');
            if (profileDropdown) profileDropdown.classList.add('hidden');
        });
    });

    // Cerrar modales (botón "Cerrar" o clic fuera de la tarjeta)
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

    // V20: toggles de configuración compartida (Modo Oscuro + Bloqueo Cruzado)
    const darkModeSwitch = document.getElementById('dark-mode-switch');
    const crossLockSwitch = document.getElementById('cross-lock-switch');

    // V24 (Propuesta 1): Pomodoro sincronizado. Se activa en Ajustes pero se
    // CONFIGURA en la pantalla de inicio del enfoque (nº descansos + duración).
    const pomodoroSwitch = document.getElementById('pomodoro-switch');
    const pomodoroSetup = document.getElementById('pomodoro-setup');
    const pomodoroSetupToggle = document.getElementById('pomodoro-setup-toggle');
    const pomodoroSetupBody = document.getElementById('pomodoro-setup-body');
    const pomodoroSetupConfig = document.getElementById('pomodoro-setup-config');
    const pomodoroSetupWarning = document.getElementById('pomodoro-setup-warning');
    const pomodoroSetupPreview = document.getElementById('pomodoro-setup-preview');
    const pomodoroRestCountText = document.getElementById('pomodoro-restcount-text');
    const pomodoroRestMaxText = document.getElementById('pomodoro-rest-max');
    const pomodoroRestSetupText = document.getElementById('pomodoro-rest-setup-text');
    const pomodoroRestCountMinus = document.getElementById('pomodoro-restcount-minus');
    const pomodoroRestCountPlus = document.getElementById('pomodoro-restcount-plus');
    const pomodoroRestSetupMinus = document.getElementById('pomodoro-rest-setup-minus');
    const pomodoroRestSetupPlus = document.getElementById('pomodoro-rest-setup-plus');
    let pomodoroWork = 25;
    let pomodoroRest = 5;
    let pomodoroRestCount = 1;
    let pomodoroSetupOpen = true;

    function getFocusTotalMinutes() {
        return (targetHours * 60) + targetMinutes;
    }

    function computePomodoroLimits(totalMinutes) {
        const maxTotalRest = Math.floor(totalMinutes / 4);
        if (maxTotalRest < 1) return { maxRest: 0, maxRestCount: 0 };
        const maxCount = Math.max(1, Math.floor(maxTotalRest / Math.max(1, pomodoroRest)));
        const maxRest = Math.max(1, Math.min(30, Math.floor(maxTotalRest / Math.max(1, pomodoroRestCount))));
        return { maxRest, maxRestCount: maxCount };
    }

    function renderPomodoro() {
        const active = !!(pomodoroSwitch && pomodoroSwitch.checked);
        if (pomodoroSetup) pomodoroSetup.classList.toggle('hidden', !active);
        renderPomodoroSetup();
    }

    function renderPomodoroSetup() {
        const total = getFocusTotalMinutes();
        const { maxRest, maxRestCount } = computePomodoroLimits(total);
        pomodoroRestCount = Math.min(Math.max(pomodoroRestCount, 1), Math.max(maxRestCount, 1));
        pomodoroRest = Math.min(Math.max(pomodoroRest, 1), Math.max(maxRest, 1));
        if (pomodoroRestCountText) pomodoroRestCountText.textContent = pomodoroRestCount;
        if (pomodoroRestMaxText) pomodoroRestMaxText.textContent = maxRest > 0 ? maxRest : 30;
        if (pomodoroRestSetupText) pomodoroRestSetupText.textContent = pomodoroRest + ' min';
        if (pomodoroSetupWarning && pomodoroSetupConfig) {
            if (total < 10) {
                pomodoroSetupWarning.classList.remove('hidden');
                pomodoroSetupWarning.textContent = 'Los descansos solo están disponibles para enfoques de 10 minutos o más. Aumenta el tiempo para configurar los descansos.';
                pomodoroSetupConfig.classList.add('hidden');
            } else {
                pomodoroSetupWarning.classList.add('hidden');
                pomodoroSetupConfig.classList.remove('hidden');
                const workPerBlockMin = Math.floor((total - pomodoroRestCount * pomodoroRest) / (pomodoroRestCount + 1));
                if (pomodoroSetupPreview) {
                    pomodoroSetupPreview.textContent = `${pomodoroRestCount + 1} bloques de trabajo de ~${Math.max(workPerBlockMin, 1)} min con ${pomodoroRestCount} descanso(s) de ${pomodoroRest} min`;
                }
            }
        }
    }

    function setPomodoro(setting, value) {
        if (setting === 'pomodoroRest') pomodoroRest = value;
        if (setting === 'pomodoroRestCount') pomodoroRestCount = value;
        renderPomodoroSetup();
        chrome.storage.local.set(setting === 'pomodoroRest' ? { pomodoroRestMinutes: value } : { pomodoroRestCount: value });
        chrome.runtime.sendMessage({ action: 'setSetting', setting, value });
    }

    if (pomodoroSetupToggle) {
        pomodoroSetupToggle.addEventListener('click', () => {
            pomodoroSetupOpen = !pomodoroSetupOpen;
            if (pomodoroSetupBody) pomodoroSetupBody.classList.toggle('hidden', !pomodoroSetupOpen);
            const arrow = document.getElementById('pomodoro-setup-arrow');
            if (arrow) arrow.textContent = pomodoroSetupOpen ? '▾' : '▸';
        });
    }
    if (pomodoroRestCountMinus) {
        pomodoroRestCountMinus.addEventListener('click', () => setPomodoro('pomodoroRestCount', Math.max(1, pomodoroRestCount - 1)));
    }
    if (pomodoroRestCountPlus) {
        pomodoroRestCountPlus.addEventListener('click', () => {
            const { maxRestCount } = computePomodoroLimits(getFocusTotalMinutes());
            setPomodoro('pomodoroRestCount', Math.min(Math.max(maxRestCount, 1), pomodoroRestCount + 1));
        });
    }
    if (pomodoroRestSetupMinus) {
        pomodoroRestSetupMinus.addEventListener('click', () => setPomodoro('pomodoroRest', Math.max(1, pomodoroRest - 1)));
    }
    if (pomodoroRestSetupPlus) {
        pomodoroRestSetupPlus.addEventListener('click', () => {
            const { maxRest } = computePomodoroLimits(getFocusTotalMinutes());
            setPomodoro('pomodoroRest', Math.min(Math.max(maxRest, 1), pomodoroRest + 1));
        });
    }

    // V24: verificación en 2 pasos del Bloqueo Cruzado. El código llega por
    // notificación al teléfono y se escribe aquí en el PC para confirmar.
    const authCodeModal = document.getElementById('auth-code-modal');
    const authCodeInput = document.getElementById('auth-code-input');
    const authPending = document.getElementById('auth-pending');
    const authError = document.getElementById('auth-error');
    const btnCancelAuth = document.getElementById('btn-cancel-auth');
    const btnConfirmAuth = document.getElementById('btn-confirm-auth');
    let pendingCrossLockValue = null;

    function requestAuthForToggle(value) {
        pendingCrossLockValue = value;
        if (authCodeInput) authCodeInput.value = '';
        if (authError) authError.classList.add('hidden');
        if (authPending) authPending.classList.remove('hidden');
        if (authCodeModal) authCodeModal.classList.remove('hidden');
        chrome.runtime.sendMessage({ action: 'requestAuthCode' }, (res) => {
            if (authPending) authPending.classList.add('hidden');
            if (res && res.success) {
                if (authCodeInput) authCodeInput.focus();
            } else {
                if (authError) {
                    authError.textContent = 'No se pudo solicitar el código. Revisa la conexión y que el teléfono esté vinculado.';
                    authError.classList.remove('hidden');
                }
            }
        });
    }

    if (crossLockSwitch) {
        crossLockSwitch.addEventListener('change', () => {
            const value = crossLockSwitch.checked;
            requestAuthForToggle(value);
        });
    }

    if (btnCancelAuth) {
        btnCancelAuth.addEventListener('click', () => {
            if (authCodeModal) authCodeModal.classList.add('hidden');
            if (crossLockSwitch && pendingCrossLockValue !== null) crossLockSwitch.checked = !pendingCrossLockValue;
            pendingCrossLockValue = null;
        });
    }
    if (btnConfirmAuth) {
        btnConfirmAuth.addEventListener('click', () => {
            const code = authCodeInput ? authCodeInput.value.trim() : '';
            if (!code) return;
            chrome.runtime.sendMessage({ action: 'verifyAuthCode', code }, (res) => {
                if (res && res.success) {
                    if (authCodeModal) authCodeModal.classList.add('hidden');
                    chrome.storage.local.set({ crossDeviceLockEnabled: pendingCrossLockValue });
                    chrome.runtime.sendMessage({ action: 'setSetting', setting: 'crossDeviceLock', value: pendingCrossLockValue });
                    pendingCrossLockValue = null;
                } else {
                    if (authError && res && res.error && res.error.indexOf('aún no ha generado') !== -1) {
                        if (authPending) authPending.classList.remove('hidden');
                        authError.classList.add('hidden');
                        setTimeout(() => btnConfirmAuth.click(), 1500);
                    } else {
                        if (authError) authError.classList.remove('hidden');
                        if (authCodeInput) { authCodeInput.value = ''; authCodeInput.focus(); }
                    }
                }
            });
        });
    }
    if (authCodeInput) {
        authCodeInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && btnConfirmAuth) btnConfirmAuth.click();
        });
    }

    // V20: aplicar modo oscuro a toda la interfaz del popup
    function applyTheme(dark) {
        document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    }

    function loadSettings() {
        chrome.runtime.sendMessage({ action: 'getSettings' }, (res) => {
            if (!res) return;
            if (darkModeSwitch) darkModeSwitch.checked = !!res.darkModeEnabled;
            if (crossLockSwitch) crossLockSwitch.checked = !!res.crossDeviceLockEnabled;
            if (pomodoroSwitch) pomodoroSwitch.checked = !!res.pomodoroEnabled;
            if (typeof res.pomodoroWorkMinutes === 'number') pomodoroWork = res.pomodoroWorkMinutes;
            if (typeof res.pomodoroRestMinutes === 'number') pomodoroRest = res.pomodoroRestMinutes;
            if (typeof res.pomodoroRestCount === 'number') pomodoroRestCount = res.pomodoroRestCount;
            renderPomodoro();
            applyTheme(!!res.darkModeEnabled);
        });
        // V24: leer los ajustes persistidos directamente para aplicarlos aunque el
        // service worker aún no haya cargado sus variables en memoria (arranque en frío).
        chrome.storage.local.get(['darkModeEnabled', 'crossDeviceLockEnabled', 'pomodoroEnabled', 'pomodoroWorkMinutes', 'pomodoroRestMinutes', 'pomodoroRestCount'], (res) => {
            if (typeof res.darkModeEnabled !== 'boolean' && typeof res.crossDeviceLockEnabled !== 'boolean') return;
            const dark = typeof res.darkModeEnabled === 'boolean' ? res.darkModeEnabled : false;
            const lock = typeof res.crossDeviceLockEnabled === 'boolean' ? res.crossDeviceLockEnabled : true;
            if (darkModeSwitch) darkModeSwitch.checked = dark;
            if (crossLockSwitch) crossLockSwitch.checked = lock;
            if (pomodoroSwitch) pomodoroSwitch.checked = !!res.pomodoroEnabled;
            if (typeof res.pomodoroWorkMinutes === 'number') pomodoroWork = res.pomodoroWorkMinutes;
            if (typeof res.pomodoroRestMinutes === 'number') pomodoroRest = res.pomodoroRestMinutes;
            if (typeof res.pomodoroRestCount === 'number') pomodoroRestCount = res.pomodoroRestCount;
            renderPomodoro();
            applyTheme(dark);
        });
    }

    if (darkModeSwitch) {
        darkModeSwitch.addEventListener('change', () => {
            const value = darkModeSwitch.checked;
            chrome.storage.local.set({ darkModeEnabled: value });
            chrome.runtime.sendMessage({ action: 'setSetting', setting: 'darkMode', value });
            applyTheme(value);
        });
    }

    // V24 (Propuesta 1): activación del Pomodoro (la configuración vive en el setup)
    if (pomodoroSwitch) {
        pomodoroSwitch.addEventListener('change', () => {
            const value = pomodoroSwitch.checked;
            chrome.storage.local.set({ pomodoroEnabled: value });
            chrome.runtime.sendMessage({ action: 'setSetting', setting: 'pomodoroEnabled', value });
            renderPomodoro();
        });
    }

    if (btnNotesToggle) {
        btnNotesToggle.addEventListener('click', () => {
            chrome.tabs.create({ url: chrome.runtime.getURL('notes.html') });
        });
    }

    if (btnAddNote && noteInput) {
        btnAddNote.addEventListener('click', () => {
            const content = noteInput.value.trim();
            if (content) {
                chrome.runtime.sendMessage({ action: 'addNote', content }, (res) => {
                    if (res && res.success) {
                        noteInput.value = '';
                        loadAndRenderNotes();
                        showNotesSyncHint(false);
                    } else {
                        showNotesSyncHint(true, 'No se pudo guardar la nota. Revisa la extensión.');
                    }
                });
            }
        });

        noteInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                btnAddNote.click();
            }
        });
    }

    const notesSyncHint = document.getElementById('notes-sync-hint');

    function showNotesSyncHint(show, text) {
        if (!notesSyncHint) return;
        if (show) {
            notesSyncHint.innerHTML = text || '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align: middle;"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg> Nota guardada localmente (sin sincronizar). Inicia sesión con Google.';
            notesSyncHint.classList.remove('hidden');
        } else {
            notesSyncHint.classList.add('hidden');
        }
    }

    // Escuchar el resultado real de la sincronización en la nube
    chrome.runtime.onMessage.addListener((request) => {
        if (request && request.action === 'noteCloudStatus') {
            if (!request.ok) {
                showNotesSyncHint(true, '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align: middle;"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg> Nota guardada localmente (sin sincronizar): ' + (request.error || 'inicia sesión con Google.'));
            } else {
                showNotesSyncHint(false);
            }
        } else if (request && request.action === 'settingsChanged' && request.settings) {
            // V20: la configuración cambió (desde este popup o desde el teléfono)
            if (darkModeSwitch) darkModeSwitch.checked = !!request.settings.darkModeEnabled;
            if (crossLockSwitch) crossLockSwitch.checked = !!request.settings.crossDeviceLockEnabled;
            if (pomodoroSwitch) pomodoroSwitch.checked = !!request.settings.pomodoroEnabled;
            if (typeof request.settings.pomodoroWorkMinutes === 'number') pomodoroWork = request.settings.pomodoroWorkMinutes;
            if (typeof request.settings.pomodoroRestMinutes === 'number') pomodoroRest = request.settings.pomodoroRestMinutes;
            if (typeof request.settings.pomodoroRestCount === 'number') pomodoroRestCount = request.settings.pomodoroRestCount;
            renderPomodoro();
            applyTheme(!!request.settings.darkModeEnabled);
        } else if (request && request.action === 'lockStateChanged') {
            // V24: bloqueo iniciado desde el teléfono → refrescar la UI al instante
            updateUI();
        } else if (request && request.action === 'pomodoroPhaseChanged') {
            // V24: cambio de fase trabajo/descanso del Pomodoro
            updateUI();
        } else if (request && request.action === 'treguaApproved') {
            // V24 (Propuesta 2): el teléfono aprobó la tregua
            if (mathModal) mathModal.classList.add('hidden');
            updateUI();
        }
    });

    function loadAndRenderNotes() {
        if (!notesList) return;
        chrome.runtime.sendMessage({ action: 'getNotes' }, (res) => {
            if (res && res.notes && res.notes.length > 0) {
                notesList.innerHTML = res.notes.map(note => {
                    const dateStr = note.timestamp ? new Date(note.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
                    const srcStr = note.deviceSource === 'android' ? '📱 Android' : '🌐 Chrome';
                    return `
                        <div class="note-item">
                            <div class="note-info">
                                <div class="note-content">${escapeHtml(note.content)}</div>
                                <div class="note-meta">${dateStr} • ${srcStr}</div>
                            </div>
                            <button class="btn-del-note" data-id="${note.id}">🗑️</button>
                        </div>
                    `;
                }).join('');

                notesList.querySelectorAll('.btn-del-note').forEach(btn => {
                    btn.addEventListener('click', (e) => {
                        const noteId = e.target.getAttribute('data-id');
                        chrome.runtime.sendMessage({ action: 'deleteNote', noteId }, () => {
                            loadAndRenderNotes();
                        });
                    });
                });
            } else {
                notesList.innerHTML = '<div style="font-size:0.72rem; color:#64748B; text-align:center; padding:10px;">Sin notas aún. ¡Anota tus ideas!</div>';
            }
        });
    }

    function escapeHtml(text) {
        return text.replace(/[&<>"']/g, function(m) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[m];
        });
    }

    if (phraseInput) {
        phraseInput.addEventListener('paste', (e) => e.preventDefault());
        phraseInput.addEventListener('copy', (e) => e.preventDefault());
        phraseInput.addEventListener('contextmenu', (e) => e.preventDefault());
    }

    // V20: anti-copy reforzado sobre el texto objetivo (no se puede seleccionar ni arrastrar)
    if (phraseTargetText) {
        phraseTargetText.addEventListener('selectstart', (e) => e.preventDefault());
        phraseTargetText.addEventListener('dragstart', (e) => e.preventDefault());
        phraseTargetText.addEventListener('copy', (e) => e.preventDefault());
        phraseTargetText.addEventListener('cut', (e) => e.preventDefault());
    }

    // V20.3: resaltar en vivo la frase objetivo (verde = correcto, rojo = error,
    // gris = aún sin escribir), igual que en la app Android
    function renderHighlightedPhrase() {
        if (!phraseTargetText || !phraseInput) return;
        const typedChars = Array.from(phraseInput.value);
        const targetChars = Array.from(longPhrase);
        let html = '';
        targetChars.forEach((ch, i) => {
            if (i < typedChars.length) {
                if (typedChars[i] === ch) {
                    html += `<span class="phrase-correct">${escapeHtml(ch)}</span>`;
                } else {
                    html += `<span class="phrase-wrong">${escapeHtml(ch)}</span>`;
                }
            } else {
                html += `<span class="phrase-pending">${escapeHtml(ch)}</span>`;
            }
        });
        phraseTargetText.innerHTML = html;
    }

    if (phraseInput) {
        phraseInput.addEventListener('input', renderHighlightedPhrase);
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

    function updateUI() {
        chrome.runtime.sendMessage({ action: 'getState' }, (response) => {
            if (!response) return;
            const { isLocked, remainingSeconds, customDomains, parentalEnabled, firebaseUid, userEmail, syncKey, connectedDeviceInfo, phaseType, treguaUntil, treguaCooldownUntil } = response;

            // V24: banner de descanso Pomodoro (tregua libre)
            const restBanner = document.getElementById('pomodoro-rest-banner');
            if (restBanner) {
                restBanner.classList.toggle('hidden', !(isLocked && phaseType === 'rest'));
            }

            // V20: puntico de conexión (verde = teléfono conectado, rojo = sin conexión)
            const connDot = document.getElementById('conn-dot');
            if (connDot) {
                if (connectedDeviceInfo) {
                    connDot.classList.add('connected');
                } else {
                    connDot.classList.remove('connected');
                }
            }

            if (parentalSwitch) parentalSwitch.checked = !!parentalEnabled;
            if (customDomains) renderSiteTags(customDomains);

            // Fallback para leer clave local si faltara en la respuesta
            chrome.storage.local.get(['userEmail', 'syncKey', 'firebaseUid'], (localRes) => {
                const activeAccount = userEmail || syncKey || localRes.userEmail || localRes.syncKey || localRes.firebaseUid;
                if (profileTitle) {
                    profileTitle.textContent = activeAccount ? activeAccount : 'Mi Cuenta';
                }
                if (btnGoogleLogin) {
                    if (activeAccount) {
                        btnGoogleLogin.innerHTML = `<span class="google-icon">G</span> ${activeAccount}`;
                        btnGoogleLogin.style.backgroundColor = '#34A853';
                    } else {
                        btnGoogleLogin.innerHTML = `<span class="google-icon">G</span> Iniciar Sesión con Google`;
                        btnGoogleLogin.style.backgroundColor = '#4285F4';
                    }
                }
            });

            const now = Date.now();
            if (treguaCooldownUntil && treguaCooldownUntil > now) {
                btnTregua.disabled = true;
                btnTregua.textContent = `Tregua en ${formatTime(Math.floor((treguaCooldownUntil - now) / 1000))}`;
            } else {
                btnTregua.disabled = false;
                btnTregua.textContent = "Pedir Tregua";
            }

            if (isLocked) {
                const now = Date.now();
                if (treguaUntil && treguaUntil > now) {
                    statusPill.textContent = "TREGUA ACTIVA";
                    statusPill.classList.remove("status-locked");
                    statusPill.style.backgroundColor = "#EAB308"; // Amarillo para destacar
                    statusPill.style.color = "#FFFFFF";
                    timerText.textContent = formatTime(Math.floor((treguaUntil - now) / 1000));
                } else {
                    statusPill.textContent = "MODO ENFOQUE ACTIVO";
                    statusPill.classList.add("status-locked");
                    statusPill.style.backgroundColor = "";
                    statusPill.style.color = "";
                    timerText.textContent = formatTime(remainingSeconds);
                }
                
                setupControls.classList.add("hidden");
                btnStart.classList.add("hidden");
                lockedControls.classList.remove("hidden");
            } else {
                statusPill.textContent = "MODO LIBRE";
                statusPill.classList.remove("status-locked");
                statusPill.style.backgroundColor = "";
                statusPill.style.color = "";
                setupControls.classList.remove("hidden");
                btnStart.classList.remove("hidden");
                lockedControls.classList.add("hidden");
                updateTimeDisplay();
            }
        });
    }

    if (btnGoogleLogin) {
        btnGoogleLogin.addEventListener('click', () => {
            chrome.tabs.create({ url: chrome.runtime.getURL('login.html') });
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
        phraseTargetText.textContent = "";
        phraseInput.value = "";
        phraseStartTime = Date.now();
        renderHighlightedPhrase();
        phraseModal.classList.remove('hidden');
    });

    btnClosePhrase.addEventListener('click', () => {
        phraseModal.classList.add('hidden');
    });

    btnVerifyPhrase.addEventListener('click', () => {
        const timeTakenSeconds = (Date.now() - phraseStartTime) / 1000;
        
        // Verificación Anti-Pegado (Nadie escribe 150 palabras en menos de 10 segundos)
        if (timeTakenSeconds < 10) {
            alert("Intento de pegado detectado. Debes escribir el texto manualmente letra por letra para demostrar autodisciplina.");
            phraseInput.value = "";
            return;
        }

        // V20: comparar la cantidad de caracteres carácter por carácter.
        // La persona debe escribir a mano al menos la mitad (50%) del texto.
        const target = longPhrase.trim();
        const typed = phraseInput.value.trim();
        const targetChars = Array.from(target);
        const typedChars = Array.from(typed);
        const compareCount = Math.min(targetChars.length, typedChars.length);
        let matches = 0;
        for (let i = 0; i < compareCount; i++) {
            if (typedChars[i] === targetChars[i]) matches++;
        }
        const matchRatio = targetChars.length > 0 ? matches / targetChars.length : 0;

        if (matchRatio >= 0.5) {
            chrome.runtime.sendMessage({ action: 'stopTimer' }, () => {
                phraseModal.classList.add('hidden');
                updateUI();
            });
        } else {
            alert("Todavía no has escrito a mano al menos la mitad del texto correctamente. Sigue escribiendo con calma.");
        }
    });

    // V24 (Propuesta 2): reto matemático local (fallback sin teléfono conectado)
    function openMathTregua() {
        const numA = Math.floor(Math.random() * 89) + 10;
        const numB = Math.floor(Math.random() * 89) + 10;
        expectedMath = numA + numB;
        mathProblemText.textContent = `¿Cuánto es ${numA} + ${numB}?`;
        mathInput.value = "";
        const pendingEl = document.getElementById('tregua-pending');
        const challengeEl = document.getElementById('math-challenge');
        if (pendingEl) pendingEl.classList.add('hidden');
        if (challengeEl) challengeEl.classList.remove('hidden');
        mathModal.classList.remove('hidden');
    }

    // V24 (Propuesta 2): estado "esperando confirmación en el teléfono"
    function showTreguaPending() {
        const pendingEl = document.getElementById('tregua-pending');
        const challengeEl = document.getElementById('math-challenge');
        if (pendingEl) pendingEl.classList.remove('hidden');
        if (challengeEl) challengeEl.classList.add('hidden');
        mathModal.classList.remove('hidden');
    }

    btnTregua.addEventListener('click', () => {
        // V24 (Propuesta 2): si el teléfono está conectado, la tregua se verifica ahí
        chrome.runtime.sendMessage({ action: 'getState' }, (res) => {
            if (res && res.connectedDeviceInfo) {
                chrome.runtime.sendMessage({ action: 'requestTregua' }, (req) => {
                    if (req && req.success && req.pending) {
                        showTreguaPending();
                    } else {
                        openMathTregua();
                    }
                });
            } else {
                openMathTregua();
            }
        });
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
    loadSettings();
});

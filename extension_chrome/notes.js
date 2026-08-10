document.addEventListener('DOMContentLoaded', () => {
    let currentFontSize = 15;
    let selectedCategory = 'general';
    let currentFilter = 'all';
    let notesCache = [];

    // DOM Elements
    const btnFontSub = document.getElementById('btn-font-sub');
    const btnFontAdd = document.getElementById('btn-font-add');
    const fontSizeDisplay = document.getElementById('font-size-display');
    const userEmailText = document.getElementById('user-email-text');

    const catChips = document.querySelectorAll('.cat-chip');
    const noteTextInput = document.getElementById('note-text-input');
    const btnSaveNote = document.getElementById('btn-save-note');

    const searchInput = document.getElementById('search-input');
    const filterChips = document.querySelectorAll('.filter-chip');
    const notesGrid = document.getElementById('notes-grid');

    // 1. Font Size Controller
    function updateFontSize(newSize) {
        currentFontSize = Math.max(12, Math.min(24, newSize));
        fontSizeDisplay.textContent = `${currentFontSize}px`;
        document.documentElement.style.setProperty('--dynamic-font-size', `${currentFontSize}px`);
        chrome.storage.local.set({ userFontSize: currentFontSize });
    }

    if (btnFontSub) btnFontSub.addEventListener('click', () => updateFontSize(currentFontSize - 1));
    if (btnFontAdd) btnFontAdd.addEventListener('click', () => updateFontSize(currentFontSize + 1));

    // Restore Font Size
    chrome.storage.local.get(['userFontSize', 'userEmail', 'syncKey', 'firebaseUid'], (res) => {
        if (res.userFontSize) updateFontSize(res.userFontSize);
        const email = res.userEmail || res.syncKey || res.firebaseUid || 'Cuenta Google Active';
        if (userEmailText) userEmailText.textContent = email;
    });

    // 2. Category Chips Selector
    catChips.forEach(chip => {
        chip.addEventListener('click', () => {
            catChips.forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            selectedCategory = chip.getAttribute('data-cat');
        });
    });

    // 3. Save Note
    const saveStatus = document.getElementById('save-status');

    function showSaveStatus(ok, text) {
        if (!saveStatus) return;
        saveStatus.textContent = text;
        saveStatus.className = 'save-status ' + (ok ? 'ok' : 'warn');
    }

    // V20: aplicar modo oscuro compartido a la interfaz del bloc de notas
    function applyTheme(dark) {
        document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    }

    // V23: menú de perfil y ajustes (Mi Cuenta / Ajustes) con toggles sincronizados
    const btnProfileMenu = document.getElementById('btn-profile-menu');
    const profileDropdown = document.getElementById('profile-dropdown');
    const darkModeSwitch = document.getElementById('dark-mode-switch');
    const crossLockSwitch = document.getElementById('cross-lock-switch');

    if (btnProfileMenu && profileDropdown) {
        btnProfileMenu.addEventListener('click', (e) => {
            e.stopPropagation();
            profileDropdown.classList.toggle('hidden');
        });
    }

    document.addEventListener('click', (e) => {
        if (profileDropdown && !profileDropdown.classList.contains('hidden') && !e.target.closest('.profile-menu-wrap')) {
            profileDropdown.classList.add('hidden');
        }
    });

    document.querySelectorAll('.dropdown-item').forEach(item => {
        item.addEventListener('click', () => {
            const modal = document.getElementById(item.getAttribute('data-open-modal'));
            if (modal) modal.classList.remove('hidden');
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

    const btnGoogleLogin = document.getElementById('btn-google-login');
    if (btnGoogleLogin) {
        btnGoogleLogin.addEventListener('click', () => {
            chrome.tabs.create({ url: chrome.runtime.getURL('login.html') });
        });
    }

    // Escuchar el resultado real de la sincronización en la nube y la configuración
    chrome.runtime.onMessage.addListener((request) => {
        if (request && request.action === 'noteCloudStatus') {
            const msg = request.ok
                ? '✅ Nota guardada y sincronizada en la nube'
                : '⚠️ Nota guardada localmente (sin sincronizar): ' + (request.error || '');
            showSaveStatus(request.ok, msg);
        } else if (request && request.action === 'settingsChanged' && request.settings) {
            if (darkModeSwitch) darkModeSwitch.checked = !!request.settings.darkModeEnabled;
            if (crossLockSwitch) crossLockSwitch.checked = !!request.settings.crossDeviceLockEnabled;
            applyTheme(!!request.settings.darkModeEnabled);
        }
    });

    if (btnSaveNote && noteTextInput) {
        btnSaveNote.addEventListener('click', () => {
            const content = noteTextInput.value.trim();
            if (!content) return;

            btnSaveNote.disabled = true;
            btnSaveNote.textContent = 'Guardando...';
            showSaveStatus(null, 'Guardando nota...');

            chrome.runtime.sendMessage({
                action: 'addNote',
                content: content,
                category: selectedCategory
            }, (res) => {
                btnSaveNote.disabled = false;
                btnSaveNote.textContent = '✨ Guardar Nota Zen';
                if (res && res.success) {
                    noteTextInput.value = '';
                    fetchAndRenderNotes();
                    showSaveStatus(false, '⏳ Nota guardada localmente. Sincronizando...');
                } else {
                    showSaveStatus(false, '⚠️ No se pudo guardar la nota. Revisa la extensión.');
                }
            });
        });
    }

    // 4. Search and Filters
    if (searchInput) {
        searchInput.addEventListener('input', () => renderNotesGrid());
    }

    filterChips.forEach(chip => {
        chip.addEventListener('click', () => {
            filterChips.forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            currentFilter = chip.getAttribute('data-filter');
            renderNotesGrid();
        });
    });

    // 5. Fetch & Render Notes
    function fetchAndRenderNotes() {
        chrome.runtime.sendMessage({ action: 'getNotes' }, (res) => {
            if (res && res.notes) {
                notesCache = res.notes;
                renderNotesGrid();
            }
        });
    }

    function renderNotesGrid() {
        if (!notesGrid) return;

        const query = searchInput ? searchInput.value.toLowerCase().trim() : '';

        let filtered = notesCache.filter(note => {
            const matchesQuery = note.content.toLowerCase().includes(query);
            const noteCat = note.category || 'general';
            const matchesFilter = currentFilter === 'all' || noteCat === currentFilter;
            return matchesQuery && matchesFilter;
        });

        if (filtered.length === 0) {
            notesGrid.innerHTML = `
                <div style="grid-column: 1/-1; text-align:center; padding: 40px; background:var(--card-bg); border-radius:20px; border:1px solid var(--card-border);">
                    <div style="font-size:2.5rem; margin-bottom:10px;">💡</div>
                    <h3 style="font-size:1.1rem; color:var(--text-main); margin-bottom:6px;">No se encontraron notas</h3>
                    <p style="font-size:0.85rem; color:var(--text-muted);">Escribe tu primera nota en el panel izquierdo para liberarte de las distracciones.</p>
                </div>
            `;
            return;
        }

        notesGrid.innerHTML = filtered.map(note => {
            const dateStr = note.timestamp ? new Date(note.timestamp).toLocaleString([], { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '';
            const catLabel = getCatLabel(note.category || 'general');
            const sourceBadge = note.deviceSource === 'android' ? '📱 Android' : '🌐 Chrome';

            return `
                <div class="note-card-full" data-id="${note.id}">
                    <div class="note-header-row">
                        <span class="cat-badge">${catLabel}</span>
                        <span class="note-meta-info">${sourceBadge}</span>
                    </div>
                    <div class="note-body">${escapeHtml(note.content)}</div>
                    <div class="note-footer-row">
                        <span class="note-meta-info">${dateStr}</span>
                        <div class="note-actions">
                            <button class="btn-icon-action btn-copy" data-text="${escapeHtml(note.content)}" title="Copiar nota">📋</button>
                            <button class="btn-icon-action btn-delete" data-id="${note.id}" title="Eliminar nota">🗑️</button>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        // Event listeners para copiar y eliminar
        notesGrid.querySelectorAll('.btn-copy').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const text = e.target.getAttribute('data-text');
                navigator.clipboard.writeText(text);
                e.target.textContent = '✅';
                setTimeout(() => { e.target.textContent = '📋'; }, 1500);
            });
        });

        notesGrid.querySelectorAll('.btn-delete').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const noteId = e.target.getAttribute('data-id');
                chrome.runtime.sendMessage({ action: 'deleteNote', noteId }, () => {
                    fetchAndRenderNotes();
                });
            });
        });
    }

    function getCatLabel(cat) {
        switch(cat) {
            case 'tarea': return '<img src="emojis/pushpin_3d.png" class="emoji-3d-sm"> Tarea';
            case 'idea': return '<img src="emojis/rocket_3d.png" class="emoji-3d-sm"> Idea';
            case 'reflexion': return '<img src="emojis/brain_3d.png" class="emoji-3d-sm"> Reflexión';
            default: return '<img src="emojis/light_bulb_3d.png" class="emoji-3d-sm"> General';
        }
    }

    function escapeHtml(text) {
        return (text || '').replace(/[&<>"']/g, function(m) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[m];
        });
    }

    // Rotador de Frases de Invitación a Anotar Ideas con Emojis 3D
    const reflectionPhrases = [
        '<img src="emojis/sparkles_3d.png" class="emoji-3d-sm"> Tu mente es para tener ideas, no para guardarlas. Anótalas aquí y libera tu concentración.',
        '<img src="emojis/light_bulb_3d.png" class="emoji-3d-sm"> Si una idea cruza tu mente, anótala en tus notas y continúa tu enfoque.',
        '<img src="emojis/brain_3d.png" class="emoji-3d-sm"> Pensar por ti mismo es el superpoder más valioso que tienes.',
        '<img src="emojis/rocket_3d.png" class="emoji-3d-sm"> Vaciando tu cabeza en notas le das espacio a nuevas ideas brillantes.',
        '<img src="emojis/memo_3d.png" class="emoji-3d-sm"> Una nota escrita vale más que mil intenciones olvidadas.',
        '<img src="emojis/sparkles_3d.png" class="emoji-3d-sm"> Escribir lo que piensas te aclara la mente y te devuelve la serenidad.',
        '<img src="emojis/pushpin_3d.png" class="emoji-3d-sm"> Descarga tus pendientes en notas para trabajar con absoluta paz mental.',
        '<img src="emojis/rocket_3d.png" class="emoji-3d-sm"> Apunta tus mejores ideas de inmediato antes de que el ruido las disipe.'
    ];

    const reflectionText = document.getElementById('reflection-text');
    let phraseIdx = 0;
    if (reflectionText) {
        setInterval(() => {
            reflectionText.style.opacity = '0';
            setTimeout(() => {
                phraseIdx = (phraseIdx + 1) % reflectionPhrases.length;
                reflectionText.innerHTML = reflectionPhrases[phraseIdx];
                reflectionText.style.opacity = '1';
            }, 400);
        }, 30000);
    }

    // Inicializar
    // V24: leer los ajustes persistidos directamente para no depender de la
    // memoria del service worker (evita volver al modo claro tras reiniciar).
    chrome.storage.local.get(['darkModeEnabled', 'crossDeviceLockEnabled'], (res) => {
        const dark = typeof res.darkModeEnabled === 'boolean' ? res.darkModeEnabled : false;
        const lock = typeof res.crossDeviceLockEnabled === 'boolean' ? res.crossDeviceLockEnabled : true;
        applyTheme(dark);
        if (darkModeSwitch) darkModeSwitch.checked = dark;
        if (crossLockSwitch) crossLockSwitch.checked = lock;
    });
    chrome.runtime.sendMessage({ action: 'getSettings' }, (res) => {
        if (res) {
            applyTheme(!!res.darkModeEnabled);
            if (darkModeSwitch) darkModeSwitch.checked = !!res.darkModeEnabled;
            if (crossLockSwitch) crossLockSwitch.checked = !!res.crossDeviceLockEnabled;
        }
    });
    fetchAndRenderNotes();
    setInterval(fetchAndRenderNotes, 5000); // Live poll sync
});

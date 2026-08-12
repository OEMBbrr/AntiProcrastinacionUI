document.addEventListener('DOMContentLoaded', () => {
    let currentFontSize = 15;
    let selectedCategory = 'general';
    let currentFilter = 'all';
    let notesCache = [];

    // V24.1: clave canónica de categoría compartida con Android (tolera mayúsculas/acentos)
    function normalizeCategory(raw) {
        const key = String(raw || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim();
        switch (key) {
            case 'tarea': return 'tarea';
            case 'idea': return 'idea';
            case 'reflexion': return 'reflexion';
            default: return 'general';
        }
    }

    // DOM Elements
    const btnFontSub = document.getElementById('btn-font-sub');
    const btnFontAdd = document.getElementById('btn-font-add');
    const fontSizeDisplay = document.getElementById('font-size-display');
    const userEmailText = document.getElementById('user-email-text');

    const catChips = document.querySelectorAll('.cat-chip');
    const noteTitleInput = document.getElementById('note-title-input');
    const noteTextInput = document.getElementById('note-text-input');
    const noteImgInput = document.getElementById('note-img-input');
    const noteImgPreview = document.getElementById('note-img-preview');
    const imgPreviewEl = document.getElementById('img-preview-el');
    const btnRemoveImg = document.getElementById('btn-remove-img');
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

    // Image Upload Logic
    if (noteImgInput) {
        noteImgInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = (event) => {
                const img = new Image();
                img.onload = () => {
                    const canvas = document.createElement('canvas');
                    const MAX_WIDTH = 800;
                    let width = img.width;
                    let height = img.height;
                    
                    if (width > MAX_WIDTH) {
                        height = Math.round((height * MAX_WIDTH) / width);
                        width = MAX_WIDTH;
                    }
                    canvas.width = width;
                    canvas.height = height;
                    const ctx = canvas.getContext('2d');
                    ctx.drawImage(img, 0, 0, width, height);
                    const compressedBase64 = canvas.toDataURL('image/jpeg', 0.7);
                    
                    if (imgPreviewEl) imgPreviewEl.src = compressedBase64;
                    if (noteImgPreview) noteImgPreview.classList.remove('hidden');
                };
                img.src = event.target.result;
            };
            reader.readAsDataURL(file);
        });
    }

    if (btnRemoveImg) {
        btnRemoveImg.addEventListener('click', () => {
            if (imgPreviewEl) imgPreviewEl.src = '';
            if (noteImgPreview) noteImgPreview.classList.add('hidden');
            if (noteImgInput) noteImgInput.value = '';
        });
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
                ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align: middle;"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg> Nota guardada y sincronizada en la nube'
                : '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align: middle;"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg> Nota guardada localmente (sin sincronizar): ' + (request.error || '');
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
            const title = noteTitleInput ? noteTitleInput.value.trim() : '';
            const imgData = imgPreviewEl ? imgPreviewEl.src : '';
            const image = (imgData && imgData.startsWith('data:image')) ? imgData : null;

            if (!content && !title && !image) return;

            btnSaveNote.disabled = true;
            btnSaveNote.textContent = 'Guardando...';
            showSaveStatus(null, 'Guardando nota...');

            chrome.runtime.sendMessage({
                action: 'addNote',
                title: title,
                content: content,
                image: image,
                category: selectedCategory
            }, (res) => {
                btnSaveNote.disabled = false;
                btnSaveNote.textContent = 'Guardar';
                if (res && res.success) {
                    noteTextInput.value = '';
                    if (noteTitleInput) noteTitleInput.value = '';
                    if (btnRemoveImg) btnRemoveImg.click();
                    fetchAndRenderNotes();
                    showSaveStatus(false, '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align: middle;"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg> Nota guardada localmente. Sincronizando...');
                } else {
                    showSaveStatus(false, '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align: middle;"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg> No se pudo guardar la nota. Revisa la extensión.');
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
            const noteCat = normalizeCategory(note.category);
            const matchesFilter = currentFilter === 'all' || noteCat === currentFilter;
            return matchesQuery && matchesFilter;
        });

        if (filtered.length === 0) {
            notesGrid.innerHTML = `
                <div style="grid-column: 1/-1; text-align:center; padding: 40px; background:transparent;">
                    <div style="margin-bottom:10px;"><svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="color: var(--text-muted);"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg></div>
                    <h3 style="font-size:1.1rem; color:var(--text-main); margin-bottom:6px;">No se encontraron notas</h3>
                    <p style="font-size:0.85rem; color:var(--text-muted);">Añade una nota para liberarte de las distracciones.</p>
                </div>
            `;
            return;
        }

        notesGrid.innerHTML = filtered.map(note => {
            const dateStr = note.timestamp ? new Date(note.timestamp).toLocaleString([], { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '';
            const sourceBadge = note.deviceSource === 'android' ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align: middle;"><rect x="5" y="2" width="14" height="20" rx="2" ry="2"></rect><line x1="12" y1="18" x2="12.01" y2="18"></line></svg>' : '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align: middle;"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg>';

            let catText = note.category || 'general';
            if(catText === 'tarea') catText = 'Tarea';
            else if(catText === 'idea') catText = 'Idea';
            else if(catText === 'reflexion') catText = 'Reflexión';
            else catText = 'General';

            const titleHtml = note.title ? `<div class="note-card-title">${escapeHtml(note.title)}</div>` : '';
            const imgHtml = note.image ? `<img class="note-card-img" src="${note.image}" alt="Imagen adjunta" onload="window.dispatchEvent(new Event('resize'))">` : '';

            return `
                <div class="note-card" data-id="${note.id}">
                    ${imgHtml}
                    ${titleHtml}
                    ${note.content ? `<div class="note-card-text">${escapeHtml(note.content)}</div>` : ''}
                    <div class="note-card-footer">
                        <div>
                            <span class="note-card-category">${catText}</span>
                            <span class="note-card-meta" style="margin-left:6px;">${sourceBadge} ${dateStr}</span>
                        </div>
                        <div style="display:flex; align-items:center; gap:4px;">
                            <button class="btn-focus-note" data-id="${note.id}" title="Enfocarme en esto (Pomodoro)"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align: middle;"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg></button>
                            <button class="btn-del-note" data-id="${note.id}" title="Eliminar nota"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align: middle;"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg></button>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        notesGrid.querySelectorAll('.btn-del-note').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const noteId = e.currentTarget.getAttribute('data-id');
                chrome.runtime.sendMessage({ action: 'deleteNote', noteId }, () => {
                    fetchAndRenderNotes();
                });
            });
        });

        notesGrid.querySelectorAll('.btn-focus-note').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const mins = prompt('¿Cuántos minutos quieres enfocarte en esta tarea?', '25');
                if (mins !== null) {
                    const parsed = parseInt(mins, 10);
                    if (!isNaN(parsed) && parsed > 0) {
                        chrome.runtime.sendMessage({ action: 'startTimer', minutes: parsed }, (res) => {
                            if (res && res.success) {
                                alert(`¡Pomodoro de ${parsed} min iniciado!\nAbre la extensión para ver el temporizador.`);
                            }
                        });
                    } else {
                        alert('Por favor, ingresa un número válido mayor a 0.');
                    }
                }
            });
        });

        // Aplicar Masonry calculation (True Masonry via CSS Grid rows)
        setTimeout(applyMasonrySpans, 50);
    }

    function applyMasonrySpans() {
        if (!notesGrid) return;
        const cards = notesGrid.querySelectorAll('.note-card');
        cards.forEach(card => {
            // Remove span to get natural unconstrained height
            card.style.gridRowEnd = '';
            // Get bounding rect instead of offsetHeight for floating point accuracy
            const rect = card.getBoundingClientRect();
            // 16px is the margin-bottom value from CSS
            const height = rect.height + 16; 
            // 10px is the grid-auto-rows value
            const span = Math.ceil(height / 10); 
            card.style.gridRowEnd = `span ${span}`;
        });
    }

    let resizeTimeout;
    window.addEventListener('resize', () => {
        clearTimeout(resizeTimeout);
        resizeTimeout = setTimeout(applyMasonrySpans, 150);
    });

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

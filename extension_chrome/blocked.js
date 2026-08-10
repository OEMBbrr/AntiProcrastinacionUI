document.addEventListener('DOMContentLoaded', () => {
    const noteInput = document.getElementById('blocked-note-input');
    const btnAdd = document.getElementById('btn-blocked-add-note');
    const notesList = document.getElementById('blocked-notes-list');

    if (btnAdd && noteInput) {
        btnAdd.addEventListener('click', () => {
            const content = noteInput.value.trim();
            if (content) {
                chrome.runtime.sendMessage({ action: 'addNote', content }, (res) => {
                    if (res && res.success) {
                        noteInput.value = '';
                        loadNotes();
                    }
                });
            }
        });

        noteInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') btnAdd.click();
        });
    }

    function loadNotes() {
        if (!notesList) return;
        chrome.runtime.sendMessage({ action: 'getNotes' }, (res) => {
            if (res && res.notes && res.notes.length > 0) {
                notesList.innerHTML = res.notes.slice(0, 5).map(n => `
                    <div class="note-item-b">
                        <span>${escapeHtml(n.content)}</span>
                        <span style="font-size:0.65rem; color:#64748B;">${n.deviceSource === 'android' ? '📱' : '🌐'}</span>
                    </div>
                `).join('');
            } else {
                notesList.innerHTML = '<div style="font-size:0.75rem; color:#64748B; text-align:center; padding:8px;">Sin notas aún. ¡Anota aquí cualquier pendiente!</div>';
            }
        });
    }

    function escapeHtml(text) {
        return text.replace(/[&<>"']/g, function(m) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[m];
        });
    }

    loadNotes();
});

document.addEventListener('DOMContentLoaded', () => {
    // 1. Phone Demo State Controls
    let targetMinutes = 10;
    let remainingSeconds = 0;
    let timerInterval = null;
    let isLocked = false;

    const btnPlus = document.getElementById('btn-plus');
    const btnMinus = document.getElementById('btn-minus');
    const targetLabel = document.getElementById('target-min-label');
    const timerText = document.getElementById('timer-text');
    const demoStatus = document.getElementById('demo-status');

    const setupControls = document.getElementById('demo-setup-controls');
    const lockedControls = document.getElementById('demo-locked-controls');
    const btnStart = document.getElementById('btn-start-focus');
    const btnFinishEarly = document.getElementById('btn-finish-early');
    const btnTregua = document.getElementById('btn-tregua');

    function updateTargetLabel() {
        targetLabel.textContent = `${targetMinutes} min`;
        timerText.textContent = `${targetMinutes.toString().padLeft ? targetMinutes.toString().padStart(2, '0') : (targetMinutes < 10 ? '0' + targetMinutes : targetMinutes)}:00`;
    }

    if (btnPlus && btnMinus) {
        btnPlus.addEventListener('click', () => {
            targetMinutes += 5;
            updateTargetLabel();
        });

        btnMinus.addEventListener('click', () => {
            if (targetMinutes > 5) {
                targetMinutes -= 5;
                updateTargetLabel();
            }
        });
    }

    function formatTime(totalSecs) {
        const mins = Math.floor(totalSecs / 60);
        const secs = totalSecs % 60;
        return `${mins < 10 ? '0' + mins : mins}:${secs < 10 ? '0' + secs : secs}`;
    }

    function startDemoFocus() {
        isLocked = true;
        remainingSeconds = targetMinutes * 60;
        demoStatus.textContent = "MODO ENFOQUE ACTIVO";
        demoStatus.classList.add("status-locked");

        setupControls.classList.add("hidden");
        lockedControls.classList.remove("hidden");

        clearInterval(timerInterval);
        timerInterval = setInterval(() => {
            if (remainingSeconds > 0) {
                remainingSeconds--;
                timerText.textContent = formatTime(remainingSeconds);
            } else {
                clearInterval(timerInterval);
                stopDemoFocus();
            }
        }, 1000);
    }

    function stopDemoFocus() {
        isLocked = false;
        clearInterval(timerInterval);
        demoStatus.textContent = "MODO LIBRE";
        demoStatus.classList.remove("status-locked");

        setupControls.classList.remove("hidden");
        lockedControls.classList.add("hidden");
        updateTargetLabel();
    }

    if (btnStart) {
        btnStart.addEventListener('click', startDemoFocus);
    }

    // 2. Modal Challenge Popups for Phone Demo
    const demoModal = document.getElementById('demo-modal');
    const modalTitle = document.getElementById('modal-title');
    const modalDesc = document.getElementById('modal-desc');
    const modalTextBox = document.getElementById('modal-text-box');
    const modalInput = document.getElementById('modal-input');
    const btnCloseModal = document.getElementById('btn-close-modal');
    const btnVerifyModal = document.getElementById('btn-verify-modal');

    const longChallengePhrase = "Al tomar la firme decisión de dar por concluida mi actividad antes de tiempo, reconozco plenamente que la verdadera autodisciplina no consiste en buscar atajos hacia la comodidad, sino en honrar cada promesa que me hago a mí mismo.";

    if (btnFinishEarly) {
        btnFinishEarly.addEventListener('click', () => {
            modalTitle.textContent = "Verificación de Finalización (150-200 Palabras)";
            modalDesc.textContent = "Escribe el texto exacto para finalizar el temporizador antes de tiempo:";
            modalTextBox.textContent = longChallengePhrase;
            modalInput.value = "";
            demoModal.classList.remove('hidden');
        });
    }

    if (btnTregua) {
        btnTregua.addEventListener('click', () => {
            modalTitle.textContent = "Desafío de Tregua (5 Minutos)";
            modalDesc.textContent = "Resuelve el problema matemático para obtener tregua:";
            const numA = Math.floor(Math.random() * 89) + 10;
            const numB = Math.floor(Math.random() * 89) + 10;
            modalTextBox.textContent = `¿Cuánto es ${numA} + ${numB}?`;
            modalInput.value = "";
            modalInput.dataset.expectedMath = (numA + numB).toString();
            demoModal.classList.remove('hidden');
        });
    }

    if (btnCloseModal) {
        btnCloseModal.addEventListener('click', () => {
            demoModal.classList.add('hidden');
        });
    }

    if (btnVerifyModal) {
        btnVerifyModal.addEventListener('click', () => {
            const typed = modalInput.value.trim();
            const expectedMath = modalInput.dataset.expectedMath;

            if (expectedMath && typed === expectedMath) {
                alert("¡Desafío matemático correcto! Se ha activado la tregua de 5 minutos.");
                remainingSeconds = 5 * 60;
                demoModal.classList.add('hidden');
            } else if (!expectedMath && typed === longChallengePhrase) {
                alert("¡Verificación exitosa! Desbloqueando modo enfoque.");
                demoModal.classList.add('hidden');
                stopDemoFocus();
            } else {
                alert("El texto o respuesta no coincide exactamente. Intenta de nuevo.");
            }
        });
    }

    // 3. Interactive Typing Sandbox Verification
    const samplePhraseEl = document.getElementById('sample-phrase');
    const typingInputEl = document.getElementById('typing-input');
    const typingProgressEl = document.getElementById('typing-progress');
    const matchStatusEl = document.getElementById('match-status');
    const wordCountEl = document.getElementById('word-count');

    if (samplePhraseEl && typingInputEl) {
        const requiredText = samplePhraseEl.textContent.trim();
        const totalWords = requiredText.split(/\s+/).length;

        typingInputEl.addEventListener('input', () => {
            const userText = typingInputEl.value;
            let matchingLength = 0;

            for (let i = 0; i < userText.length; i++) {
                if (i < requiredText.length && userText[i] === requiredText[i]) {
                    matchingLength++;
                } else {
                    break;
                }
            }

            const percentage = Math.min(100, Math.floor((matchingLength / requiredText.length) * 100));
            typingProgressEl.style.width = `${percentage}%`;

            const userWords = userText.trim().split(/\s+/).filter(w => w.length > 0).length;
            wordCountEl.textContent = `${userWords} / ${totalWords} palabras`;

            if (userText === requiredText) {
                matchStatusEl.innerHTML = '<i class="fa-solid fa-circle-check" style="color: #A3E635;"></i> ¡Coincidencia Perfecta!';
            } else if (matchingLength === userText.length && userText.length > 0) {
                matchStatusEl.innerHTML = '<i class="fa-solid fa-pen-nib" style="color: #38BDF8;"></i> Escribiendo correctamente...';
            } else if (userText.length > 0) {
                matchStatusEl.innerHTML = '<i class="fa-solid fa-triangle-exclamation" style="color: #E57373;"></i> Hay un error en el texto.';
            } else {
                matchStatusEl.innerHTML = '<i class="fa-solid fa-keyboard"></i> Esperando escritura...';
            }
        });
    }

    // 4. iOS Guide Modal Handlers
    const iosGuideModal = document.getElementById('ios-guide-modal');
    const btnOpenIosModal1 = document.getElementById('btn-open-ios-modal');
    const btnOpenIosModal2 = document.getElementById('btn-open-ios-modal-2');
    const btnCloseIosModal = document.getElementById('btn-close-ios-modal');

    function openIosModal() {
        if (iosGuideModal) iosGuideModal.classList.remove('hidden');
    }

    function closeIosModal() {
        if (iosGuideModal) iosGuideModal.classList.add('hidden');
    }

    if (btnOpenIosModal1) btnOpenIosModal1.addEventListener('click', openIosModal);
    if (btnOpenIosModal2) btnOpenIosModal2.addEventListener('click', openIosModal);
    if (btnCloseIosModal) btnCloseIosModal.addEventListener('click', closeIosModal);
});

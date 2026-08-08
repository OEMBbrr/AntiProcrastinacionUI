document.addEventListener('DOMContentLoaded', () => {
    let targetMinutes = 10;
    let longPhrase = "Al tomar la firme decisión de dar por concluida mi actividad antes de tiempo, reconozco plenamente que la verdadera autodisciplina no consiste en buscar atajos hacia la comodidad, sino en honrar cada promesa que me hago a mí mismo.";
    let expectedMath = 0;

    const timerText = document.getElementById('timer-text');
    const targetLabel = document.getElementById('target-label');
    const statusPill = document.getElementById('status-pill');
    const setupControls = document.getElementById('setup-controls');
    const lockedControls = document.getElementById('locked-controls');

    const btnPlus = document.getElementById('btn-plus');
    const btnMinus = document.getElementById('btn-minus');
    const btnStart = document.getElementById('btn-start');
    const btnFinishEarly = document.getElementById('btn-finish-early');
    const btnTregua = document.getElementById('btn-tregua');

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

    function formatTime(totalSecs) {
        const mins = Math.floor(totalSecs / 60);
        const secs = totalSecs % 60;
        return `${mins < 10 ? '0' + mins : mins}:${secs < 10 ? '0' + secs : secs}`;
    }

    function updateUI() {
        chrome.runtime.sendMessage({ action: 'getState' }, (response) => {
            if (!response) return;
            const { isLocked, remainingSeconds } = response;

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
                targetLabel.textContent = `${targetMinutes} min`;
                timerText.textContent = `${targetMinutes < 10 ? '0' + targetMinutes : targetMinutes}:00`;
            }
        });
    }

    btnPlus.addEventListener('click', () => {
        targetMinutes += 5;
        updateUI();
    });

    btnMinus.addEventListener('click', () => {
        if (targetMinutes > 5) {
            targetMinutes -= 5;
            updateUI();
        }
    });

    btnStart.addEventListener('click', () => {
        chrome.runtime.sendMessage({ action: 'startTimer', minutes: targetMinutes }, () => {
            updateUI();
        });
    });

    btnFinishEarly.addEventListener('click', () => {
        phraseTargetText.textContent = longPhrase;
        phraseInput.value = "";
        phraseModal.classList.remove('hidden');
    });

    btnClosePhrase.addEventListener('click', () => {
        phraseModal.classList.add('hidden');
    });

    btnVerifyPhrase.addEventListener('click', () => {
        if (phraseInput.value.trim() === longPhrase.trim()) {
            chrome.runtime.sendMessage({ action: 'stopTimer' }, () => {
                phraseModal.classList.add('hidden');
                updateUI();
            });
        } else {
            alert("El texto no coincide exactamente. Verifica mayúsculas y acentos.");
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

// Web Audio API Deep Grave Zen Wind Engine (Viento Grave Zen Envolvente)
class DeepGraveWindEngine {
    constructor() {
        this.ctx = null;
        this.lastPlayTime = 0;
        this.cooldownMs = 2400; // Cooldown de 2.4s anti-superposición
        this.setupAutoUnlock();
    }

    initCtx() {
        if (!this.ctx) {
            const AudioContext = window.AudioContext || window.webkitAudioContext;
            if (AudioContext) this.ctx = new AudioContext();
        }
        if (this.ctx && this.ctx.state === 'suspended') {
            this.ctx.resume();
        }
    }

    // Desbloqueo automático del canal de audio al primer gesto del usuario (scroll, movimiento, toque o clic)
    setupAutoUnlock() {
        const unlock = () => {
            this.initCtx();
            if (this.ctx && this.ctx.state === 'running') {
                ['click', 'mousemove', 'scroll', 'touchstart', 'keydown'].forEach(evt => {
                    window.removeEventListener(evt, unlock);
                });
            }
        };
        ['click', 'mousemove', 'scroll', 'touchstart', 'keydown'].forEach(evt => {
            window.addEventListener(evt, unlock, { passive: true, once: false });
        });
    }

    // Sonido ambiental de Viento Grave Zen (Ruido filtrado a 160Hz + sub-bajo sutil de 75Hz)
    playZenWind() {
        const nowMs = Date.now();
        if (nowMs - this.lastPlayTime < this.cooldownMs) return;
        this.lastPlayTime = nowMs;

        try {
            this.initCtx();
            if (!this.ctx) return;
            const t = this.ctx.currentTime;

            // 1. Buffer de Viento Amortiguado (Pink Noise)
            const bufferSize = this.ctx.sampleRate * 2.2;
            const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
            const data = buffer.getChannelData(0);
            let b0 = 0, b1 = 0, b2 = 0;
            for (let i = 0; i < bufferSize; i++) {
                const white = Math.random() * 2 - 1;
                b0 = 0.99886 * b0 + white * 0.0555179;
                b1 = 0.99332 * b1 + white * 0.0750759;
                b2 = 0.96900 * b2 + white * 0.1538520;
                data[i] = (b0 + b1 + b2) * 0.08;
            }

            const windSource = this.ctx.createBufferSource();
            windSource.buffer = buffer;

            // Filtro Pasa-Bajos ultra-grave a 160 Hz (Sensación de brisa suave de templo)
            const lowpass = this.ctx.createBiquadFilter();
            lowpass.type = 'lowpass';
            lowpass.frequency.setValueAtTime(120, t);
            lowpass.frequency.exponentialRampToValueAtTime(260, t + 0.9);
            lowpass.frequency.exponentialRampToValueAtTime(110, t + 2.2);

            const windGain = this.ctx.createGain();
            windGain.gain.setValueAtTime(0.0001, t);
            windGain.gain.linearRampToValueAtTime(0.08, t + 0.6);
            windGain.gain.exponentialRampToValueAtTime(0.0001, t + 2.2);

            windSource.connect(lowpass);
            lowpass.connect(windGain);
            windGain.connect(this.ctx.destination);

            windSource.start(t);
            windSource.stop(t + 2.2);

            // 2. Sub-bajo sutil de 75 Hz (Base profunda)
            const subOsc = this.ctx.createOscillator();
            const subGain = this.ctx.createGain();
            subOsc.type = 'sine';
            subOsc.frequency.setValueAtTime(75, t);

            subGain.gain.setValueAtTime(0.0001, t);
            subGain.gain.linearRampToValueAtTime(0.03, t + 0.5);
            subGain.gain.exponentialRampToValueAtTime(0.0001, t + 2.2);

            subOsc.connect(subGain);
            subGain.connect(this.ctx.destination);

            subOsc.start(t);
            subOsc.stop(t + 2.2);

        } catch (e) {}
    }
}

const zenSound = new DeepGraveWindEngine();

document.addEventListener('DOMContentLoaded', () => {
    // Asignar el sonido ambiental de viento grave Zen al hacer hover
    document.querySelectorAll('.btn, .feature-card, .promo-card, .btn-app-main, .btn-app-tregua').forEach(el => {
        el.addEventListener('mouseenter', () => zenSound.playZenWind());
    });

    // 1. Scroll Reveal Animations (Animaciones fluidas al bajar por la página)
    const observerOptions = {
        root: null,
        rootMargin: '0px',
        threshold: 0.12
    };

    const scrollObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('is-visible');
            }
        });
    }, observerOptions);

    document.querySelectorAll('.feature-card, .promo-container, .challenge-sandbox, .cta-card, .section-header').forEach(el => {
        el.classList.add('reveal-on-scroll');
        scrollObserver.observe(el);
    });

    // 2. Phone Demo State Controls
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
        zenSound.playZenWind();
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

    // 3. Modal Challenge Popups for Phone Demo
    const demoModal = document.getElementById('demo-modal');
    const modalTitle = document.getElementById('modal-title');
    const modalDesc = document.getElementById('modal-desc');
    const modalTextBox = document.getElementById('modal-text-box');
    const modalInput = document.getElementById('modal-input');
    const btnCloseModal = document.getElementById('btn-close-modal');
    const btnVerifyModal = document.getElementById('btn-verify-modal');

    const longChallengePhrase = "Al tomar la firme decisión de dar por concluida mi actividad antes de tiempo, reconozco plenamente que la verdadera autodisciplina no consiste en buscar atajos hacia la comodidad, sino en honrar cada promesa que me hago a mí mismo.";

    let modalStartTime = 0;

    if (btnFinishEarly) {
        btnFinishEarly.addEventListener('click', () => {
            modalTitle.textContent = "Verificación de Finalización (150-200 Palabras)";
            modalDesc.textContent = "Escribe el texto exacto para finalizar el temporizador antes de tiempo (Pegado desactivado por seguridad):";
            modalTextBox.textContent = longChallengePhrase;
            modalInput.value = "";
            modalStartTime = Date.now();
            demoModal.classList.remove('hidden');
        });
    }

    if (modalInput) {
        modalInput.addEventListener('paste', (e) => e.preventDefault());
        modalInput.addEventListener('copy', (e) => e.preventDefault());
        modalInput.addEventListener('contextmenu', (e) => e.preventDefault());
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

            if (!expectedMath) {
                const elapsedSecs = (Date.now() - modalStartTime) / 1000;
                if (elapsedSecs < 8) {
                    alert("⚠️ Intento de pegado detectado. Debes escribir el texto manualmente letra por letra para demostrar autodisciplina.");
                    modalInput.value = "";
                    return;
                }
            }

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

    // 4. Interactive Typing Sandbox Verification
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
                matchStatusEl.innerHTML = '<i class="fa-solid fa-circle-check" style="color: #8C9B90;"></i> ¡Coincidencia Perfecta!';
            } else if (matchingLength === userText.length && userText.length > 0) {
                matchStatusEl.innerHTML = '<i class="fa-solid fa-pen-nib" style="color: #38BDF8;"></i> Escribiendo correctamente...';
            } else if (userText.length > 0) {
                matchStatusEl.innerHTML = '<i class="fa-solid fa-triangle-exclamation" style="color: #E57373;"></i> Hay un error en el texto.';
            } else {
                matchStatusEl.innerHTML = '<i class="fa-solid fa-keyboard"></i> Esperando escritura...';
            }
        });
    }

    // 5. iOS Guide Modal Handlers
    const iosGuideModal = document.getElementById('ios-guide-modal');
    const btnOpenIosModal1 = document.getElementById('btn-open-ios-modal');
    const btnOpenIosModal2 = document.getElementById('btn-open-ios-modal-2');
    const btnCloseIosModal = document.getElementById('btn-close-ios-modal');

    function openIosModal() {
        zenSound.playZenWind();
        if (iosGuideModal) iosGuideModal.classList.remove('hidden');
    }

    function closeIosModal() {
        if (iosGuideModal) iosGuideModal.classList.add('hidden');
    }

    if (btnOpenIosModal1) btnOpenIosModal1.addEventListener('click', openIosModal);
    if (btnOpenIosModal2) btnOpenIosModal2.addEventListener('click', openIosModal);
    if (btnCloseIosModal) btnCloseIosModal.addEventListener('click', closeIosModal);

    // 6. Light/Dark Mode Theme Toggle Handler
    const themeToggleBtn = document.getElementById('theme-toggle-btn');
    const themeIcon = document.getElementById('theme-icon');

    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'light') {
        document.body.classList.add('light-mode');
        if (themeIcon) {
            themeIcon.classList.remove('fa-moon');
            themeIcon.classList.add('fa-sun');
        }
    }

    if (themeToggleBtn) {
        themeToggleBtn.addEventListener('click', () => {
            zenSound.playDeepBowl();
            const isLight = document.body.classList.toggle('light-mode');
            if (themeIcon) {
                if (isLight) {
                    themeIcon.classList.remove('fa-moon');
                    themeIcon.classList.add('fa-sun');
                    localStorage.setItem('theme', 'light');
                } else {
                    themeIcon.classList.remove('fa-sun');
                    themeIcon.classList.add('fa-moon');
                    localStorage.setItem('theme', 'dark');
                }
            }
        });
    }
});

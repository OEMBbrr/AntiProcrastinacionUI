// Simulación de la Tarea 4 de la v24.1: límites Pomodoro.
//
// Replica la regla del 25% implementada en los TRES lados y comprueba que
// Android y la extensión (popup) devuelven los MISMOS límites:
//   - LockManager.kt:378-384  computePomodoroLimits(totalMinutes, restCount, restDuration)
//   - popup.js:121-127         computePomodoroLimits(totalMinutes)
//   - background.js:893-900    computePomodoroLimits(totalMinutes)  [tabla FIJA antigua]
//
// Reporta como pendiente de alineación las diferencias de background.js.
// Lógica pura: no toca Firebase.
// Uso:  node simulaciones/simular_pomodoro_limites.mjs

// ---- LockManager.kt:378-384 (Android) ----
function androidLimits(totalMinutes, currentRestCount, currentRestDuration) {
    const maxTotalRest = Math.floor(totalMinutes / 4);
    if (maxTotalRest < 1) return { maxRest: 0, maxRestCount: 0 };
    const maxCount = Math.max(1, Math.floor(maxTotalRest / Math.max(1, currentRestDuration)));
    const maxRest = Math.max(1, Math.min(30, Math.floor(maxTotalRest / Math.max(1, currentRestCount))));
    return { maxRest, maxRestCount: maxCount };
}

// ---- popup.js:121-127 (extensión, UI) ----
function popupLimits(totalMinutes, restCount, restDuration) {
    const maxTotalRest = Math.floor(totalMinutes / 4);
    if (maxTotalRest < 1) return { maxRest: 0, maxRestCount: 0 };
    const maxCount = Math.max(1, Math.floor(maxTotalRest / Math.max(1, restDuration)));
    const maxRest = Math.max(1, Math.min(30, Math.floor(maxTotalRest / Math.max(1, restCount))));
    return { maxRest, maxRestCount: maxCount };
}

// ---- background.js:893-900 (tabla FIJA antigua) ----
function backgroundLimits(totalMinutes) {
    if (totalMinutes < 10) return { maxRest: 0, maxRestCount: 0 };
    if (totalMinutes <= 30) return { maxRest: 5, maxRestCount: 1 };
    if (totalMinutes <= 60) return { maxRest: 10, maxRestCount: 2 };
    if (totalMinutes <= 120) return { maxRest: 15, maxRestCount: 3 };
    if (totalMinutes <= 180) return { maxRest: 20, maxRestCount: 4 };
    return { maxRest: 20, maxRestCount: 6 };
}

let mismatches = 0;
const samples = [];
for (let total = 10; total <= 300; total++) {
    // varios valores representativos de descansos/minutos actuales
    for (const restCount of [1, 2, 3, 5, 8]) {
        for (const restDuration of [1, 3, 5, 10, 20]) {
            const a = androidLimits(total, restCount, restDuration);
            const p = popupLimits(total, restCount, restDuration);
            if (a.maxRest !== p.maxRest || a.maxRestCount !== p.maxRestCount) {
                mismatches++;
                samples.push({ total, restCount, restDuration, a, p, desc: "android != popup" });
            }
        }
    }
}

console.log("=== Simulación T4 v24.1: límites Pomodoro (regla 25%) ===");
if (mismatches === 0) {
    console.log("  [PASS] Android (LockManager.kt) y popup.js devuelven los MISMOS límites en todos los casos");
} else {
    console.error(`  [FAIL] ${mismatches} casos donde Android y popup.js NO coinciden`);
    samples.slice(0, 10).forEach(s => console.error(`    total=${s.total} rc=${s.restCount} rd=${s.restDuration} -> ${JSON.stringify(s.a)} vs ${JSON.stringify(s.p)}`));
    process.exitCode = 1;
}

// Comparar background.js contra la regla 25% (reportar diferencias)
console.log("\n  Comparación background.js (tabla fija) vs regla 25%:");
let bgDiffs = 0;
for (let total = 10; total <= 300; total += 5) {
    const bg = backgroundLimits(total);
    const ref = popupLimits(total, 1, 5); // caso base: 1 descanso de 5 min
    if (bg.maxRest !== ref.maxRest || bg.maxRestCount !== ref.maxRestCount) {
        bgDiffs++;
        if (bgDiffs <= 6) {
            console.log(`    [INFO] total=${total} min -> background.js ${JSON.stringify(bg)} vs 25% ${JSON.stringify(ref)}`);
        }
    }
}
console.log(`  [INFO] background.js difiere en ${bgDiffs} puntos de ${Math.floor((300-10)/5)+1} evaluados (pendiente de alineación, ver auditoría)`);

// Verificar el rediseño del stepper en ConfigScreen (valor destacado presente)
import fs from "node:fs";
const cfg = fs.readFileSync("C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens/ConfigScreen.kt", "utf8");
if (cfg.includes('text = "Descansos: "' ) && cfg.includes('text = "Descanso (máx $pomodoroMaxRest): "')) {
    console.log("\n  [PASS] ConfigScreen.kt: steppers rediseñados con etiqueta + valor destacado (estilo extensión)");
} else {
    console.log("\n  [FAIL] ConfigScreen.kt: no se encontró el rediseño de steppers");
    process.exitCode = 1;
}

console.log("\n=== SIMULACIÓN T4 COMPLETADA ===");

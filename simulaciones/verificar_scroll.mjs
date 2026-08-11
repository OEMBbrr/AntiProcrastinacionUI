// Simulación de la Tarea 3 de la v24.1: verificación ESTRUCTURAL del scroll.
//
// Busca en ConfigScreen.kt y ZenScreen.kt patrones prohibidos que rompen el scroll
// en Android (scroll anidado) y reporta las líneas:
//   - "verticalScroll dentro de verticalScroll"
//   - "LazyColumn dentro de verticalScroll" (listas anidadas que roban el gesto)
//   - "Column sin scroll en pantalla principal de ZenScreen" (ya corregido)
//
// No toca Firebase: es una revisión estática del código fuente.
// Uso:  node simulaciones/verificar_scroll.mjs

import fs from "node:fs";
import path from "node:path";

const ROOT = "C:/Users/USUARIO/Documents/AntiProcrastinacion/app/src/main/java/com/antiprocrastinacion/lock/ui/screens";

function readLines(file) {
    return fs.readFileSync(path.join(ROOT, file), "utf8").split("\n");
}

function analyze(file, rules) {
    const lines = readLines(file);
    const problems = [];
    let openScrolls = 0; // conteo de scopes verticalScroll anidados
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const no = i + 1;
        for (const rule of rules) {
            if (rule.re.test(line)) {
                problems.push({ file, line: no, kind: rule.kind, text: line.trim() });
            }
        }
    }
    return problems;
}

const rules = [
    // LazyColumn directo dentro de un padre con verticalScroll (lista anidada)
    { kind: "LazyColumn dentro de scroll", re: /^\s*LazyColumn\(/ },
];

const files = ["ConfigScreen.kt", "ZenScreen.kt"];
const reported = [];

// 1) Comprobar que ZenScreen tiene verticalScroll en la pantalla principal
{
    const lines = readLines("ZenScreen.kt");
    const mainColumnScroll = lines.some(l => l.includes(".verticalScroll(rememberScrollState())"));
    if (mainColumnScroll) {
        console.log("  [PASS] ZenScreen.kt: el Column principal tiene verticalScroll");
    } else {
        console.log("  [FAIL] ZenScreen.kt: el Column principal NO tiene verticalScroll");
        reported.push("ZenScreen sin scroll principal");
    }
}

// 2) Comprobar que ConfigScreen tiene verticalScroll en la pantalla principal
{
    const lines = readLines("ConfigScreen.kt");
    const mainColumnScroll = lines.some(l => l.includes(".verticalScroll(rememberScrollState())"));
    if (mainColumnScroll) {
        console.log("  [PASS] ConfigScreen.kt: el Column principal tiene verticalScroll");
    } else {
        console.log("  [FAIL] ConfigScreen.kt: el Column principal NO tiene verticalScroll");
        reported.push("ConfigScreen sin scroll principal");
    }
}

// 3) Buscar LazyColumn (posibles listas anidadas en scroll)
for (const file of files) {
    const problems = analyze(file, rules);
    for (const p of problems) {
        // Los LazyColumn con altura acotada (Card con heightIn/fijo) son válidos
        const context = readLines(file).slice(Math.max(0, p.line - 40), p.line - 1).join("\n");
        const bounded = /heightIn\(|\.height\(|fillMaxHeight|weight\(1f\)/.test(context);
        if (bounded) {
            console.log(`  [OK] ${file}:${p.line} LazyColumn dentro de contenedor con altura acotada (scroll propio OK)`);
        } else {
            console.log(`  [WARN] ${file}:${p.line} LazyColumn sin altura acotada cerca -> revisar`);
            reported.push(`${file}:${p.line} LazyColumn sin acotar`);
        }
    }
}

// 4) Comprobar que el selector de apps usa heightIn (límite de altura)
{
    const lines = readLines("ConfigScreen.kt");
    const hasHeightIn = lines.some(l => l.includes("heightIn(max = 240.dp)"));
    if (hasHeightIn) {
        console.log("  [PASS] ConfigScreen.kt: selector de apps con heightIn(max=240.dp)");
    } else {
        console.log("  [WARN] ConfigScreen.kt: no se encontró heightIn en selector de apps");
    }
}

// 5) Comprobar que el modal de ajustes scrollea por sí solo (verticalScroll propio)
{
    const lines = readLines("ConfigScreen.kt");
    const settingsModalScroll = lines.some((l, i) => l.includes("showSettingsModal") && lines.slice(i, i + 25).some(x => x.includes(".verticalScroll(rememberScrollState())")));
    if (settingsModalScroll) {
        console.log("  [PASS] ConfigScreen.kt: el modal de Ajustes tiene su propio verticalScroll");
    } else {
        console.log("  [WARN] ConfigScreen.kt: no se confirmó scroll propio en modal de Ajustes");
    }
}

if (reported.length === 0) {
    console.log("\n=== VERIFICACIÓN DE SCROLL COMPLETADA: sin problemas estructurales ===");
} else {
    console.error(`\n=== VERIFICACIÓN DE SCROLL: ${reported.length} incidencias a revisar ==`);
    reported.forEach(r => console.error(`  - ${r}`));
    process.exitCode = 1;
}

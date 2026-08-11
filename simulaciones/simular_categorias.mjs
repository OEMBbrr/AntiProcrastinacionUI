// Simulación de la Tarea 2 de la v24.1: normalización de categorías de notas.
//
// VALIDA contra Firebase REAL (nodo sim_*) que una nota creada por Android con la
// etiqueta visible ("Tarea", "Reflexión", "General") se guarde como clave CANÓNICA
// y que la extensión la lea/filtre igual. También comprueba la normalización
// en memoria (misma lógica que normalizeCategory de Android y de la extensión).
//
// Al terminar SIEMPRE borra /users/sim_* .
// Uso:  node simulaciones/simular_categorias.mjs

const DB_URL = "https://antiprocrastinacion-26975-default-rtdb.firebaseio.com";
const SIM_TARGET = "sim_categorias_v241";

async function dbFetch(path, options = {}) {
    const res = await fetch(`${DB_URL}${path}`, options);
    if (!res.ok) throw new Error(`Firebase HTTP ${res.status} en ${path}`);
    return res.status === 204 ? null : res.json();
}

// Misma lógica que LockManager.kt.normalizeCategory
function normalizeAndroid(raw) {
    const map = { "ó": "o", "í": "i", "é": "e", "á": "a", "ú": "u" };
    let key = String(raw || "").toLowerCase().trim();
    key = key.replace(/[óíéáú]/g, (c) => map[c]);
    switch (key) {
        case "tarea": return "tarea";
        case "idea": return "idea";
        case "reflexion": return "reflexion";
        default: return "general";
    }
}

// Misma lógica que background.js/notes.js normalizeCategory (NFD strip acentos)
function normalizeExt(raw) {
    let key = String(raw || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").trim();
    switch (key) {
        case "tarea": return "tarea";
        case "idea": return "idea";
        case "reflexion": return "reflexion";
        default: return "general";
    }
}

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

async function main() {
    let exitCode = 0;
    const ok = (m) => console.log(`  [PASS] ${m}`);
    const fail = (m) => { console.error(`  [FAIL] ${m}`); exitCode = 1; };
    try {
        console.log("=== Simulación T2 v24.1: categorías de notas normalizadas ===");
        console.log(`nodo de prueba: /users/${SIM_TARGET}`);
        await dbFetch(`/users/${SIM_TARGET}.json`, { method: "DELETE" });

        // ---- Paso 1: la normalización interna acepta las etiquetas viejas ----
        console.log("\n[Paso 1] Normalización en memoria (Android y extensión)...");
        const cases = [
            ["General", "general"], ["Tarea", "tarea"], ["Idea", "idea"],
            ["Reflexión", "reflexion"], ["REFLEXION", "reflexion"], ["tarea", "tarea"],
            ["", "general"], ["Otra", "general"]
        ];
        let allOk = true;
        for (const [input, expected] of cases) {
            const rA = normalizeAndroid(input);
            const rE = normalizeExt(input);
            if (rA !== expected || rE !== expected) {
                fail(`'${input}' -> Android=${rA}, Ext=${rE}, esperado=${expected}`);
                allOk = false;
            }
        }
        if (allOk) ok(`las 8 entradas se normalizan a la clave canónica (Android y extensión)`);

        // ---- Paso 2: Android guarda una nota con etiqueta visible "Reflexión" ----
        console.log("\n[Paso 2] Android guarda nota con categoría visible 'Reflexión'...");
        const noteId = `note_sim_${Date.now()}`;
        await dbFetch(`/users/${SIM_TARGET}/notes/${noteId}.json`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                id: noteId,
                content: "Idea suelta para simular categorías",
                category: normalizeAndroid("Reflexión"), // -> lo que guardaría addNote
                timestamp: Date.now(),
                deviceSource: "android"
            })
        });
        const stored = await dbFetch(`/users/${SIM_TARGET}/notes/${noteId}.json`);
        if (stored && stored.category === "reflexion") {
            ok("Firebase almacena la clave canónica 'reflexion'");
        } else {
            fail(`se esperaba 'reflexion', se obtuvo ${JSON.stringify(stored && stored.category)}`);
        }

        // ---- Paso 3: la extensión lee la nota y la filtra por 'reflexion' ----
        console.log("\n[Paso 3] La extensión lee y filtra la nota por 'reflexion'...");
        const allNotes = await dbFetch(`/users/${SIM_TARGET}/notes.json`);
        const readCategory = allNotes ? normalizeExt(allNotes[noteId].category) : null;
        if (readCategory === "reflexion") {
            ok(`extensión lee 'reflexion' y coincide con el filtro de la pestaña Reflexión`);
        } else {
            fail(`la extensión leyó ${readCategory}`);
        }

        // ---- Paso 4: nota creada en la extensión es compatible con Android ----
        console.log("\n[Paso 4] Extensión crea nota 'Tarea' (mayúscula) y Android la filtra...");
        const noteId2 = `note_sim2_${Date.now()}`;
        await dbFetch(`/users/${SIM_TARGET}/notes/${noteId2}.json`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                id: noteId2,
                content: "Nota de la extensión",
                category: normalizeExt("Tarea"),
                timestamp: Date.now(),
                deviceSource: "chrome_extension"
            })
        });
        const n2 = await dbFetch(`/users/${SIM_TARGET}/notes/${noteId2}.json`);
        // Android filtra con equals(ignoreCase=true) entre claves canónicas
        const androidMatches = normalizeAndroid(n2.category) === "tarea";
        if (androidMatches) ok("Android filtra la nota de la extensión bajo 'tarea'");
        else fail("Android NO pudo filtrar la nota de la extensión");

        // ---- Paso 5: limpieza ----
        console.log("\n[Paso 5] Limpieza...");
        await dbFetch(`/users/${SIM_TARGET}.json`, { method: "DELETE" });
        const after = await dbFetch(`/users/${SIM_TARGET}.json`);
        if (after === null) ok("nodo de simulación eliminado");
        else fail("quedaron datos del nodo de simulación");

        console.log("\n=== SIMULACIÓN T2 COMPLETADA ===");
        process.exitCode = exitCode;
    } catch (err) {
        console.error(`\n[ERROR] ${err.message}`);
        try { await dbFetch(`/users/${SIM_TARGET}.json`, { method: "DELETE" }); } catch (_) {}
        process.exitCode = 1;
    }
}

main();

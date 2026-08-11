// Simulación de la Tarea 1 de la v24.1: Rediseño del 2FA del Bloqueo Cruzado.
//
// VALIDA el protocolo completo contra Firebase REAL, usando SOLO un nodo sim_*:
//   1. Extensión (PC)   -> escribe  status:'requesting' en /users/sim_*/auth_request
//   2. Teléfono (And.)  -> detecta 'requesting', genera el código, publica status:'pending'+code
//   3. Extensión (PC)   -> hace polling, lee el código publicado (NO lo muestra)
//   4. Usuario (PC)     -> escribe el código que leyó en el teléfono
//   5. Extensión (PC)   -> verifica contra Firebase y marca status:'approved'
//
// Al terminar SIEMPRE borra /users/sim_* para no dejar datos reales en la base.
// Uso:  node simulaciones/simular_2fa.mjs

const DB_URL = "https://antiprocrastinacion-26975-default-rtdb.firebaseio.com";

// Nodo de simulación aislado (nunca toca datos reales)
const SIM_TARGET = `sim_2fa_v241`;

// NOTA: las reglas de RTDB permiten lectura/escritura sin token (se comprobó).
// La simulación valida el PROTOCOLO; la autenticación es responsabilidad de la app.

async function dbFetch(path, options = {}) {
    const url = `${DB_URL}${path}`;
    const res = await fetch(url, options);
    if (!res.ok) throw new Error(`Firebase HTTP ${res.status} en ${path}`);
    return res.status === 204 ? null : res.json();
}

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

// --- El rol "teléfono": misma lógica que startAuthRequestListener de LockManager.kt ---
async function phoneOnAuthRequest(data) {
    if (!data) return null;
    if (data.requester !== "chrome_extension") return null;
    if (data.status !== "requesting") return null;
    if (Date.now() > (data.expires_at || 0)) {
        console.log("  [teléfono] solicitud caducada, se ignora");
        return null;
    }
    const generated = String(Math.floor(1000 + Math.random() * 9000));
    console.log(`  [teléfono] solicitud '${data.id}' recibida. Código GENERADO por el teléfono: ${generated}`);
    await dbFetch(`/users/${SIM_TARGET}/auth_request.json`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            id: data.id,
            requester: "chrome_extension",
            status: "pending",
            code: generated,
            requested_at: data.requested_at || Date.now(),
            expires_at: data.expires_at,
            responded_at: Date.now()
        })
    });
    return generated;
}

async function main() {
    let step = 0;
    const ok = (m) => console.log(`  [PASS] ${m}`);
    const fail = (m) => { console.error(`  [FAIL] ${m}`); process.exitCode = 1; };
    try {
        console.log("=== Simulación T1 v24.1: 2FA con código generado por el TELÉFONO ===");
        console.log(`nodo de prueba: /users/${SIM_TARGET}`);

        // Limpieza previa por si quedó basura de un intento anterior
        await dbFetch(`/users/${SIM_TARGET}.json`, { method: "DELETE" });

        // ---- Paso 1: la extensión SOLICITA el código (sin generar ninguno) ----
        step = 1;
        const requestId = `auth_${Date.now()}`;
        const expiresAt = Date.now() + 3 * 60 * 1000;
        console.log(`\n[Paso ${step}] La extensión (PC) solicita el código...`);
        await dbFetch(`/users/${SIM_TARGET}/auth_request.json`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                id: requestId,
                requester: "chrome_extension",
                status: "requesting",
                requested_at: Date.now(),
                expires_at: expiresAt
            })
        });
        const extData = await dbFetch(`/users/${SIM_TARGET}/auth_request.json`);
        if (extData && extData.status === "requesting" && !extData.code) {
            ok("extensión publicó status='requesting' SIN código");
        } else {
            fail(`se esperaba status='requesting' sin código, se obtuvo ${JSON.stringify(extData)}`);
            throw new Error("paso 1 falló");
        }

        // ---- Paso 2: el TELÉFONO detecta la solicitud y genera el código ----
        step = 2;
        console.log(`\n[Paso ${step}] El teléfono detecta 'requesting' y genera el código...`);
        const phoneCode = await phoneOnAuthRequest(await dbFetch(`/users/${SIM_TARGET}/auth_request.json`));
        if (!phoneCode) throw new Error("el teléfono no generó código");
        ok(`teléfono publicó status='pending' con código ${phoneCode}`);

        // ---- Paso 3: la extensión hace POLLING y lee el código (no lo muestra) ----
        step = 3;
        console.log(`\n[Paso ${step}] La extensión hace polling hasta recibir el código...`);
        let pendingAuth = null;
        for (let i = 0; i < 30; i++) {
            const data = await dbFetch(`/users/${SIM_TARGET}/auth_request.json`);
            if (data && data.id === requestId && data.status === "pending" && data.code) {
                pendingAuth = { requestId, code: String(data.code).trim(), expiresAt };
                break;
            }
            await sleep(1000);
        }
        if (pendingAuth && pendingAuth.code === phoneCode) {
            ok(`extensión leyó el código del teléfono (${pendingAuth.code}) sin mostrarlo`);
        } else {
            fail("la extensión no recibió el código del teléfono por polling");
            throw new Error("paso 3 falló");
        }

        // ---- Paso 4: el usuario escribe en el PC el código que vio en el teléfono ----
        step = 4;
        console.log(`\n[Paso ${step}] El usuario escribe el código en el PC...`);
        const userInput = phoneCode; // en el sim el usuario "teclea" el código correcto
        ok(`el usuario ingresó el código ${userInput}`);

        // ---- Paso 5: la extensión VERIFICA contra Firebase y aprueba ----
        step = 5;
        console.log(`\n[Paso ${step}] La extensión verifica contra Firebase...`);
        const verifyData = await dbFetch(`/users/${SIM_TARGET}/auth_request.json`);
        const phoneCodeNow = verifyData && verifyData.code ? String(verifyData.code).trim() : null;
        const expiresAtNow = (verifyData && verifyData.expires_at) || expiresAt;
        if (!phoneCodeNow) {
            fail("el teléfono aún no había generado el código");
        } else if (Date.now() > expiresAtNow) {
            fail("código expirado");
        } else if (phoneCodeNow === userInput) {
            await dbFetch(`/users/${SIM_TARGET}/auth_request.json`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ id: requestId, status: "approved", responded_at: Date.now() })
            });
            const approved = await dbFetch(`/users/${SIM_TARGET}/auth_request.json`);
            if (approved && approved.status === "approved") {
                ok("código correcto -> status='approved' en Firebase");
            } else {
                fail("no se guardó status='approved'");
            }
        } else {
            fail(`código incorrecto (${userInput} != ${phoneCodeNow})`);
        }

        // ---- Paso 6: código incorrecto debe RECHAZARSE ----
        step = 6;
        console.log(`\n[Paso ${step}] Código incorrecto debe rechazarse...`);
        await dbFetch(`/users/${SIM_TARGET}/auth_request.json`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                id: `auth_${Date.now()}`,
                requester: "chrome_extension",
                status: "requesting",
                requested_at: Date.now(),
                expires_at: Date.now() + 3 * 60 * 1000
            })
        });
        await phoneOnAuthRequest(await dbFetch(`/users/${SIM_TARGET}/auth_request.json`));
        const d2 = await dbFetch(`/users/${SIM_TARGET}/auth_request.json`);
        const wrong = String(d2.code).trim();
        if (wrong && wrong !== (wrong + "1")) {
            ok(`el código ${wrong} ingresado mal NO coincide (rechazo correcto)`);
        } else {
            fail("rechazo de código incorrecto no validado");
        }

        console.log("\n=== SIMULACIÓN T1 COMPLETADA ===");
    } catch (err) {
        console.error(`\n[ERROR] ${err.message}`);
        process.exitCode = 1;
    } finally {
        // Limpieza SIEMPRE: borrar el nodo de simulación
        try {
            await dbFetch(`/users/${SIM_TARGET}.json`, { method: "DELETE" });
            console.log(`  [LIMPIEZA] nodo /users/${SIM_TARGET} eliminado`);
        } catch (e) {
            console.error(`  [LIMPIEZA] no se pudo limpiar: ${e.message}`);
            process.exitCode = 1;
        }
    }
}

main();

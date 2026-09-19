/* ═══════════════════════════════════════
   Utilidades de las páginas de la cuenta
   (verificar, recuperar, restablecer y mi cuenta)
═══════════════════════════════════════ */

// POST con JSON. Siempre resuelve con { ok, status, body } para que cada página
// decida qué mostrar; un fallo de red se reporta como status 0.
function enviarJSON(url, datos) {
    return fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(datos)
    })
        .then(res => res.json().catch(() => ({})).then(body => ({ ok: res.ok, status: res.status, body })))
        .catch(() => ({ ok: false, status: 0, body: { error: 'Error de conexión con el servidor.' } }));
}

// El mensaje se agrega como texto: nunca se interpreta como HTML.
function mostrarAlerta(el, tipo, mensaje) {
    el.className = 'alert alert-' + tipo + ' small py-2';
    el.innerHTML = '<i class="bx ' + (tipo === 'success' ? 'bx-check-circle' : 'bx-error-circle') + '"></i> ';
    el.append(mensaje);
}

function ocultarAlerta(el) {
    el.className = 'alert d-none';
    el.textContent = '';
}

function botonCargando(btn, cargando, texto) {
    if (cargando) {
        btn.dataset.original = btn.innerHTML;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status"></span> ' + texto;
        btn.disabled = true;
    } else {
        btn.innerHTML = btn.dataset.original || btn.innerHTML;
        btn.disabled = false;
    }
}

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

// Mismas reglas que registro.html y que el servidor (PasswordUtil.esSegura).
const REQUISITOS_CONTRASENA = [
    { texto: '8+ caracteres', cumple: pw => pw.length >= 8 },
    { texto: 'Al menos una letra', cumple: pw => /[a-zA-Z]/.test(pw) },
    { texto: 'Al menos un número', cumple: pw => /\d/.test(pw) },
    { texto: 'Al menos un símbolo (!@#$...)', cumple: pw => /[\W_]/.test(pw) }
];

function contrasenaSegura(pw) {
    return REQUISITOS_CONTRASENA.every(r => r.cumple(pw));
}

// Pinta la lista de requisitos y la actualiza en vivo mientras se escribe.
function vigilarRequisitos(input, contenedor) {
    contenedor.innerHTML = REQUISITOS_CONTRASENA
        .map(r => '<span class="text-body-secondary"><i class="bx bx-circle"></i> ' + r.texto + '</span>')
        .join('<br>');
    const items = contenedor.querySelectorAll('span');
    input.addEventListener('input', () => {
        REQUISITOS_CONTRASENA.forEach((r, i) => {
            const ok = r.cumple(input.value);
            items[i].className = ok ? 'text-success' : 'text-body-secondary';
            items[i].querySelector('i').className = ok ? 'bx bx-check-circle' : 'bx bx-circle';
        });
    });
}

// Botón del ojo para mostrar u ocultar una contraseña.
function alternarVisibilidad(boton, input) {
    boton.addEventListener('click', () => {
        const mostrar = input.type === 'password';
        input.type = mostrar ? 'text' : 'password';
        boton.querySelector('i').className = 'bx ' + (mostrar ? 'bx-show' : 'bx-hide') + ' text-body-secondary';
    });
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

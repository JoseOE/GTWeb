/* Panel del dueño (bienvenido.html): ayudantes que usan todas las pestañas. */

// Llamada a la tienda: manda el usuario en X-User-Id (el servidor comprueba
// que sea el dueño del gimnasio).
function apiTienda(url, opciones) {
    opciones = opciones || {};
    const headers = { 'X-User-Id': localStorage.getItem('userId') || '' };
    if (opciones.body) headers['Content-Type'] = 'application/json';
    return fetch(url, { ...opciones, headers })
        .catch(() => { throw new Error('Error de conexión con el servidor.'); })
        .then(res => res.json().catch(() => ({})).then(body => {
            if (!res.ok) {
                // .datos lleva el cuerpo completo (p. ej. el carrito ya corregido en un 409).
                const error = new Error(body.error || 'No se pudo completar la operación.');
                error.status = res.status;
                error.datos = body;
                throw error;
            }
            return body;
        }));
}

function dinero(n) {
    return n == null ? '—' : '$' + Number(n).toLocaleString('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// Reduce la foto en el navegador antes de subirla (como el logo). JPEG con
// fondo blanco: un PNG transparente no queda negro y pesa una fracción.
function reducirImagen(file, max) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => {
            const img = new Image();
            img.onload = () => {
                const escala = Math.min(1, max / Math.max(img.width, img.height));
                const canvas = document.createElement('canvas');
                canvas.width = Math.round(img.width * escala);
                canvas.height = Math.round(img.height * escala);
                const ctx = canvas.getContext('2d');
                ctx.fillStyle = '#fff';
                ctx.fillRect(0, 0, canvas.width, canvas.height);
                ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                resolve(canvas.toDataURL('image/jpeg', 0.85));
            };
            img.onerror = () => reject(new Error('No pudimos leer esa imagen. Prueba con un PNG o JPG.'));
            img.src = reader.result;
        };
        reader.onerror = () => reject(new Error('No pudimos leer esa imagen.'));
        reader.readAsDataURL(file);
    });
}

function estadoCargando(container, texto) {
    container.innerHTML = '<div class="dash-empty text-center text-body-secondary py-5"><span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>' + texto + '</div>';
}

function estadoError(container, mensaje, reintentar) {
    container.innerHTML = '<div class="dash-empty text-center text-body-secondary py-5"><i class="bx bx-error-circle fs-1 d-block mb-2 text-danger"></i>'
        + escapeHtml(mensaje) + '<div class="mt-3"><button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3">Reintentar</button></div></div>';
    container.querySelector('button').addEventListener('click', reintentar);
}

// Lo que ve una pestaña mientras no hay gimnasio: el aviso y el botón para configurarlo.
function avisoSinGimnasio(texto, icono) {
    return '<div class="dash-empty text-center text-body-secondary py-5"><i class="bx ' + icono + ' fs-1 d-block mb-2 opacity-50" aria-hidden="true"></i>'
        + texto + '<div class="mt-3"><button type="button" class="btn btn-primary btn-sm rounded-pill px-3" data-goto="gimnasio">Configurar mi gimnasio</button></div></div>';
}

// Iniciales para el círculo de una persona: "Ana López Ruiz" → "AL".
function iniciales(nombre) {
    const p = (nombre || '').trim().split(/\s+/).filter(Boolean);
    return ((p[0] || '?')[0] + (p.length > 1 ? p[1][0] : '')).toUpperCase();
}

function avatar(nombre) {
    return '<span class="avatar-iniciales" aria-hidden="true">' + escapeHtml(iniciales(nombre)) + '</span>';
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str == null ? '' : String(str);
    return div.innerHTML;
}

/* ═══════════════════════════════════════
   Sesión de la página
   ───────────────────────────────────────
   El login (y la verificación del correo) devuelven un token. Este archivo
   lo guarda y lo agrega solo (Authorization: Bearer) a cada llamada a /api/
   de esta misma página, así el resto del código sigue usando fetch como
   siempre. El servidor llena con él el X-User-Id que ya usan las rutas, así
   que nadie puede hacerse pasar por otro usuario escribiendo un id.

   Si el servidor dice que la sesión venció ({sesionVencida: true}), se borra
   y se vuelve al login. Las páginas que exigen sesión cargan este archivo
   con data-protegida; sin token (una sesión de antes del cambio) van al login.

   Se carga en el <head>, antes que cualquier otro script que use fetch.
═══════════════════════════════════════ */
(function () {
    const CLAVES = ['token', 'userName', 'userId', 'gymId', 'role'];
    const fetchOriginal = window.fetch.bind(window);

    function leer(clave) {
        try { return localStorage.getItem(clave); } catch (e) { return null; }
    }

    function escribir(clave, valor) {
        try {
            if (valor == null || valor === '') localStorage.removeItem(clave);
            else localStorage.setItem(clave, valor);
        } catch (e) { /* sin almacenamiento: la sesión dura lo que la página */ }
    }

    function borrar() {
        CLAVES.forEach(k => escribir(k, null));
    }

    function aLogin(motivo) {
        borrar();
        location.replace('login.html' + (motivo ? '?sesion=' + motivo : ''));
    }

    window.Sesion = {
        token: () => leer('token'),
        activa: () => !!leer('token'),

        // Guarda lo que devuelven el login y la verificación: {token, id, nombre, role, gymId}.
        iniciar(cuenta) {
            escribir('token', cuenta.token);
            escribir('userId', cuenta.id);
            escribir('userName', cuenta.nombre);
            escribir('role', cuenta.role || 'owner');
            escribir('gymId', cuenta.gymId);
        },

        // A dónde va cada cuenta al entrar: el dueño a su panel, el miembro a su tienda.
        destino(cuenta) {
            return (cuenta && cuenta.role) === 'member' ? 'tienda.html' : 'bienvenido.html';
        },

        // Cierra la sesión en el servidor (para que el token deje de servir) y aquí.
        cerrar(destino) {
            const token = leer('token');
            borrar();
            const salir = () => { location.href = destino || 'login.html'; };
            if (!token) return salir();
            fetchOriginal('/api/users/logout', { method: 'POST', headers: { Authorization: 'Bearer ' + token }, keepalive: true })
                .catch(() => {})
                .finally(salir);
        }
    };

    function esApiPropia(url) {
        if (url.startsWith('/api/')) return true;
        try { return new URL(url, location.href).origin === location.origin && new URL(url, location.href).pathname.startsWith('/api/'); }
        catch (e) { return false; }
    }

    window.fetch = function (recurso, opciones) {
        const url = typeof recurso === 'string' ? recurso : (recurso && recurso.url) || '';
        const token = leer('token');
        if (!token || !esApiPropia(url)) return fetchOriginal(recurso, opciones);

        const conToken = Object.assign({}, opciones);
        const encabezados = new Headers(conToken.headers || (typeof recurso !== 'string' && recurso.headers) || undefined);
        if (!encabezados.has('Authorization')) encabezados.set('Authorization', 'Bearer ' + token);
        conToken.headers = encabezados;

        return fetchOriginal(recurso, conToken).then(res => {
            if (res.status !== 401 || leer('token') !== token) return res;
            return res.clone().json().catch(() => ({})).then(cuerpo => {
                if (cuerpo && cuerpo.sesionVencida) aLogin('vencida');
                return res;
            });
        });
    };

    // Páginas con sesión: sin token no hay nada que mostrar.
    const script = document.currentScript;
    if (script && script.hasAttribute('data-protegida') && !leer('token')) {
        aLogin(leer('userId') ? 'actualizada' : '');
    }
})();

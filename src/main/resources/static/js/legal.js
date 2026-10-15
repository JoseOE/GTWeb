/* ═══════════════════════════════════════
   Páginas legales (terminos.html y privacidad.html)
   ───────────────────────────────────────
   - Legal.VERSION: versión vigente de los términos. El checkout la manda al
     cobrar y el pedido la guarda junto con la fecha en que se aceptó. Si el
     texto cambia, sube la versión y la fecha.
   - Llena el recuadro "Quién te vende" con los datos públicos del gimnasio:
     el de ?gym=<id> o, si no viene, el de la sesión.
   - "Volver" regresa a la página anterior del sitio (o a la tienda).
═══════════════════════════════════════ */
const Legal = (() => {
    const VERSION = '1';
    const VIGENTE = '14 de octubre de 2026';

    function gymId() {
        return new URLSearchParams(location.search).get('gym') || localStorage.getItem('gymId') || '';
    }

    function vendedor() {
        const caja = document.getElementById('vendedor');
        if (!caja) return;
        const id = gymId();
        const sinDatos = () => {
            caja.querySelector('dl').hidden = true;
            caja.querySelector('.sin-datos').hidden = false;
        };
        if (!id) return sinDatos();
        fetch('/api/gyms/' + encodeURIComponent(id))
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(g => {
                const datos = {
                    razon: g.razonSocial || g.nombre,
                    comercial: g.razonSocial && g.nombre !== g.razonSocial ? g.nombre : '',
                    rfc: g.rfc,
                    direccion: g.direccion,
                    telefono: g.telefono,
                    correo: g.correoContacto
                };
                let alguno = false;
                Object.entries(datos).forEach(([clave, valor]) => {
                    const dd = caja.querySelector('[data-vendedor="' + clave + '"]');
                    if (!dd) return;
                    const fila = [dd.previousElementSibling, dd];
                    if (!valor) { fila.forEach(el => { el.hidden = true; }); return; }
                    alguno = true;
                    if (clave === 'correo') {
                        dd.innerHTML = '';
                        const a = document.createElement('a');
                        a.href = 'mailto:' + valor;
                        a.textContent = valor;
                        dd.appendChild(a);
                    } else {
                        dd.textContent = valor;
                    }
                });
                document.querySelectorAll('[data-vendedor-nombre]').forEach(el => { el.textContent = datos.razon; });
                if (!alguno) sinDatos();
            })
            .catch(sinDatos);
    }

    function volver() {
        const enlace = document.getElementById('volver');
        if (!enlace) return;
        enlace.addEventListener('click', (e) => {
            let mismoSitio = false;
            try { mismoSitio = !!document.referrer && new URL(document.referrer).origin === location.origin; } catch (err) { /* sin referrer */ }
            if (mismoSitio && history.length > 1) {
                e.preventDefault();
                history.back();
            }
        });
    }

    function iniciar() {
        document.querySelectorAll('[data-legal-version]').forEach(el => { el.textContent = 'Versión ' + VERSION + ' · vigente desde el ' + VIGENTE; });
        // Los enlaces entre páginas legales conservan el gimnasio.
        const id = new URLSearchParams(location.search).get('gym');
        if (id) document.querySelectorAll('a[data-con-gym]').forEach(a => { a.href += (a.href.includes('?') ? '&' : '?') + 'gym=' + encodeURIComponent(id); });
        vendedor();
        volver();
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
    else iniciar();

    return { VERSION, VIGENTE };
})();

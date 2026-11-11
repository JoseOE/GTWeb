/* Panel del dueño (bienvenido.html): arranque. Protege la página, cambia de pestaña y llama a cada init*().
   Los archivos de js/panel/ se cargan en este orden y comparten el ámbito
   global, igual que cuando eran un solo <script> en la página. */

document.addEventListener("DOMContentLoaded", () => {
    const userName = localStorage.getItem('userName');
    // Protección de ruta: si no hay sesión, regresa al login
    if (!userName) {
        window.location.href = 'login.html';
        return;
    }
    // La cuenta del equipo de GymTrack no tiene gimnasio: su panel es otro.
    if (localStorage.getItem('role') === 'admin') {
        location.replace('admin.html');
        return;
    }
    // El panel es del dueño: un miembro que llegue aquí va a la tienda de su gimnasio.
    if (localStorage.getItem('role') === 'member') {
        window.location.href = 'tienda.html';
        return;
    }
    document.getElementById('nav-user-name').textContent = userName;
    document.getElementById('nav-avatar').textContent = iniciales(userName);
    document.getElementById('dash-user-name').textContent = userName;

    const navLinks = document.querySelectorAll('.dash-nav-link');
    const panels = document.querySelectorAll('.dash-panel');
    const content = document.querySelector('.dash-content');

    // Cada pestaña se entera cuando se muestra (evento "panel:mostrado") para
    // traer datos frescos: solicitudes nuevas, pagos, ventas.
    function showPanel(key) {
        panels.forEach(p => p.classList.toggle('active', p.dataset.panel === key));
        navLinks.forEach(l => {
            const isActive = l.dataset.panel === key;
            l.classList.toggle('active', isActive);
            l.setAttribute('aria-selected', String(isActive));
        });
        content.scrollTo({ top: 0, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
        document.dispatchEvent(new CustomEvent('panel:mostrado', { detail: key }));
    }

    navLinks.forEach(link => link.addEventListener('click', () => showPanel(link.dataset.panel)));
    // [data-goto] lleva a otra pestaña; con data-abrir, además pulsa ese
    // botón al llegar (p. ej. "Agregar usuario"). Delegado, porque varios se
    // pintan después (Resumen, avisos de "configura tu gimnasio").
    document.addEventListener('click', (e) => {
        const destino = e.target.closest('[data-goto]');
        if (!destino) return;
        showPanel(destino.dataset.goto);
        const abrir = destino.dataset.abrir && document.getElementById(destino.dataset.abrir);
        if (abrir && !abrir.disabled) abrir.click();
    });

    initGymForm();
    initRoutines();
    initMembers();
    initMachines();
    initBilling();
    initProductos();
    initPlanes();
    initMostrador();
    initVentas();
    initResumen();
    initBitacora();
    initAccesos();
});

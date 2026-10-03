// Aviso para quien entra con la contraseña temporal que le dio su gimnasio
// (alta en recepción): le pide cambiarla. Se usa en la tienda y en Mi cuenta.
(function () {
    const userId = localStorage.getItem('userId');
    if (!userId) return;
    const formulario = document.getElementById('form-contrasena');   // solo existe en Mi cuenta
    let aviso = null;

    function mostrar() {
        aviso = document.createElement('div');
        aviso.className = 'alert alert-warning rounded-0 border-0 border-bottom mb-0 py-2';
        aviso.setAttribute('role', 'status');
        aviso.innerHTML = '<div class="container d-flex align-items-center gap-2">'
            + '<i class="bx bx-lock-alt fs-5 flex-shrink-0" aria-hidden="true"></i><span>'
            + (formulario
                ? 'Estás usando la contraseña temporal que te dio tu gimnasio. Cámbiala aquí abajo por una que solo tú conozcas.'
                : 'Estás usando la contraseña temporal que te dio tu gimnasio. <a href="cuenta.html" class="alert-link">Cámbiala ahora</a>.')
            + '</span></div>';
        const ancla = document.querySelector('#tienda-barra, nav.navbar');
        if (ancla) ancla.insertAdjacentElement('afterend', aviso);
        else document.body.prepend(aviso);
    }

    function revisar() {
        return fetch('/api/users/' + encodeURIComponent(userId) + '/me')
            .then(r => r.ok ? r.json() : null)
            .then(cuenta => {
                const temporal = !!(cuenta && cuenta.contrasenaTemporal);
                if (temporal && !aviso) mostrar();
                if (!temporal && aviso) { aviso.remove(); aviso = null; }
            })
            .catch(() => { /* sin conexión: no se muestra nada */ });
    }

    revisar();
    // En Mi cuenta el aviso se quita en cuanto la contraseña cambia.
    if (formulario) formulario.addEventListener('submit', () => setTimeout(revisar, 1500));
})();

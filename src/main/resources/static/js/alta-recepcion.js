// Alta en recepción (panel del dueño): al abrir "Agregar usuario" se propone
// una contraseña temporal segura, que el dueño puede cambiar o copiar para
// entregársela al usuario. El servidor la marca como temporal y la página y la
// app le piden al usuario cambiarla en cuanto entra.
(function () {
    const modal = document.getElementById('memberModal');
    const campo = document.getElementById('member-password');
    if (!modal || !campo) return;

    // Sin caracteres que se confundan al dictarla (0/O, 1/l/I).
    const LETRAS = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ';
    const NUMEROS = '23456789';

    function azar(n) {
        const valor = new Uint32Array(1);
        crypto.getRandomValues(valor);
        return valor[0] % n;
    }

    // Cuatro caracteres con al menos un número en una posición al azar.
    function bloque() {
        const caracteres = Array.from({ length: 4 }, () => LETRAS[azar(LETRAS.length)]);
        caracteres[azar(4)] = NUMEROS[azar(NUMEROS.length)];
        return caracteres.join('');
    }

    // Tres bloques con guiones, p. ej. "Kp7m-Rx4t-Zq9w": letras, números y
    // símbolo (cumple las reglas de contraseña segura) y fácil de dictar.
    function generar() {
        campo.value = [bloque(), bloque(), bloque()].join('-');
    }

    modal.addEventListener('show.bs.modal', generar);

    document.getElementById('member-password-generar').addEventListener('click', () => {
        generar();
        campo.focus();
    });

    const copiar = document.getElementById('member-password-copiar');
    copiar.addEventListener('click', () => {
        const texto = copiar.querySelector('.copiar-texto');
        navigator.clipboard.writeText(campo.value)
            .then(() => {
                texto.textContent = 'Copiada';
                setTimeout(() => { texto.textContent = 'Copiar'; }, 1800);
            })
            .catch(() => campo.select());
    });
})();

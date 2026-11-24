/* Páginas de error: mismo tema que eligió la persona en el panel (localStorage
   "tema") o, si no eligió, el del sistema. Se carga en el <head> para no destellar. */
(function () {
    let tema = null;
    try { tema = localStorage.getItem('tema'); } catch (e) { /* sin almacenamiento */ }
    if (tema !== 'oscuro' && tema !== 'claro') tema = matchMedia('(prefers-color-scheme: dark)').matches ? 'oscuro' : 'claro';
    document.documentElement.setAttribute('data-tema', tema);
})();

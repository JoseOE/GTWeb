/* Panel del dueño (bienvenido.html): Productos — /api/gyms/{id}/productos y sus reseñas. */

let productosCache = [];
let planesCache = [];
let presetsCache = [];

/* ─── Productos ─── */
function initProductos() {
    document.getElementById('resenas-panel').addEventListener('click', (e) => {
        const boton = e.target.closest('[data-resena-id]');
        if (!boton) return;
        const gymId = localStorage.getItem('gymId');
        boton.disabled = true;
        apiTienda('/api/gyms/' + gymId + '/resenas/' + encodeURIComponent(boton.dataset.resenaId) + '/visibilidad', {
            method: 'POST',
            body: JSON.stringify({ oculta: boton.dataset.oculta === 'true' })
        })
            .then(() => loadResenasPanel(gymId))
            .catch(err => { boton.disabled = false; alert(err.message); });
    });

    const modal = new bootstrap.Modal(document.getElementById('productoModal'));
    const form = document.getElementById('producto-form');
    let advertenciaTocada = false;
    const alertBox = document.getElementById('producto-alert');
    const saveBtn = document.getElementById('producto-guardar-btn');
    const rows = document.getElementById('variantes-rows');
    const imagenInput = document.getElementById('producto-imagen-input');
    const imagenPreview = document.getElementById('producto-imagen-preview');
    const imagenQuitar = document.getElementById('producto-imagen-quitar');
    let imagenUrl = null;        // la que ya tiene el producto
    let imagenPendiente = null;  // data URI elegida pero aún no subida
    let filaN = 0;

    function mostrarImagen(src) {
        if (src) {
            imagenPreview.innerHTML = '<img src="' + escapeHtml(src) + '" alt="Foto del producto" style="width:100%;height:100%;object-fit:cover">';
            imagenQuitar.classList.remove('d-none');
        } else {
            imagenPreview.innerHTML = '<i class="bx bx-image-add fs-2 text-body-secondary"></i>';
            imagenQuitar.classList.add('d-none');
        }
    }

    imagenInput.addEventListener('change', () => {
        const file = imagenInput.files && imagenInput.files[0];
        if (!file) return;
        reducirImagen(file, 800)
            .then(dataUrl => { imagenPendiente = dataUrl; mostrarImagen(dataUrl); })
            .catch(err => { alertBox.textContent = err.message; alertBox.classList.remove('d-none'); });
    });
    imagenQuitar.addEventListener('click', () => {
        imagenUrl = null; imagenPendiente = null; imagenInput.value = ''; mostrarImagen(null);
    });

    // Una fila por presentación. El id viaja oculto para que al editar se
    // actualice la misma variante (y su stock) en lugar de crear otra.
    function agregarVariante(v) {
        v = v || { controlarInventario: true };
        const n = ++filaN;
        const apartados = v.controlarInventario && v.existencias != null && v.disponible != null ? v.existencias - v.disponible : 0;
        const row = document.createElement('div');
        row.className = 'row g-2 align-items-end variante-row border rounded-3 p-2 mx-0';
        row.innerHTML = `
            <input type="hidden" class="var-id" value="${escapeHtml(v.id || '')}">
            <div class="col-6 col-md-3">
                <label class="form-label small mb-1" for="var-pres-${n}">Presentación</label>
                <input type="text" id="var-pres-${n}" class="form-control form-control-sm var-presentacion" list="presentaciones-sugeridas" placeholder="Ej. Scoop" value="${escapeHtml(v.presentacion || '')}">
            </div>
            <div class="col-6 col-md-2">
                <label class="form-label small mb-1" for="var-sabor-${n}">Sabor</label>
                <input type="text" id="var-sabor-${n}" class="form-control form-control-sm var-sabor" placeholder="Opcional" value="${escapeHtml(v.sabor || '')}">
            </div>
            <div class="col-6 col-md-2">
                <label class="form-label small mb-1" for="var-precio-${n}">Precio</label>
                <input type="number" step="0.01" min="0" id="var-precio-${n}" class="form-control form-control-sm var-precio" placeholder="0.00" value="${v.precio != null ? v.precio : ''}">
            </div>
            <div class="col-6 col-md-2">
                <label class="form-label small mb-1" for="var-exist-${n}">Existencias</label>
                <input type="number" step="1" min="0" id="var-exist-${n}" class="form-control form-control-sm var-existencias" placeholder="0" value="${v.existencias != null ? v.existencias : ''}">
            </div>
            <div class="col-8 col-md-2 pb-1">
                <div class="form-check form-switch mb-0">
                    <input class="form-check-input var-controlar" type="checkbox" role="switch" id="var-ctrl-${n}" ${v.controlarInventario === false ? '' : 'checked'}>
                    <label class="form-check-label small" for="var-ctrl-${n}">Inventario</label>
                </div>
            </div>
            <div class="col-4 col-md-1 text-end">
                <button type="button" class="btn btn-outline-danger btn-sm rounded-circle var-quitar" title="Quitar" aria-label="Quitar presentación"><i class='bx bx-x'></i></button>
            </div>
            ${apartados > 0 ? '<div class="col-12"><small class="text-body-secondary">' + apartados + ' apartado(s) en pedidos sin entregar.</small></div>' : ''}`;
        const controlar = row.querySelector('.var-controlar');
        const existencias = row.querySelector('.var-existencias');
        const sincronizar = () => { existencias.disabled = !controlar.checked; if (!controlar.checked) existencias.value = ''; };
        controlar.addEventListener('change', sincronizar);
        sincronizar();
        row.querySelector('.var-quitar').addEventListener('click', () => {
            if (rows.querySelectorAll('.variante-row').length > 1) row.remove();
        });
        rows.appendChild(row);
    }

    document.getElementById('variante-agregar-btn').addEventListener('click', () => agregarVariante());

    function abrir(p) {
        form.reset();
        rows.innerHTML = '';
        alertBox.classList.add('d-none');
        imagenInput.value = '';
        imagenPendiente = null;
        imagenUrl = p ? p.imagen : null;
        mostrarImagen(imagenUrl);
        document.getElementById('producto-id').value = p ? p.id : '';
        document.getElementById('productoModalLabel').textContent = p ? 'Editar producto' : 'Nuevo producto';
        document.getElementById('producto-nombre').value = p ? p.nombre : '';
        document.getElementById('producto-descripcion').value = p && p.descripcion ? p.descripcion : '';
        document.getElementById('producto-activo').checked = p ? p.activo : true;
        document.getElementById('producto-marca').value = p && p.marca ? p.marca : '';
        document.getElementById('producto-proveedor').value = p && p.proveedor ? p.proveedor : '';
        document.getElementById('producto-sitio').value = p && p.sitioProveedor ? p.sitioProveedor : '';
        const cat = document.getElementById('producto-categoria');
        if (p && p.categoria) cat.value = p.categoria.handle;
        // Un producto nuevo sigue a su categoría (suplementos sí) hasta que el dueño toque la casilla.
        advertenciaTocada = !!p;
        document.getElementById('producto-advertencia').checked = p ? !!p.requiereAdvertencia : cat.value === 'suplementos';
        (p ? p.variantes : [null]).forEach(agregarVariante);
        modal.show();
    }

    document.getElementById('producto-nuevo-btn').addEventListener('click', () => abrir(null));
    document.getElementById('producto-advertencia').addEventListener('change', () => { advertenciaTocada = true; });
    document.getElementById('producto-categoria').addEventListener('change', (e) => {
        if (!advertenciaTocada) document.getElementById('producto-advertencia').checked = e.target.value === 'suplementos';
    });
    document.getElementById('productos-buscar').addEventListener('input', renderProductos);
    document.getElementById('productos-categoria').addEventListener('change', renderProductos);

    document.getElementById('productos-container').addEventListener('click', (e) => {
        const editar = e.target.closest('[data-editar-producto]');
        const borrar = e.target.closest('[data-borrar-producto]');
        if (editar) {
            abrir(productosCache.find(p => p.id === editar.dataset.editarProducto));
        } else if (borrar) {
            const p = productosCache.find(x => x.id === borrar.dataset.borrarProducto);
            if (!confirm('¿Eliminar "' + p.nombre + '" de tu tienda? Los pedidos que ya lo incluyen no cambian.')) return;
            borrar.disabled = true;
            const gymId = localStorage.getItem('gymId');
            apiTienda('/api/gyms/' + gymId + '/productos/' + p.id, { method: 'DELETE' })
                .then(() => loadProductos(gymId))
                .catch(err => { borrar.disabled = false; alert(err.message); });
        }
    });

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        alertBox.classList.add('d-none');
        const gymId = localStorage.getItem('gymId');
        const id = document.getElementById('producto-id').value;
        const variantes = [...rows.querySelectorAll('.variante-row')].map(r => ({
            id: r.querySelector('.var-id').value || null,
            presentacion: r.querySelector('.var-presentacion').value.trim(),
            sabor: r.querySelector('.var-sabor').value.trim(),
            precio: r.querySelector('.var-precio').value === '' ? null : parseFloat(r.querySelector('.var-precio').value),
            controlarInventario: r.querySelector('.var-controlar').checked,
            existencias: r.querySelector('.var-existencias').value === '' ? 0 : parseInt(r.querySelector('.var-existencias').value, 10)
        }));
        const fallar = (msg) => { alertBox.textContent = msg; alertBox.classList.remove('d-none'); };
        if (!document.getElementById('producto-nombre').value.trim()) return fallar('Escribe el nombre del producto.');
        if (variantes.some(v => !v.precio || v.precio <= 0)) return fallar('Cada presentación necesita un precio mayor a $0.');

        saveBtn.disabled = true;
        saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status"></span> Guardando...';
        const subirImagen = imagenPendiente
            ? apiTienda('/api/gyms/' + gymId + '/imagenes', { method: 'POST', body: JSON.stringify({ dataUrl: imagenPendiente }) }).then(r => r.url)
            : Promise.resolve(imagenUrl);
        subirImagen
            .then(url => apiTienda(id ? '/api/gyms/' + gymId + '/productos/' + id : '/api/gyms/' + gymId + '/productos', {
                method: id ? 'PUT' : 'POST',
                body: JSON.stringify({
                    nombre: document.getElementById('producto-nombre').value.trim(),
                    descripcion: document.getElementById('producto-descripcion').value.trim(),
                    categoria: document.getElementById('producto-categoria').value,
                    imagen: url,
                    activo: document.getElementById('producto-activo').checked,
                    marca: document.getElementById('producto-marca').value.trim(),
                    proveedor: document.getElementById('producto-proveedor').value.trim(),
                    sitioProveedor: document.getElementById('producto-sitio').value.trim(),
                    requiereAdvertencia: document.getElementById('producto-advertencia').checked,
                    variantes
                })
            }))
            .then(() => { modal.hide(); loadProductos(gymId); })
            .catch(err => fallar(err.message))
            .finally(() => { saveBtn.disabled = false; saveBtn.innerHTML = '<i class="bx bx-save"></i> Guardar producto'; });
    });

    // Las categorías son las mismas para todos los gimnasios; "Membresías" se maneja en Planes.
    fetch('/api/tienda/categorias')
        .then(res => res.ok ? res.json() : [])
        .then(cats => {
            const deProducto = cats.filter(c => c.handle !== 'membresias');
            document.getElementById('producto-categoria').innerHTML = deProducto
                .map(c => '<option value="' + escapeHtml(c.handle) + '">' + escapeHtml(c.nombre) + '</option>').join('');
            document.getElementById('productos-categoria').innerHTML = '<option value="">Todas las categorías</option>'
                + deProducto.map(c => '<option value="' + escapeHtml(c.handle) + '">' + escapeHtml(c.nombre) + '</option>').join('');
        })
        .catch(() => {});

    const gymId = localStorage.getItem('gymId');
    if (gymId) document.getElementById('producto-nuevo-btn').disabled = false;
    loadProductos(gymId);
}

function loadProductos(gymId) {
    const container = document.getElementById('productos-container');
    if (!gymId) {
        container.innerHTML = avisoSinGimnasio('Configura primero "Mi Gimnasio" para abrir tu tienda.', 'bx-package');
        return;
    }
    estadoCargando(container, 'Cargando productos…');
    apiTienda('/api/gyms/' + gymId + '/productos')
        .then(productos => {
            productosCache = productos;
            resumenConteos.productos = productos.length;
            pintarPasos();
            renderProductos();
        })
        .catch(err => estadoError(container, err.message, () => loadProductos(gymId)));
    loadResenasPanel(gymId);
}

// ─── Reseñas: el dueño las revisa y oculta las que no deben mostrarse ───
function loadResenasPanel(gymId) {
    const lista = document.getElementById('resenas-panel');
    const conteo = document.getElementById('resenas-panel-conteo');
    apiTienda('/api/gyms/' + gymId + '/resenas')
        .then(resenas => {
            const ocultas = resenas.filter(r => r.oculta).length;
            conteo.textContent = resenas.length
                ? resenas.length + (resenas.length === 1 ? ' reseña' : ' reseñas') + (ocultas ? ' · ' + ocultas + (ocultas === 1 ? ' oculta' : ' ocultas') : '')
                : '';
            lista.innerHTML = resenas.length
                ? resenas.map(r => `
                    <li class="d-flex flex-wrap justify-content-between align-items-start gap-2 px-4 py-3 border-bottom${r.oculta ? ' opacity-75' : ''}">
                        <div style="min-width:0;flex:1 1 280px">
                            <div class="d-flex flex-wrap align-items-center gap-2">
                                <span class="text-warning-emphasis" role="img" aria-label="${r.estrellas} de 5 estrellas">${'★'.repeat(r.estrellas)}<span class="text-body-tertiary">${'★'.repeat(5 - r.estrellas)}</span></span>
                                <strong class="small">${escapeHtml(r.producto)}</strong>
                                ${r.oculta ? '<span class="badge rounded-pill bg-secondary-subtle text-secondary-emphasis">Oculta</span>' : ''}
                            </div>
                            <div class="small text-body-secondary">${escapeHtml(r.autor)} · ${new Date(r.fecha).toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' })}</div>
                            ${r.comentario ? '<p class="small mb-0 mt-1" style="overflow-wrap:anywhere">' + escapeHtml(r.comentario) + '</p>' : ''}
                        </div>
                        <button type="button" class="btn btn-sm rounded-pill px-3 ${r.oculta ? 'btn-outline-primary' : 'btn-outline-secondary'}" data-resena-id="${escapeHtml(r.id)}" data-oculta="${r.oculta ? 'false' : 'true'}">
                            <i class='bx ${r.oculta ? 'bx-show' : 'bx-hide'}' aria-hidden="true"></i> ${r.oculta ? 'Mostrar' : 'Ocultar'}
                        </button>
                    </li>`).join('')
                : '<li class="text-center text-body-secondary py-4 px-4 small">Todavía no hay reseñas. Aparecen cuando tus miembros califican lo que compraron.</li>';
        })
        .catch(err => { lista.innerHTML = '<li class="text-danger small px-4 py-3">' + escapeHtml(err.message) + '</li>'; });
}

function renderProductos() {
    const container = document.getElementById('productos-container');
    if (!productosCache.length) {
        container.innerHTML = '<div class="dash-empty text-center text-body-secondary py-5"><i class="bx bx-package fs-1 d-block mb-2 opacity-50"></i>Tu tienda aún no tiene productos. Agrega el primero con "Nuevo producto".</div>';
        return;
    }
    const q = document.getElementById('productos-buscar').value.trim().toLowerCase();
    const cat = document.getElementById('productos-categoria').value;
    const visibles = productosCache.filter(p =>
        (!q || p.nombre.toLowerCase().includes(q)) && (!cat || (p.categoria && p.categoria.handle === cat)));
    if (!visibles.length) {
        container.innerHTML = '<div class="dash-empty text-center text-body-secondary py-5">Ningún producto coincide con la búsqueda.</div>';
        return;
    }
    container.innerHTML = '<div class="row g-3">' + visibles.map(p => {
        const lineas = p.variantes.slice(0, 4).map(v => {
            let stock = '<span class="text-body-secondary">sin control</span>';
            if (v.controlarInventario) {
                stock = v.disponible <= 0 ? '<span class="text-danger fw-semibold">agotado</span>'
                    : v.disponible <= 3 ? '<span class="text-warning fw-semibold">' + v.disponible + ' disp.</span>'
                    : '<span class="text-body-secondary">' + v.disponible + ' disp.</span>';
            }
            return '<div class="d-flex justify-content-between gap-2 border-bottom py-1"><span class="text-truncate">' + escapeHtml(v.nombre)
                + '</span><span class="flex-shrink-0">' + dinero(v.precio) + ' · ' + stock + '</span></div>';
        }).join('');
        const mas = p.variantes.length > 4 ? '<div class="text-body-secondary pt-1">+' + (p.variantes.length - 4) + ' más</div>' : '';
        return `
        <div class="col-sm-6 col-xl-4">
            <div class="dash-empty text-start h-100 d-flex flex-column p-0 overflow-hidden">
                <div class="ratio ratio-16x9 bg-body-tertiary">
                    ${p.imagen
                        ? '<img src="' + escapeHtml(p.imagen) + '" alt="" style="object-fit:cover">'
                        : '<div class="d-flex align-items-center justify-content-center"><i class="bx bx-package fs-1 text-body-secondary opacity-50"></i></div>'}
                </div>
                <div class="p-3 d-flex flex-column flex-grow-1">
                    <div class="d-flex justify-content-between align-items-start gap-2">
                        <h4 class="fw-bold fs-6 mb-1">${escapeHtml(p.nombre)}</h4>
                        ${p.activo ? '' : '<span class="badge rounded-pill bg-secondary bg-opacity-10 text-body-secondary flex-shrink-0"><i class="bx bx-hide"></i> Oculto</span>'}
                    </div>
                    <small class="text-body-secondary mb-2">${escapeHtml(p.categoria ? p.categoria.nombre : 'Sin categoría')} · desde <strong>${dinero(p.precioDesde)}</strong></small>
                    <div class="small mb-3">${lineas}${mas}</div>
                    <div class="mt-auto d-flex gap-2">
                        <button type="button" class="btn btn-outline-primary btn-sm rounded-pill px-3" data-editar-producto="${escapeHtml(p.id)}">Editar</button>
                        <button type="button" class="btn btn-outline-danger btn-sm rounded-pill px-3" data-borrar-producto="${escapeHtml(p.id)}">Eliminar</button>
                    </div>
                </div>
            </div>
        </div>`;
    }).join('') + '</div>';
}

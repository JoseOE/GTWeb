document.addEventListener("DOMContentLoaded", () => {
    initNavbarScroll();
    initScrollAnimations();
    initCounters();
    initServiceModal();
    initSolucionesSlider();
    fetchServices();
    initServiceAutoRefresh();
    initMap();
    initEditorialInteractions();
});

/* ═══════════════════════════════════════
   CONTADOR ANIMADO (sección de métricas)
═══════════════════════════════════════ */
function initCounters() {
    const counters = document.querySelectorAll('[data-count-target]');
    if (!counters.length) return;

    const obs = new IntersectionObserver(entries => {
        entries.forEach(entry => {
            if (!entry.isIntersecting) return;
            obs.unobserve(entry.target);
            animateCounter(entry.target);
        });
    }, { threshold: 0.4 });

    counters.forEach(el => obs.observe(el));
}

function animateCounter(el) {
    const target = el.dataset.countTarget;
    const match = target.match(/^(\d+)(.*)$/);
    if (!match) return;

    const end = parseInt(match[1], 10);
    const suffix = match[2];
    const duration = 1200;
    const start = performance.now();

    function tick(now) {
        const progress = Math.min((now - start) / duration, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        el.textContent = Math.round(eased * end) + suffix;
        if (progress < 1) requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
}

/* ═══════════════════════════════════════
   OBSERVADOR REUTILIZABLE (fade-in al hacer scroll)
═══════════════════════════════════════ */
function createRevealObserver() {
    return new IntersectionObserver(entries => {
        entries.forEach(e => { if (e.isIntersecting) e.target.classList.add('visible'); });
    }, { threshold: 0.1, rootMargin: '0px 0px -80px 0px' });
}

/* ═══════════════════════════════════════
   NAVBAR GLASSMORPHISM AL SCROLL
═══════════════════════════════════════ */
function initNavbarScroll() {
    const nav = document.getElementById('main-navbar');
    if (!nav) return;
    nav.classList.toggle('scrolled', window.scrollY > 60);
    window.addEventListener('scroll', () => {
        nav.classList.toggle('scrolled', window.scrollY > 60);
    }, { passive: true });
}

/* ═══════════════════════════════════════
   MAPA LEAFLET + RUTA
═══════════════════════════════════════ */
function initMap() {
    const mapElement = document.getElementById('map');
    if (!mapElement) return;
    if (typeof L === 'undefined') {
        mapElement.innerHTML = '<p class="p-4">El mapa no está disponible. <a href="https://www.openstreetmap.org/?mlat=20.206231&mlon=-99.222102#map=16/20.206231/-99.222102" target="_blank" rel="noopener noreferrer">Abrir ubicación</a></p>';
        return;
    }
    const destLat = 20.206231;
    const destLng = -99.222102;
    const map = L.map('map').setView([destLat, destLng], 14);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(map);

    L.marker([destLat, destLng]).addTo(map)
        .bindPopup('<b>GymTrack</b><br>Oficinas Centrales.')
        .openPopup();

    const routeButton = document.getElementById('show-route');
    const routeStatus = document.getElementById('route-status');
    if (!routeButton) return;

    // Se guardan para poder recalcular la ruta sin encimar marcadores ni líneas.
    let marcadorUsuario = null;
    let controlDeRuta = null;

    routeButton.addEventListener('click', pedirRuta);
    rutaAutomatica();

    // La ruta se traza sola al abrir la página, sin pulsar nada. Si el permiso está
    // denegado no se insiste: se explica cómo activarlo y el botón queda para
    // volver a intentarlo.
    function rutaAutomatica() {
        if (!navigator.geolocation) return;
        if (!navigator.permissions || !navigator.permissions.query) {
            pedirRuta();
            return;
        }
        navigator.permissions.query({ name: 'geolocation' })
            .then(permiso => {
                if (permiso.state === 'denied') {
                    avisar('Activa el permiso de ubicación de tu navegador para ver la ruta desde donde estás.');
                    return;
                }
                pedirRuta();
            })
            .catch(() => pedirRuta());
    }

    function pedirRuta() {
        if (!navigator.geolocation) {
            avisar('Tu navegador no permite consultar la ubicación.');
            return;
        }
        routeButton.disabled = true;
        avisar('Buscando tu ubicación…');

        // Primero se pide la ubicación precisa (GPS). En una computadora sin GPS eso
        // suele tardar o fallar, así que se reintenta con la ubicación aproximada
        // en lugar de quedarse sin ruta.
        ubicacionActual({ enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 })
            .catch(() => ubicacionActual({ enableHighAccuracy: false, timeout: 15000, maximumAge: 300000 }))
            .then(trazarRuta)
            .catch(error => {
                avisar(mensajeDeUbicacion(error));
                routeButton.disabled = false;
            });
    }

    function trazarRuta(pos) {
        const uLat = pos.coords.latitude, uLng = pos.coords.longitude;

        if (marcadorUsuario) map.removeLayer(marcadorUsuario);
        marcadorUsuario = L.marker([uLat, uLng], {
            icon: L.divIcon({
                html: '<i class="bx bxs-user-circle" style="font-size:28px;color:#1a73e8"></i>',
                iconSize: [28, 28], className: 'user-marker'
            })
        }).addTo(map).bindPopup('<b>Tu ubicación</b>');

        map.fitBounds([[uLat, uLng],[destLat, destLng]], { padding: [50, 50] });
        avisar('Calculando la ruta…');

        if (controlDeRuta) map.removeControl(controlDeRuta);
        controlDeRuta = crearRuta(map, uLat, uLng, destLat, destLng);
        if (!controlDeRuta) {          // el complemento de rutas no cargó
            rutaNoDisponible(uLat, uLng);
            return;
        }

        // Si el servicio tarda demasiado en contestar, no se deja al usuario
        // esperando: a los 12 segundos se ofrece la ruta por otro lado.
        const espera = setTimeout(() => rutaNoDisponible(uLat, uLng), 12000);

        controlDeRuta.on('routesfound', evento => {
            clearTimeout(espera);
            const resumen = evento.routes[0].summary;
            const km = (resumen.totalDistance / 1000).toFixed(1);
            const minutos = Math.max(1, Math.round(resumen.totalTime / 60));
            avisar('Ruta lista: ' + km + ' km, unos ' + minutos + ' min en coche.');
            routeButton.disabled = false;
        });
        // El servicio de rutas es gratuito y a veces no responde: conviene avisarlo
        // y ofrecer otra forma de llegar, en vez de dejar el mapa sin ruta y sin explicación.
        controlDeRuta.on('routingerror', () => {
            clearTimeout(espera);
            rutaNoDisponible(uLat, uLng);
        });
    }

    function rutaNoDisponible(uLat, uLng) {
        avisar('No pudimos calcular la ruta en el mapa. ');
        const enlace = document.createElement('a');
        enlace.href = 'https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route='
            + uLat + '%2C' + uLng + '%3B' + destLat + '%2C' + destLng;
        enlace.target = '_blank';
        enlace.rel = 'noopener noreferrer';
        enlace.textContent = 'Ábrela en OpenStreetMap';
        routeStatus.append(enlace);
        routeButton.disabled = false;
    }

    function avisar(texto) {
        routeStatus.textContent = texto;
    }

    // Un mensaje distinto por cada motivo: así se sabe si hay que dar permiso,
    // encender la ubicación o solo volver a intentarlo.
    function mensajeDeUbicacion(error) {
        if (!error || typeof error.code !== 'number') return 'No pudimos obtener tu ubicación. Vuelve a intentarlo.';
        if (error.code === error.PERMISSION_DENIED) return 'No diste permiso para usar tu ubicación. Actívalo en el candado de la barra de direcciones y vuelve a intentarlo.';
        if (error.code === error.POSITION_UNAVAILABLE) return 'Tu dispositivo no pudo obtener la ubicación. Revisa que la ubicación del sistema esté encendida.';
        if (error.code === error.TIMEOUT) return 'Tardó demasiado en obtener tu ubicación. Vuelve a intentarlo.';
        return 'No pudimos obtener tu ubicación. Vuelve a intentarlo.';
    }
}

function ubicacionActual(opciones) {
    return new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve, reject, opciones));
}

// Dibuja la ruta en coche entre dos puntos; null si el complemento de rutas no está disponible.
function crearRuta(map, fromLat, fromLng, toLat, toLng) {
    if (!L.Routing) return null;
    return L.Routing.control({
        waypoints: [L.latLng(fromLat, fromLng), L.latLng(toLat, toLng)],
        routeWhileDragging: false,
        addWaypoints: false,
        lineOptions: { styles: [{ color: '#ff5722', weight: 5, opacity: 0.8 }] },
        createMarker: () => null,
        show: true, collapsible: true, language: 'es'
    }).addTo(map);
}

/* ═══════════════════════════════════════
   ANIMACIONES SCROLL (IntersectionObserver)
═══════════════════════════════════════ */
function initScrollAnimations() {
    const obs = createRevealObserver();
    document.querySelectorAll('.scroll-animate, .card-animate').forEach(el => obs.observe(el));
}

/* ═══════════════════════════════════════
   CATÁLOGO DINÁMICO (fetch → MongoDB)
═══════════════════════════════════════ */
let currentServices = [];
// Huella del último catálogo pintado: permite volver a consultar la base en
// segundo plano y repintar SOLO si algo cambió (sin parpadeos ni saltos).
let serviciosFirma = '';
let categoriaActiva = 'todas';

const IMAGEN_SERVICIO_DEFECTO = 'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?q=80&w=600&auto=format&fit=crop';
const REFRESCO_CATALOGO_MS = 30000;

function escapeHtml(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, c => (
        { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    ));
}

// Un servicio agregado a mano en MongoDB Atlas puede venir sin precio: antes eso
// lanzaba un error y dejaba TODO el catálogo en blanco. Ahora solo cambia el texto.
function precioHtml(precio) {
    if (precio === 0) return '<span class="text-success"><i class="bx bx-check-circle"></i> Incluido en plan</span>';
    if (typeof precio === 'number' && isFinite(precio)) {
        return `$${precio.toLocaleString('es-MX')} <small class="text-body-secondary fw-normal">MXN</small>`;
    }
    return '<span class="text-body-secondary fw-normal">Precio a consultar</span>';
}

function normalizarCategoria(cat) {
    return (cat || '').trim().toLowerCase();
}

function etiquetaCategoria(cat) {
    return cat.charAt(0).toUpperCase() + cat.slice(1);
}

// Carga el catálogo desde la base de datos (colección "servicios").
// silencioso = consulta en segundo plano: no muestra spinner y, si falla,
// deja lo que ya se estaba viendo en lugar de reemplazarlo por un error.
function fetchServices(opciones = {}) {
    const silencioso = opciones.silencioso === true;
    const container = document.getElementById('services-container');
    if (!container) return;

    if (!silencioso) {
        container.setAttribute('aria-busy', 'true');
        container.innerHTML = '<div class="catalog-state"><span class="spinner-border" role="status"><span class="visually-hidden">Cargando soluciones</span></span><p>Estamos preparando las soluciones…</p></div>';
    }

    fetch('/api/servicios', { cache: 'no-store', signal: AbortSignal.timeout(12000) })
        .then(r => { if (!r.ok) throw new Error('DB error'); return r.json(); })
        .then(data => {
            const firma = JSON.stringify(data);
            if (silencioso && firma === serviciosFirma) return;
            serviciosFirma = firma;
            currentServices = Array.isArray(data) ? data : [];
            renderServices();
        })
        .catch(() => {
            if (silencioso) return;
            container.innerHTML = `
                <div class="catalog-state" role="status">
                    <i class="bx bx-wifi-off" aria-hidden="true"></i><div><h3>El catálogo no está disponible por ahora</h3><p>Puedes volver a intentarlo o escribirnos para conocer las soluciones.</p></div>
                    <button type="button" id="retry-services">Reintentar</button><a class="text-link dark-link" href="https://wa.me/527721013839" target="_blank" rel="noopener noreferrer">Consultar al equipo <i class="bx bx-right-arrow-alt" aria-hidden="true"></i></a>
                </div>`;
            document.getElementById('retry-services').addEventListener('click', () => fetchServices());
        })
        .finally(() => container.setAttribute('aria-busy', 'false'));
}

function renderServices() {
    const container = document.getElementById('services-container');
    const filtrosCaja = document.getElementById('soluciones-filtros');
    if (!container) return;

    if (!currentServices.length) {
        if (filtrosCaja) filtrosCaja.innerHTML = '';
        container.innerHTML = '<div class="catalog-state"><i class="bx bx-layer" aria-hidden="true"></i><div><h3>Estamos preparando el catálogo</h3><p>Cuéntanos qué necesita tu gimnasio y te orientamos.</p></div><a class="text-link dark-link" href="https://wa.me/527721013839" target="_blank" rel="noopener noreferrer">Consultar al equipo <i class="bx bx-right-arrow-alt" aria-hidden="true"></i></a></div>';
        actualizarSlider();
        return;
    }

    // Las categorías salen de los propios datos: si en la base aparece una
    // categoría nueva, aparece su filtro sin tocar el HTML.
    const categorias = [...new Set(currentServices.map(s => normalizarCategoria(s.categoria)).filter(Boolean))];
    if (categoriaActiva !== 'todas' && !categorias.includes(categoriaActiva)) categoriaActiva = 'todas';

    if (filtrosCaja) {
        filtrosCaja.innerHTML = categorias.length > 1 ? `
            <div class="catalog-filters" role="group" aria-label="Filtrar soluciones por categoría">
                ${['todas', ...categorias].map(cat => `
                    <button type="button" data-categoria="${escapeHtml(cat)}" aria-pressed="${cat === categoriaActiva}">
                        ${cat === 'todas' ? 'Todas' : escapeHtml(etiquetaCategoria(cat))}
                        <span>${cat === 'todas' ? currentServices.length : currentServices.filter(s => normalizarCategoria(s.categoria) === cat).length}</span>
                    </button>`).join('')}
            </div>` : '';
    }

    // El índice original se conserva para que el modal abra el servicio correcto.
    const visibles = currentServices
        .map((s, i) => ({ s, i }))
        .filter(({ s }) => categoriaActiva === 'todas' || normalizarCategoria(s.categoria) === categoriaActiva);

    // Cada tarjeta se arma por separado: si un documento viniera muy roto,
    // solo se omite esa tarjeta y el resto del catálogo se sigue viendo.
    const tarjetas = visibles.map(({ s, i }, posicion) => {
        try {
            const cat = normalizarCategoria(s.categoria);
            const nombre = s.nombre || 'Servicio sin nombre';
            const cotizable = cat === 'hardware' && s.precio > 0;
            return `
                <article class="solucion${s.destacado ? ' es-destacado' : ''}" role="group" aria-roledescription="diapositiva" aria-label="${posicion + 1} de ${visibles.length}: ${escapeHtml(nombre)}" style="--orden:${posicion}">
                    <figure class="solucion-foto">
                        <img src="${escapeHtml(s.imagen || IMAGEN_SERVICIO_DEFECTO)}" alt="" loading="lazy" decoding="async" data-fallback="${IMAGEN_SERVICIO_DEFECTO}">
                        ${cat ? `<span class="solucion-cat">${escapeHtml(etiquetaCategoria(cat))}</span>` : ''}
                        ${s.destacado ? '<span class="solucion-destacado"><i class="bx bxs-star" aria-hidden="true"></i> Destacado</span>' : ''}
                    </figure>
                    <div class="solucion-cuerpo">
                        <span class="solucion-icono" aria-hidden="true"><i class="bx ${escapeHtml(s.icono || 'bx-layer')}"></i></span>
                        <h3>${escapeHtml(nombre)}</h3>
                        <p>${escapeHtml(s.descripcion)}</p>
                        <div class="solucion-pie">
                            <span class="solucion-precio">${precioHtml(s.precio)}</span>
                            <button type="button" class="solucion-cta" data-bs-toggle="modal" data-bs-target="#serviceModal" data-index="${i}">
                                ${cotizable ? 'Cotizar' : 'Ver detalles'}<span class="visually-hidden"> de ${escapeHtml(nombre)}</span>
                                <i class="bx bx-right-arrow-alt" aria-hidden="true"></i>
                            </button>
                        </div>
                    </div>
                </article>`;
        } catch (e) {
            console.warn('Servicio omitido por datos inválidos:', s, e);
            return '';
        }
    }).join('');

    container.innerHTML = tarjetas;
    container.scrollLeft = 0;

    // Si la URL de imagen que alguien puso en la base no carga, se usa la de respaldo.
    container.querySelectorAll('img[data-fallback]').forEach(img => {
        img.addEventListener('error', () => {
            if (img.src !== img.dataset.fallback) img.src = img.dataset.fallback;
        }, { once: true });
    });

    actualizarSlider();
    const anuncio = document.getElementById('soluciones-anuncio');
    if (anuncio && filtrosCaja && filtrosCaja.dataset.anunciar) {
        anuncio.textContent = visibles.length === 1 ? '1 solución' : visibles.length + ' soluciones';
        delete filtrosCaja.dataset.anunciar;
    }
}

// Filtros por categoría (delegado: los botones se regeneran con cada repintado).
document.addEventListener('click', (e) => {
    const boton = e.target.closest('.catalog-filters [data-categoria]');
    if (!boton) return;
    categoriaActiva = boton.dataset.categoria;
    const caja = document.getElementById('soluciones-filtros');
    if (caja) caja.dataset.anunciar = '1';
    renderServices();
    // El foco vuelve al filtro elegido (los botones se acaban de redibujar).
    document.querySelector(`.catalog-filters [data-categoria="${CSS.escape(categoriaActiva)}"]`)?.focus();
});

/* ═══════════════════════════════════════
   SLIDER DE SOLUCIONES
   La pista se desliza sola con scroll-snap (dedo, rueda, trackpad o flechas
   del teclado con la pista enfocada). Los botones avanzan una vista completa
   y la barra de abajo dice en qué parte del catálogo vas. Sin avance
   automático: nadie pierde una tarjeta mientras la lee.
═══════════════════════════════════════ */
function pasoSlider(pista) {
    const tarjeta = pista.querySelector('.solucion');
    if (!tarjeta) return { ancho: pista.clientWidth, porVista: 1 };
    const gap = parseFloat(getComputedStyle(pista).columnGap) || 0;
    const ancho = tarjeta.getBoundingClientRect().width + gap;
    const porVista = Math.max(1, Math.floor((pista.clientWidth + gap) / ancho + 0.05));
    return { ancho, porVista };
}

function moverSlider(direccion) {
    const pista = document.getElementById('services-container');
    if (!pista) return;
    const { ancho, porVista } = pasoSlider(pista);
    const total = pista.querySelectorAll('.solucion').length;
    // Se va a una tarjeta exacta (no "N píxeles más"): dos clics rápidos no
    // dejan la pista a medias entre dos tarjetas.
    const actual = Math.round(pista.scrollLeft / ancho);
    const destino = Math.max(0, Math.min(total - porVista, actual + direccion * porVista));
    const reducir = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const desde = pista.scrollLeft;
    pista.scrollTo({ left: destino * ancho, behavior: reducir ? 'auto' : 'smooth' });
    // Si el navegador no anima (pestaña en segundo plano, ahorro de energía),
    // se salta directo para que la flecha nunca parezca no hacer nada.
    if (!reducir) {
        setTimeout(() => {
            if (Math.abs(pista.scrollLeft - desde) < 2 && Math.abs(destino * ancho - desde) > 2) {
                pista.scrollTo({ left: destino * ancho, behavior: 'auto' });
            }
        }, 700);
    }
}

function actualizarSlider() {
    const pista = document.getElementById('services-container');
    const anterior = document.getElementById('soluciones-anterior');
    const siguiente = document.getElementById('soluciones-siguiente');
    if (!pista || !anterior || !siguiente) return;
    const max = pista.scrollWidth - pista.clientWidth;
    const desliza = max > 4;
    anterior.disabled = !desliza || pista.scrollLeft <= 4;
    siguiente.disabled = !desliza || pista.scrollLeft >= max - 4;
    const seccion = pista.closest('.solutions-section');
    if (seccion) seccion.classList.toggle('sin-desplazar', !desliza);

    const barra = document.querySelector('.soluciones-progreso span');
    if (barra) {
        const visible = Math.min(1, pista.clientWidth / Math.max(1, pista.scrollWidth));
        const avance = desliza ? pista.scrollLeft / max : 0;
        barra.style.width = (visible * 100) + '%';
        barra.style.transform = `translateX(${visible < 1 ? avance * (1 / visible - 1) * 100 : 0}%)`;
    }

    const conteo = document.getElementById('soluciones-conteo');
    const total = pista.querySelectorAll('.solucion').length;
    if (conteo) {
        if (!total || !desliza) {
            conteo.textContent = total ? (total === 1 ? '1 solución' : total + ' soluciones') : '';
        } else {
            const { ancho, porVista } = pasoSlider(pista);
            const primera = Math.min(total, Math.round(pista.scrollLeft / ancho) + 1);
            const ultima = Math.min(total, primera + porVista - 1);
            conteo.textContent = (primera === ultima ? primera : primera + '–' + ultima) + ' de ' + total;
        }
    }
}

function initSolucionesSlider() {
    const pista = document.getElementById('services-container');
    if (!pista) return;
    document.getElementById('soluciones-anterior')?.addEventListener('click', () => moverSlider(-1));
    document.getElementById('soluciones-siguiente')?.addEventListener('click', () => moverSlider(1));
    let pendiente = false;
    pista.addEventListener('scroll', () => {
        if (pendiente) return;
        pendiente = true;
        requestAnimationFrame(() => { pendiente = false; actualizarSlider(); });
    }, { passive: true });
    window.addEventListener('resize', actualizarSlider);
    // Con la pista enfocada: ← → avanzan una vista; Inicio y Fin van a los extremos.
    pista.addEventListener('keydown', (e) => {
        if (e.target !== pista) return;
        if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
            e.preventDefault();
            moverSlider(e.key === 'ArrowRight' ? 1 : -1);
        } else if (e.key === 'Home' || e.key === 'End') {
            e.preventDefault();
            pista.scrollTo({ left: e.key === 'Home' ? 0 : pista.scrollWidth, behavior: 'auto' });
        }
    });
}

// Si alguien agrega un servicio en la base de datos mientras la página está
// abierta, aparece al volver a la pestaña o, a más tardar, en 30 segundos.
function initServiceAutoRefresh() {
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') fetchServices({ silencioso: true });
    });
    setInterval(() => {
        if (document.visibilityState === 'visible') fetchServices({ silencioso: true });
    }, REFRESCO_CATALOGO_MS);
}

function initEditorialInteractions() {
    const details = document.querySelectorAll('.feature-accordion details');
    details.forEach(item => item.addEventListener('toggle', () => {
        if (item.open) details.forEach(other => { if (other !== item) other.open = false; });
    }));
    const menu = document.getElementById('navbarNav');
    if (!menu || typeof bootstrap === 'undefined') return;
    menu.querySelectorAll('a[href^="#"]').forEach(link => link.addEventListener('click', () => {
        if (menu.classList.contains('show')) bootstrap.Collapse.getOrCreateInstance(menu).hide();
    }));
}

/* ═══════════════════════════════════════
   MODAL DE DETALLE + COTIZADOR CON
   DESCUENTO POR VOLUMEN (máx. 30%)
═══════════════════════════════════════ */
let currentUnitPrice = 0;

function getVolumeDiscount(qty) {
    if (qty >= 20) return 0.30;
    if (qty >= 10) return 0.20;
    if (qty >= 5)  return 0.10;
    return 0;
}

function formatMXN(value) {
    return '$' + Math.round(value).toLocaleString('es-MX') + ' MXN';
}

function initServiceModal() {
    const modalEl = document.getElementById('serviceModal');
    if (!modalEl) return;

    const qtyInput = document.getElementById('sm-qty');

    modalEl.addEventListener('show.bs.modal', (event) => {
        const index = event.relatedTarget ? event.relatedTarget.dataset.index : undefined;
        const service = currentServices[index];
        if (service) populateServiceModal(service);
    });

    document.getElementById('sm-qty-minus').addEventListener('click', () => {
        qtyInput.value = Math.max(1, (parseInt(qtyInput.value, 10) || 1) - 1);
        updateQuote();
    });
    document.getElementById('sm-qty-plus').addEventListener('click', () => {
        qtyInput.value = (parseInt(qtyInput.value, 10) || 1) + 1;
        updateQuote();
    });
    qtyInput.addEventListener('input', () => {
        if (!qtyInput.value || qtyInput.value < 1) qtyInput.value = 1;
        updateQuote();
    });
}

function updateQuote() {
    const qty = Math.max(1, parseInt(document.getElementById('sm-qty').value, 10) || 1);
    const subtotal = currentUnitPrice * qty;
    const discountPct = getVolumeDiscount(qty);
    const discountAmount = subtotal * discountPct;
    const total = subtotal - discountAmount;

    document.getElementById('sm-unit-price').textContent = formatMXN(currentUnitPrice);
    document.getElementById('sm-subtotal').textContent = formatMXN(subtotal);
    document.getElementById('sm-discount-pct').textContent = Math.round(discountPct * 100) + '%';
    document.getElementById('sm-discount-amount').textContent = '-' + formatMXN(discountAmount);
    document.getElementById('sm-discount-row').classList.toggle('d-none', discountPct === 0);
    document.getElementById('sm-total').textContent = formatMXN(total);
}

function populateServiceModal(s) {
    const cat = (s.categoria || '').toUpperCase();
    const isQuotable = cat === 'HARDWARE' && s.precio > 0;

    document.getElementById('sm-img').src = s.imagen || 'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?q=80&w=600&auto=format&fit=crop';
    document.getElementById('sm-img').alt = s.nombre || '';
    document.getElementById('sm-cat').textContent = cat;
    document.getElementById('sm-icon').className = 'bx ' + (s.icono || 'bx-layer');
    document.getElementById('sm-title').textContent = s.nombre || 'Servicio sin nombre';
    document.getElementById('sm-desc').textContent = s.descripcion || '';
    document.getElementById('sm-star').innerHTML = s.destacado
        ? '<span class="solucion-destacado"><i class="bx bxs-star" aria-hidden="true"></i> Destacado</span>' : '';

    const duracionWrap = document.getElementById('sm-duracion');
    if (s.duracion) {
        duracionWrap.classList.remove('d-none');
        duracionWrap.querySelector('span').textContent = s.duracion;
    } else {
        duracionWrap.classList.add('d-none');
    }

    document.getElementById('sm-simple-price').classList.toggle('d-none', isQuotable);
    document.getElementById('sm-quote').classList.toggle('d-none', !isQuotable);

    if (isQuotable) {
        currentUnitPrice = s.precio;
        document.getElementById('sm-qty').value = 1;
        updateQuote();
    } else {
        document.getElementById('sm-simple-price-text').innerHTML = precioHtml(s.precio);
    }

    const waMsg = encodeURIComponent(`Hola, me interesa cotizar: ${s.nombre}`);
    document.getElementById('sm-whatsapp-btn').href = `https://wa.me/527721013839?text=${waMsg}`;
}

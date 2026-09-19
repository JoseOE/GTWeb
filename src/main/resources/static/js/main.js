document.addEventListener("DOMContentLoaded", () => {
    initNavbarScroll();
    initScrollAnimations();
    initCounters();
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
    routeButton.addEventListener('click', () => {
    if (!navigator.geolocation) {
        routeStatus.textContent = 'Tu navegador no permite consultar la ubicación.';
        return;
    }
    routeButton.disabled = true;
    routeStatus.textContent = 'Buscando tu ubicación…';
    navigator.geolocation.getCurrentPosition(
        pos => {
            const uLat = pos.coords.latitude, uLng = pos.coords.longitude;

            L.marker([uLat, uLng], {
                icon: L.divIcon({
                    html: '<i class="bx bxs-user-circle" style="font-size:28px;color:#1a73e8"></i>',
                    iconSize: [28, 28], className: 'user-marker'
                })
            }).addTo(map).bindPopup('<b>Tu ubicación</b>');

            addRoute(map, uLat, uLng, destLat, destLng);
            map.fitBounds([[uLat, uLng],[destLat, destLng]], { padding: [50, 50] });
            routeStatus.textContent = 'Tu ubicación aparece en el mapa.';
            routeButton.disabled = false;
        },
        () => {
            routeStatus.textContent = 'No pudimos obtener tu ubicación. Puedes volver a intentarlo o consultar el mapa.';
            routeButton.disabled = false;
        },
        { enableHighAccuracy: true, timeout: 10000 }
    );
    });
}

function addRoute(map, fromLat, fromLng, toLat, toLng) {
    if (!L.Routing) return;
    L.Routing.control({
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

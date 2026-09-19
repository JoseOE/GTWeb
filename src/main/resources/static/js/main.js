document.addEventListener("DOMContentLoaded", () => {
    initScrollAnimations();
    initCounters();
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
   ANIMACIONES SCROLL (IntersectionObserver)
═══════════════════════════════════════ */
function initScrollAnimations() {
    const obs = createRevealObserver();
    document.querySelectorAll('.scroll-animate, .card-animate').forEach(el => obs.observe(el));
}

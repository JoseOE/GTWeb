/* Athletic motion: one carousel clock, bounded entrances, no animation dependency. */
document.addEventListener('DOMContentLoaded', () => {
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    initAthleticSlider(motion);
    initAthleticEntrances(motion);
});

function initAthleticSlider(motion) {
    const slider = document.getElementById('sliderPrincipal');
    if (!slider) return;
    const slides = [...slider.querySelectorAll('.hero-slide-panel')];
    const selectors = [...slider.querySelectorAll('.slide-selector')];
    const pauseButton = slider.querySelector('.slider-pause');
    const announcement = slider.querySelector('.slider-announcement');
    const duration = 7000;
    let active = 0;
    let elapsed = 0;
    let previousTime = null;
    let frame = null;
    let paused = motion.matches;
    let hovered = false;
    let focused = false;
    let inView = true;
    let pointerStart = null;

    slider.querySelector('.slider-controls').hidden = false;
    function canPlay() {
        return !paused && !motion.matches && !hovered && !focused && inView && !document.hidden;
    }
    function updatePauseButton() {
        pauseButton.setAttribute('aria-label', paused ? 'Reanudar reproducción automática' : 'Pausar reproducción automática');
        pauseButton.querySelector('i').className = paused ? 'bx bx-play' : 'bx bx-pause';
        pauseButton.disabled = motion.matches;
        pauseButton.title = motion.matches ? 'Reproducción automática desactivada por tu preferencia de movimiento reducido' : '';
    }
    function showSlide(index, manual = false) {
        const next = (index + slides.length) % slides.length;
        if (manual) paused = true;
        slides.forEach((slide, i) => {
            slide.classList.toggle('is-active', i === next);
            slide.setAttribute('aria-hidden', String(i !== next));
            slide.inert = i !== next;
        });
        selectors.forEach((button, i) => {
            button.classList.toggle('is-active', i === next);
            if (i === next) button.setAttribute('aria-current', 'true');
            else button.removeAttribute('aria-current');
            button.querySelector('.slide-track > span').style.transform = 'scaleX(0)';
        });
        active = next;
        elapsed = 0;
        slider.querySelector('.slide-count strong').textContent = String(active + 1).padStart(2, '0');
        if (manual) announcement.textContent = slides[active].getAttribute('aria-label');
        updatePauseButton();
        syncClock();
    }
    function tick(now) {
        frame = null;
        if (!canPlay()) { previousTime = null; return; }
        if (previousTime !== null) elapsed += Math.min(now - previousTime, 100);
        previousTime = now;
        if (elapsed >= duration) showSlide(active + 1);
        selectors[active].querySelector('.slide-track > span').style.transform = `scaleX(${Math.min(elapsed / duration, 1)})`;
        if (frame === null) frame = requestAnimationFrame(tick);
    }
    function syncClock() {
        if (frame !== null) cancelAnimationFrame(frame);
        frame = null;
        previousTime = null;
        if (canPlay()) frame = requestAnimationFrame(tick);
    }
    selectors.forEach((button, i) => button.addEventListener('click', () => showSlide(i, true)));
    slider.querySelector('.slider-prev').addEventListener('click', () => showSlide(active - 1, true));
    slider.querySelector('.slider-next').addEventListener('click', () => showSlide(active + 1, true));
    pauseButton.addEventListener('click', () => {
        paused = !paused;
        updatePauseButton();
        syncClock();
    });
    slider.addEventListener('pointerenter', event => {
        if (event.pointerType === 'mouse') { hovered = true; syncClock(); }
    });
    slider.addEventListener('pointerleave', () => { hovered = false; syncClock(); });
    slider.addEventListener('focusin', () => { focused = true; syncClock(); });
    slider.addEventListener('focusout', event => {
        focused = slider.contains(event.relatedTarget);
        syncClock();
    });
    slider.addEventListener('keydown', event => {
        if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
        event.preventDefault();
        showSlide(active + (event.key === 'ArrowRight' ? 1 : -1), true);
    });
    slider.querySelectorAll('.hero-visual').forEach(visual => {
        visual.addEventListener('pointerdown', event => {
            if (event.pointerType === 'mouse') return;
            pointerStart = { x: event.clientX, y: event.clientY };
            visual.setPointerCapture(event.pointerId);
        });
        visual.addEventListener('pointerup', event => {
            if (!pointerStart) return;
            const dx = event.clientX - pointerStart.x;
            const dy = event.clientY - pointerStart.y;
            pointerStart = null;
            if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.5) showSlide(active + (dx < 0 ? 1 : -1), true);
        });
        visual.addEventListener('pointercancel', () => { pointerStart = null; });
    });
    document.addEventListener('visibilitychange', syncClock);
    if ('IntersectionObserver' in window) {
        new IntersectionObserver(entries => {
            inView = entries[0].isIntersecting;
            syncClock();
        }, { threshold: 0 }).observe(slider);
    }
    motion.addEventListener('change', () => {
        if (motion.matches) paused = true;
        updatePauseButton();
        syncClock();
    });
    updatePauseButton();
    syncClock();
}

function initAthleticEntrances(motion) {
    if (!('IntersectionObserver' in window) || !Element.prototype.animate) return;
    const effects = [
        ['.about-grid h2', 'type'],
        ['.about-copy', 'rise'],
        ['.ecosystem-photo', 'photo'],
        ['.feature-accordion', 'rise'],
        ['.solutions-section .section-heading', 'type'],
        ['.contact-band h2', 'type'],
        ['.contact-grid > div', 'rise'],
        ['.location-grid > div:first-child', 'rise']
    ];
    const running = new Set();
    const observer = new IntersectionObserver(entries => {
        entries.forEach(entry => {
            if (!entry.isIntersecting) return;
            observer.unobserve(entry.target);
            if (motion.matches) return;
            const kind = entry.target.dataset.motion;
            const frames = kind === 'photo'
                ? [{ clipPath: 'inset(0 0 18% 0)', transform: 'translateY(18px)' }, { clipPath: 'inset(0)', transform: 'none' }]
                : kind === 'type'
                    ? [{ opacity: .35, transform: 'translateY(22px)' }, { opacity: 1, transform: 'none' }]
                    : [{ opacity: .4, transform: 'translateY(14px)' }, { opacity: 1, transform: 'none' }];
            const animation = entry.target.animate(frames, { duration: kind === 'photo' ? 800 : 650, easing: 'cubic-bezier(.16,1,.3,1)' });
            running.add(animation);
            animation.onfinish = () => running.delete(animation);
        });
    }, { threshold: .15 });
    effects.forEach(([selector, kind]) => document.querySelectorAll(selector).forEach(element => {
        element.dataset.motion = kind;
        observer.observe(element);
    }));
    motion.addEventListener('change', () => {
        if (motion.matches) { running.forEach(animation => animation.cancel()); running.clear(); }
    });
}

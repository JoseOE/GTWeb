/* Panel del dueño (bienvenido.html): Mi Gimnasio. */

/* ═══════════════════════════════════════
   MI GIMNASIO — conectado a /api/gyms
═══════════════════════════════════════ */
function initGymForm() {
    const userId = localStorage.getItem('userId');
    const form = document.getElementById('gym-form');
    const badge = document.getElementById('gym-status-badge');
    const alertBox = document.getElementById('gym-alert');
    const btn = document.getElementById('gym-save-btn');
    const fields = {
        nombre: document.getElementById('gym-nombre'),
        telefono: document.getElementById('gym-telefono'),
        direccion: document.getElementById('gym-direccion'),
        horario: document.getElementById('gym-horario'),
        equipamiento: document.getElementById('gym-equipamiento'),
        maquinas: document.getElementById('gym-maquinas'),
        razonSocial: document.getElementById('gym-razon'),
        rfc: document.getElementById('gym-rfc'),
        correoContacto: document.getElementById('gym-correo-contacto')
    };

    // ─── Identidad visual ───
    const colorInput = document.getElementById('gym-color');
    const colorHex = document.getElementById('gym-color-hex');
    const cuotaInput = document.getElementById('gym-cuota');
    const directorioInput = document.getElementById('gym-directorio');
    const logoInput = document.getElementById('gym-logo-input');
    const logoPreview = document.getElementById('gym-logo-preview');
    const logoClear = document.getElementById('gym-logo-clear');
    let logoDataUrl = null;
    // ─── Portada de la app: fotos del carrusel y letra del nombre ───
    const fotoInput = document.getElementById('gym-foto-input');
    const fotosBox = document.getElementById('gym-fotos');
    const fotoAyuda = document.getElementById('gym-foto-ayuda');
    const tipografiaSel = document.getElementById('gym-tipografia');
    const portadaVista = document.getElementById('portada-vista');
    const portadaNombre = document.getElementById('portada-nombre');
    // Gym.TIPOGRAFIAS → la familia de css/portada.css (los mismos archivos que la app).
    const FAMILIAS = {
        poppins: 'Portada Poppins', 'bebas-neue': 'Portada Bebas Neue', oswald: 'Portada Oswald',
        anton: 'Portada Anton', montserrat: 'Portada Montserrat', playfair: 'Portada Playfair'
    };
    const MAX_FOTOS = 6;
    const AYUDA_FOTOS = fotoAyuda.textContent;
    // Las fotos en orden. Si no se tocan, no se mandan y el servidor las conserva.
    let fotos = [];
    let fotosCambiadas = false;

    function pintarPortada() {
        portadaNombre.style.fontFamily = "'" + (FAMILIAS[tipografiaSel.value] || FAMILIAS.poppins) + "', sans-serif";
        portadaNombre.textContent = fields.nombre.value.trim() || 'Tu gimnasio';
        portadaVista.style.setProperty('--portada-color', colorInput.value);
        let img = portadaVista.querySelector('img');
        if (fotos[0]) {
            if (!img) {
                img = document.createElement('img');
                img.alt = '';
                portadaVista.prepend(img);
            }
            img.src = fotos[0];
        } else if (img) {
            img.remove();
        }
    }

    function pintarFotos() {
        fotosBox.innerHTML = fotos.map((url, i) =>
            '<div class="portada-foto"><img src="' + escapeHtml(url) + '" alt="Foto ' + (i + 1) + ' del gimnasio">'
            + '<div class="portada-acciones">'
            + '<button type="button" data-mover="' + i + '" data-hacia="-1" aria-label="Mover a la izquierda"' + (i === 0 ? ' disabled' : '') + '><i class="bx bx-chevron-left"></i></button>'
            + '<button type="button" data-quitar="' + i + '" aria-label="Quitar foto ' + (i + 1) + '"><i class="bx bx-trash"></i></button>'
            + '<button type="button" data-mover="' + i + '" data-hacia="1" aria-label="Mover a la derecha"' + (i === fotos.length - 1 ? ' disabled' : '') + '><i class="bx bx-chevron-right"></i></button>'
            + '</div></div>').join('');
        fotoInput.disabled = fotos.length >= MAX_FOTOS;
        pintarPortada();
    }

    fotosBox.addEventListener('click', (e) => {
        const boton = e.target.closest('button');
        if (!boton) return;
        if (boton.dataset.quitar !== undefined) {
            fotos.splice(Number(boton.dataset.quitar), 1);
        } else if (boton.dataset.mover !== undefined) {
            const i = Number(boton.dataset.mover);
            const j = i + Number(boton.dataset.hacia);
            if (j < 0 || j >= fotos.length) return;
            [fotos[i], fotos[j]] = [fotos[j], fotos[i]];
        }
        fotosCambiadas = true;
        pintarFotos();
    });

    // Cada foto se reduce a 1280 px y se sube como imagen del gimnasio
    // (POST /api/gyms/{id}/imagenes); al guardar se manda la lista.
    fotoInput.addEventListener('change', async () => {
        const id = localStorage.getItem('gymId');
        const archivos = [...(fotoInput.files || [])].slice(0, MAX_FOTOS - fotos.length);
        fotoInput.value = '';
        if (!archivos.length) return;
        if (!id) {
            alertBox.textContent = 'Guarda primero tu gimnasio y después sube sus fotos.';
            alertBox.classList.remove('d-none');
            return;
        }
        for (const [n, file] of archivos.entries()) {
            fotoAyuda.textContent = 'Subiendo la foto ' + (n + 1) + ' de ' + archivos.length + '…';
            try {
                const dataUrl = await reducirImagen(file, 1280);
                const r = await apiTienda('/api/gyms/' + id + '/imagenes', { method: 'POST', body: JSON.stringify({ dataUrl }) });
                fotos.push(r.url);
                fotosCambiadas = true;
                pintarFotos();
            } catch (err) {
                fotoAyuda.textContent = err.message || 'No se pudo subir la foto.';
                return;
            }
        }
        fotoAyuda.textContent = 'Listas. Guarda los cambios para usarlas en la app.';
        setTimeout(() => { fotoAyuda.textContent = AYUDA_FOTOS; }, 5000);
    });

    tipografiaSel.addEventListener('change', pintarPortada);
    fields.nombre.addEventListener('input', pintarPortada);
    colorInput.addEventListener('input', pintarPortada);

    colorInput.addEventListener('input', () => { colorHex.value = colorInput.value.toUpperCase(); });
    colorHex.addEventListener('input', () => {
        if (/^#[0-9a-fA-F]{6}$/.test(colorHex.value)) colorInput.value = colorHex.value;
    });

    function showLogo(dataUrl) {
        logoDataUrl = dataUrl || null;
        if (logoDataUrl) {
            logoPreview.innerHTML = '<img src="' + logoDataUrl + '" alt="Logo del gimnasio" style="max-width:100%;max-height:100%;object-fit:contain">';
            logoClear.classList.remove('d-none');
        } else {
            logoPreview.innerHTML = '<i class="bx bx-image-add fs-2 text-body-secondary"></i>';
            logoClear.classList.add('d-none');
        }
    }

    logoClear.addEventListener('click', () => { showLogo(null); logoInput.value = ''; });

    // El logo se reduce en el navegador antes de subirlo: así cabe en la
    // base de datos sin necesitar un servicio de almacenamiento aparte.
    logoInput.addEventListener('change', () => {
        const file = logoInput.files && logoInput.files[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = () => {
            const img = new Image();
            img.onload = () => {
                const MAX = 512;
                const escala = Math.min(1, MAX / Math.max(img.width, img.height));
                const canvas = document.createElement('canvas');
                canvas.width = Math.round(img.width * escala);
                canvas.height = Math.round(img.height * escala);
                canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
                showLogo(canvas.toDataURL('image/png'));
            };
            img.onerror = () => {
                alertBox.textContent = 'No pudimos leer esa imagen. Prueba con un PNG o JPG.';
                alertBox.classList.remove('d-none');
            };
            img.src = reader.result;
        };
        reader.readAsDataURL(file);
    });

    const codeCard = document.getElementById('gym-code-card');
    const codeValue = document.getElementById('gym-code-value');

    function showCode(codigo) {
        if (!codigo) return;
        codeValue.textContent = codigo;
        codeCard.classList.remove('d-none');
    }

    document.getElementById('gym-code-copy').addEventListener('click', (e) => {
        navigator.clipboard.writeText(codeValue.textContent.trim()).then(() => {
            const btn = e.currentTarget;
            btn.innerHTML = '<i class="bx bx-check"></i> Copiado';
            setTimeout(() => { btn.innerHTML = '<i class="bx bx-copy"></i> Copiar'; }, 1800);
        });
    });

    document.getElementById('gym-code-regen').addEventListener('click', () => {
        const id = localStorage.getItem('gymId');
        if (!id) return;
        if (!confirm('Se generará un código nuevo y el anterior dejará de funcionar. Quien ya sea miembro no pierde su acceso. ¿Continuar?')) return;
        fetch('/api/gyms/' + id + '/codigo', { method: 'POST' })
            .then(res => res.json())
            .then(data => showCode(data.codigo));
    });

    function setBadge(state) {
        if (state === 'saved') {
            badge.className = 'badge rounded-pill bg-success bg-opacity-10 text-success px-3 py-2';
            badge.innerHTML = '<i class="bx bx-check-circle"></i> Configurado';
        } else {
            badge.className = 'badge rounded-pill bg-warning bg-opacity-10 text-warning px-3 py-2';
            badge.innerHTML = '<i class="bx bx-time-five"></i> Sin configurar';
        }
    }

    const gymId = localStorage.getItem('gymId');
    if (gymId) {
        fetch('/api/gyms/' + gymId)
            .then(res => res.ok ? res.json() : Promise.reject())
            .then(gym => {
                fields.nombre.value = gym.nombre || '';
                fields.telefono.value = gym.telefono || '';
                fields.direccion.value = gym.direccion || '';
                fields.horario.value = gym.horario || '';
                fields.equipamiento.value = gym.equipamiento || '';
                fields.maquinas.value = (gym.maquinas || []).join('\n');
                fields.razonSocial.value = gym.razonSocial || '';
                fields.rfc.value = gym.rfc || '';
                fields.correoContacto.value = gym.correoContacto || '';
                if (gym.colorPrimario) { colorInput.value = gym.colorPrimario; colorHex.value = gym.colorPrimario.toUpperCase(); }
                cuotaInput.value = gym.cuotaMensual != null ? gym.cuotaMensual : '';
                directorioInput.checked = !!gym.enDirectorio;
                showLogo(gym.logo);
                fotos = (gym.fotos && gym.fotos.length ? gym.fotos : (gym.foto ? [gym.foto] : [])).slice(0, MAX_FOTOS);
                tipografiaSel.value = FAMILIAS[gym.tipografia] ? gym.tipografia : 'poppins';
                pintarFotos();
                showCode(gym.codigo);
                setBadge('saved');
                pintarIdentidad(gym.nombre, gym.logo);
            })
            .catch(() => { /* el gimnasio pudo eliminarse; se deja el formulario vacío */ });
    }

    form.addEventListener('submit', (e) => {
        e.preventDefault();
        alertBox.classList.add('d-none');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status"></span> Guardando...';

        fetch('/api/gyms', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                nombre: fields.nombre.value.trim(),
                telefono: fields.telefono.value.trim(),
                direccion: fields.direccion.value.trim(),
                horario: fields.horario.value.trim(),
                equipamiento: fields.equipamiento.value.trim(),
                maquinas: fields.maquinas.value.split('\n').map(s => s.trim()).filter(Boolean),
                logo: logoDataUrl,
                fotos: fotosCambiadas ? fotos : undefined,
                tipografia: tipografiaSel.value,
                colorPrimario: colorInput.value,
                enDirectorio: directorioInput.checked,
                cuotaMensual: cuotaInput.value === '' ? null : parseFloat(cuotaInput.value),
                razonSocial: fields.razonSocial.value.trim(),
                rfc: fields.rfc.value.trim(),
                correoContacto: fields.correoContacto.value.trim(),
                ownerUserId: userId
            })
        })
        .then(res => res.json().then(data => ({ status: res.status, body: data })))
        .then(response => {
            btn.disabled = false;
            btn.innerHTML = '<i class="bx bx-save"></i> Guardar cambios';
            if (response.status === 200) {
                fotosCambiadas = false;
                const primeraVez = !localStorage.getItem('gymId');
                localStorage.setItem('gymId', response.body.id);
                showCode(response.body.codigo);
                setBadge('saved');
                pintarIdentidad(fields.nombre.value.trim(), logoDataUrl);
                // La primera vez se activan todas las pestañas; después solo
                // cambian los precios sugeridos de los planes (salen de la cuota).
                if (primeraVez) {
                    unlockGymFeatures(response.body.id);
                    cargarResumen();
                } else {
                    loadPlanes(response.body.id);
                }
                const guardado = document.getElementById('gym-guardado');
                guardado.classList.remove('d-none');
                clearTimeout(guardado._ocultar);
                guardado._ocultar = setTimeout(() => guardado.classList.add('d-none'), 4000);
            } else {
                alertBox.textContent = response.body.error || 'No se pudo guardar el gimnasio.';
                alertBox.classList.remove('d-none');
            }
        })
        .catch(() => {
            btn.disabled = false;
            btn.innerHTML = '<i class="bx bx-save"></i> Guardar cambios';
            alertBox.textContent = 'Error de conexión con el servidor.';
            alertBox.classList.remove('d-none');
        });
    });
}

// El gimnasio en lo alto del menú: su logo (o sus iniciales) y su nombre.
function pintarIdentidad(nombre, logo) {
    const marca = document.getElementById('panel-gym-logo');
    document.getElementById('panel-gym-nombre').textContent = nombre || 'Tu gimnasio';
    marca.classList.toggle('con-logo', !!logo);
    if (logo) {
        const img = document.createElement('img');
        img.src = logo;
        img.alt = '';
        marca.replaceChildren(img);
    } else if (nombre) {
        marca.textContent = iniciales(nombre);
    } else {
        marca.innerHTML = "<i class='bx bx-dumbbell'></i>";
    }
}

function unlockGymFeatures(gymId) {
    ['routine-new-btn', 'member-new-btn', 'machine-new-btn', 'machine-import-btn', 'producto-nuevo-btn', 'plan-nuevo-btn']
        .forEach(id => { document.getElementById(id).disabled = false; });
    loadRoutines(gymId);
    loadMembers(gymId);
    loadMachines(gymId);
    loadBilling(gymId);
    loadProductos(gymId);
    loadPlanes(gymId);
    loadMostrador(gymId);
}

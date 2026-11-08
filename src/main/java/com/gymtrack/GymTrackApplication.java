package com.gymtrack;

import com.gymtrack.model.GymService;
import com.gymtrack.repository.GymServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// @EnableScheduling activa el cobrador automático de membresías (BillingService),
// que revisa vencimientos todos los días a las 6:00 sin que nadie abra el panel.
// @EnableAsync deja que los correos con recibo se manden en segundo plano.
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class GymTrackApplication {

	private static final Logger log = LoggerFactory.getLogger(GymTrackApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(GymTrackApplication.class, args);
	}

	// Módulos de GymTrack que muestra la portada (colección "servicios"). Los
	// textos dicen lo que ya funciona.
	static List<GymService> catalogo() {
		return List.of(
			new GymService(null, "Plataforma Web",
				"Panel en la nube para administrar tu gimnasio: miembros y altas en recepción, membresías y pagos, máquinas y rutinas, tienda, mostrador, ventas y el control de acceso.",
				0.0, "bx-laptop",
				"https://images.unsplash.com/photo-1460925895917-afdab827c52f?q=80&w=600&auto=format&fit=crop",
				"software", "SaaS", true, true),
			new GymService(null, "Control de Acceso IoT",
				"Lector RFID en la entrada: el socio pasa su tarjeta y se valida al momento si su membresía está al día. Registra entradas y salidas, avisa con luz y buzzer, abre la puerta con un servo y tiene un botón para pedir acceso; tú abres a distancia desde el panel.",
				3500.0, "bx-chip",
				"https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=600&auto=format&fit=crop",
				"hardware", "Pago Único", true, true),
			new GymService(null, "App Móvil para Clientes",
				"App para Android con la que tus socios se unen con tu código, consultan su membresía, compran y pagan en tu tienda, recogen sus pedidos con un QR, siguen sus rutinas y reciben tus avisos.",
				0.0, "bx-mobile-alt",
				"https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?q=80&w=600&auto=format&fit=crop",
				"software", "SaaS", true, true),
			new GymService(null, "Gestión de Cobros",
				"Registra pagos en recepción o deja que tus socios paguen en línea con tarjeta (también a meses sin intereses), PayPal o efectivo en OXXO. Las membresías vencidas se dan de baja solas y avisamos antes del corte.",
				0.0, "bx-credit-card-front",
				"https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?q=80&w=600&auto=format&fit=crop",
				"módulo", "SaaS", false, true),
			new GymService(null, "Creador de Rutinas",
				"Diseña rutinas con las máquinas que de verdad tiene tu gimnasio; tus miembros las ven en la app.",
				0.0, "bx-dumbbell",
				"https://images.unsplash.com/photo-1534438327276-14e5300c3a48?q=80&w=600&auto=format&fit=crop",
				"módulo", "SaaS", false, true),
			new GymService(null, "Analíticas y Reportes",
				"Ventas del día y del mes, ticket promedio, lo más vendido y el desglose por canal y método de pago.",
				0.0, "bx-bar-chart-alt-2",
				"https://images.unsplash.com/photo-1551288049-bebda4e38f71?q=80&w=600&auto=format&fit=crop",
				"software", "SaaS", false, true),
			new GymService(null, "Credenciales RFID",
				"Tarjetas RFID que sirven de llave a tus socios. Las registras desde el panel pasándolas por el lector y quedan ligadas a cada socio.",
				50.0, "bx-id-card",
				"https://images.unsplash.com/photo-1563013544-824ae1b704d3?q=80&w=600&auto=format&fit=crop",
				"hardware", "Por unidad", false, true),
			new GymService(null, "Tienda y Mostrador",
				"Vende suplementos, bebidas y membresías con inventario al día: cobra en el mostrador o en la app, entrega los pedidos escaneando su QR y comparte el comprobante en PDF por WhatsApp o correo.",
				0.0, "bx-store",
				"https://images.unsplash.com/photo-1556740738-b6a63e27c4df?q=80&w=600&auto=format&fit=crop",
				"módulo", "SaaS", false, true),
			new GymService(null, "Avisos y Anuncios",
				"Publica avisos de tu gimnasio y llegan como notificación al celular de tus socios, aunque tengan la app cerrada.",
				0.0, "bx-bell",
				"https://images.unsplash.com/photo-1563986768609-322da13575f3?q=80&w=600&auto=format&fit=crop",
				"módulo", "SaaS", false, true),
			new GymService(null, "Seguridad y Bitácora",
				"Sesiones seguras y una bitácora de inicios de sesión, cambios en el panel y accesos negados, siguiendo la norma ISO 27001.",
				0.0, "bx-shield-quarter",
				"https://images.unsplash.com/photo-1614064641938-3bbee52942c7?q=80&w=600&auto=format&fit=crop",
				"servicio", "Incluido", false, true),
			new GymService(null, "Respaldo en la Nube",
				"Tu información vive en la nube y cada gimnasio ve solo la suya: nada se instala en la computadora de recepción.",
				0.0, "bx-cloud-upload",
				"https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=600&auto=format&fit=crop",
				"servicio", "Incluido", false, true)
		);
	}

	// Sincroniza el catálogo al arrancar, por nombre: agrega los que falten y
	// actualiza los que ya existen. Así un despliegue nuevo (Render) corrige los
	// textos de la portada sin borrar la colección a mano.
	@Bean
	CommandLineRunner initData(GymServiceRepository repository) {
		return args -> {
			Map<String, String> ids = new HashMap<>();
			repository.findAll().forEach(s -> ids.putIfAbsent(s.getNombre(), s.getId()));
			List<GymService> catalogo = catalogo();
			catalogo.forEach(s -> s.setId(ids.get(s.getNombre())));
			repository.saveAll(catalogo);
			log.info("Catálogo de la portada sincronizado: {} módulos.", catalogo.size());
		};
	}
}

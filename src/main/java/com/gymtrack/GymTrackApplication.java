package com.gymtrack;

import com.gymtrack.model.GymService;
import com.gymtrack.repository.GymServiceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class GymTrackApplication {

	public static void main(String[] args) {
		SpringApplication.run(GymTrackApplication.class, args);
	}

	// Inserta datos de prueba en MongoDB Atlas si la colección está vacía
	@Bean
	CommandLineRunner initData(GymServiceRepository repository) {
		return args -> {
			if (repository.count() == 0) {
				repository.saveAll(List.of(
					new GymService(null, "Plataforma Web",
						"Panel de control centralizado en la nube para gestionar clientes, finanzas y configuración de tu gimnasio.",
						0.0, "bx-laptop",
						"https://images.unsplash.com/photo-1460925895917-afdab827c52f?q=80&w=600&auto=format&fit=crop",
						"software", "SaaS", true, true),
					new GymService(null, "Control de Acceso IoT",
						"Módulo de hardware inteligente que se conecta a tu torniquete para validar entradas mediante RFID.",
						3500.0, "bx-chip",
						"https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=600&auto=format&fit=crop",
						"hardware", "Pago Único", true, true),
					new GymService(null, "App Móvil para Clientes",
						"Aplicación nativa para que tus usuarios consulten su membresía, rutinas y progreso.",
						0.0, "bx-mobile-alt",
						"https://images.unsplash.com/photo-1512941937669-90a1b58e7e9c?q=80&w=600&auto=format&fit=crop",
						"software", "SaaS", true, true),
					new GymService(null, "Gestión de Cobros",
						"Sistema automatizado que bloquea el acceso físico a usuarios morosos y notifica vencimientos.",
						0.0, "bx-credit-card-front",
						"https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?q=80&w=600&auto=format&fit=crop",
						"módulo", "SaaS", false, true),
					new GymService(null, "Creador de Rutinas",
						"Herramienta para diseñar rutinas personalizadas basadas en el equipamiento físico de tu sucursal.",
						0.0, "bx-dumbbell",
						"https://images.unsplash.com/photo-1534438327276-14e5300c3a48?q=80&w=600&auto=format&fit=crop",
						"módulo", "SaaS", false, true),
					new GymService(null, "Analíticas y Reportes",
						"Gráficas sobre asistencia, horarios pico, ingresos mensuales y retención de clientes.",
						0.0, "bx-bar-chart-alt-2",
						"https://images.unsplash.com/photo-1551288049-bebda4e38f71?q=80&w=600&auto=format&fit=crop",
						"software", "SaaS", false, true),
					new GymService(null, "Credenciales RFID",
						"Tarjetas o pulseras RFID que funcionan como llave de acceso única y segura para tus clientes.",
						50.0, "bx-id-card",
						"https://images.unsplash.com/photo-1563013544-824ae1b704d3?q=80&w=600&auto=format&fit=crop",
						"hardware", "Por unidad", false, true),
					new GymService(null, "Respaldo en la Nube",
						"Tus datos seguros con arquitectura Multi-tenant, respaldos automáticos y soporte técnico.",
						0.0, "bx-cloud-upload",
						"https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=600&auto=format&fit=crop",
						"servicio", "24/7", false, true)
				));
				System.out.println("✅ Datos de prueba insertados en MongoDB (8 módulos de GymTrack SaaS)");
			}
		};
	}
}

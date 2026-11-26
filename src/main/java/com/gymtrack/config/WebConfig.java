package com.gymtrack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// La app móvil (Expo, en el emulador o en un dispositivo) llama a la API
// desde un origen distinto al del sitio web, así que se habilita CORS
// solo para /api/** — los recursos estáticos servidos por Spring no lo necesitan.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SuscripcionInterceptor suscripcion;

    public WebConfig(SuscripcionInterceptor suscripcion) {
        this.suscripcion = suscripcion;
    }

    // Panel en solo lectura cuando la suscripción a GymTrack venció.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(suscripcion).addPathPatterns("/api/gyms/*/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}

package com.gymtrack.config;

import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

// Arma el cliente SMTP con las propiedades spring.mail.* de application.properties.
// Existe por un detalle: Google muestra la contraseña de aplicación en grupos de
// cuatro ("abcd efgh ijkl mnop"); aquí se le quitan los espacios para que funcione
// igual si se pega así en el .env.
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class CorreoConfig {

    @Bean
    public JavaMailSenderImpl mailSender(MailProperties props) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(props.getHost());
        if (props.getPort() != null) sender.setPort(props.getPort());
        sender.setProtocol(props.getProtocol());
        if (props.getDefaultEncoding() != null) sender.setDefaultEncoding(props.getDefaultEncoding().name());

        String usuario = props.getUsername();
        String contrasena = props.getPassword();
        sender.setUsername(usuario == null || usuario.isBlank() ? null : usuario.trim());
        sender.setPassword(contrasena == null || contrasena.isBlank() ? null : contrasena.replaceAll("\\s", ""));

        Properties javaMail = new Properties();
        javaMail.putAll(props.getProperties());
        sender.setJavaMailProperties(javaMail);
        return sender;
    }
}

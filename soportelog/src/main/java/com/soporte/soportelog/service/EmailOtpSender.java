package com.soporte.soportelog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.soporte.soportelog.service.exception.OtpDeliveryException;

/*
 * Envia el codigo por correo usando el servidor SMTP configurado en
 * spring.mail.* (Gmail). Se usa en dev y prod; en test se usa ConsoleOtpSender.
 */
@Service
@Profile("!test")
public class EmailOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(EmailOtpSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final long expirationMinutes;

    @Autowired
    public EmailOtpSender(JavaMailSender mailSender,
            @Value("${otp.mail.from}") String from,
            @Value("${otp.expiration-minutes}") long expirationMinutes) {
        this.mailSender = mailSender;
        this.from = from;
        this.expirationMinutes = expirationMinutes;
    }

    @Override
    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(this.from);
        message.setTo(email);
        // El codigo va en el asunto para que se lea en la notificacion del celular
        message.setSubject(code + " es tu codigo de Soportelog");
        message.setText("""
                Tu codigo de acceso a Soportelog es: %s

                Caduca en %d minutos. No lo compartas con nadie.
                Si no pediste este codigo, ignora este correo.
                """.formatted(code, this.expirationMinutes));

        try {
            this.mailSender.send(message);
        } catch (MailException e) {
            // La causa real (credenciales vacias, rechazadas, sin red...) queda en
            // el log del servidor; al cliente solo le llega un mensaje generico
            log.warn("No se pudo enviar el codigo OTP: {}", e.getMessage());
            // Se convierte en una excepcion propia: OtpService hace rollback y
            // el controlador responde 503 sin mostrar detalles del servidor SMTP
            throw new OtpDeliveryException(e);
        }
    }
}

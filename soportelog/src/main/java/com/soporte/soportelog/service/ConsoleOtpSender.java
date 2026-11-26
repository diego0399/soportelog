package com.soporte.soportelog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/*
 * Solo para las pruebas automaticas: en lugar de enviar el correo escribe el
 * codigo en la consola. En dev y prod se usa EmailOtpSender.
 */
@Service
@Profile("test")
public class ConsoleOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(ConsoleOtpSender.class);

    @Override
    public void send(String email, String code) {
        log.info("Codigo OTP para {}: {}", email, code);
    }
}

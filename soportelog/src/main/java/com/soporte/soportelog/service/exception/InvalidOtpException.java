package com.soporte.soportelog.service.exception;

/*
 * Un solo mensaje para todos los casos (codigo incorrecto, expirado, ya usado,
 * sin intentos) para no dar pistas a quien intenta adivinarlo.
 */
public class InvalidOtpException extends RuntimeException {
    public InvalidOtpException() {
        super("Codigo invalido o expirado");
    }
}

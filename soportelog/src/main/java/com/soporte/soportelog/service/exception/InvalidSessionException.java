package com.soporte.soportelog.service.exception;

/*
 * Un solo mensaje para todos los casos (token desconocido, sesion cerrada,
 * sesion caducada por inactividad), igual que con los codigos OTP.
 */
public class InvalidSessionException extends RuntimeException {
    public InvalidSessionException() {
        super("Sesion invalida o cerrada");
    }
}

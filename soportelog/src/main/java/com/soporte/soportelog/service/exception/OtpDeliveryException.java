package com.soporte.soportelog.service.exception;

// El codigo se genero pero no se pudo entregar (servidor de correo caido,
// credenciales malas, etc.)
public class OtpDeliveryException extends RuntimeException {
    public OtpDeliveryException(Throwable cause) {
        super("No se pudo enviar el codigo, intenta de nuevo en unos minutos", cause);
    }
}

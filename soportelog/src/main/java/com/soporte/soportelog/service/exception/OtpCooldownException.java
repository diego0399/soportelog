package com.soporte.soportelog.service.exception;

public class OtpCooldownException extends RuntimeException {
    private final long retryAfterSeconds;

    public OtpCooldownException(long retryAfterSeconds) {
        super("Espera " + retryAfterSeconds + " segundos para pedir otro codigo");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

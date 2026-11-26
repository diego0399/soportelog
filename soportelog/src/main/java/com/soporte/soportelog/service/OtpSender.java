package com.soporte.soportelog.service;

/*
 * Canal por el que el codigo llega al usuario (por ahora, correo).
 * OtpService solo conoce esta interfaz; como se envia de verdad lo decide
 * la clase que la implemente.
 */
public interface OtpSender {
    void send(String email, String code);
}

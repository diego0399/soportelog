package com.soporte.soportelog.service.dto;

/*
 * Lo que recibe el dispositivo al iniciar sesion o al renovar:
 * - accessToken: el JWT que se envia en cada peticion (Authorization: Bearer ...)
 * - refreshToken: la llave de la sesion, para pedir nuevos accessToken
 * - expiresInSeconds: cuanto dura el accessToken; antes de que pase ese tiempo
 *   el dispositivo debe renovarlo
 */
public record AuthTokensDto(String accessToken, String refreshToken, long expiresInSeconds) {
}

package com.soporte.soportelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.soporte.soportelog.persistence.entity.OtpCodeEntity;
import com.soporte.soportelog.persistence.entity.SessionEntity;
import com.soporte.soportelog.persistence.repository.OtpCodeRepository;
import com.soporte.soportelog.persistence.repository.SessionRepository;
import com.soporte.soportelog.service.OtpSender;
import com.soporte.soportelog.service.exception.OtpDeliveryException;
import com.soporte.soportelog.web.config.JwtUtil;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OtpAuthFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OtpCodeRepository otpCodeRepository;

	@Autowired
	private SessionRepository sessionRepository;

	@Value("${jwt.secret}")
	private String jwtSecret;

	// Sustituye el envio real para poder leer el codigo que se "envio"
	@MockitoBean
	private OtpSender otpSender;

	// ---------- Codigo OTP ----------

	@Test
	void codigoCorrectoIniciaSesionYSoloSirveUnaVez() throws Exception {
		String email = "usuario1@prueba.com";
		String code = requestCode(email);

		String body = verifyCode(email, code)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.expiresInSeconds").value(900))
				.andReturn().getResponse().getContentAsString();
		String accessToken = JsonPath.read(body, "$.accessToken");

		me(accessToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));

		// El mismo codigo ya no vale por segunda vez
		verifyCode(email, code).andExpect(status().isUnauthorized());
	}

	@Test
	void elCorreoNoDistingueMayusculas() throws Exception {
		String code = requestCode("usuario2@prueba.com");

		String body = verifyCode("Usuario2@PRUEBA.com", code)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		me(JsonPath.read(body, "$.accessToken"))
				.andExpect(jsonPath("$.email").value("usuario2@prueba.com"));
	}

	@Test
	void siElCorreoNoSePuedeEnviarRespondeServicioNoDisponibleYNoGuardaElCodigo() throws Exception {
		String email = "sin-entrega@prueba.com";
		doThrow(new OtpDeliveryException(new RuntimeException("SMTP caido")))
				.when(this.otpSender).send(eq(email), anyString());

		this.mockMvc.perform(post("/api/auth/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.message").isNotEmpty());

		// Rollback: no queda un codigo que el usuario nunca recibio, asi que
		// tampoco tiene que esperar 60 segundos para reintentar
		assertThat(this.otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(email)).isEmpty();
	}

	// ---------- CORS (llamadas desde el navegador) ----------

	@Test
	void elNavegadorPuedeLlamarDesdeLocalhostConCualquierPuerto() throws Exception {
		this.mockMvc.perform(options("/api/auth/otp/request")
				.header(HttpHeaders.ORIGIN, "http://localhost:51760")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:51760"));
	}

	@Test
	void elNavegadorNoPuedeLlamarDesdeUnSitioNoPermitido() throws Exception {
		this.mockMvc.perform(options("/api/auth/otp/request")
				.header(HttpHeaders.ORIGIN, "https://sitio-malicioso.com")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void elCodigoSeGuardaComoHashYNoEnClaro() throws Exception {
		String email = "usuario3@prueba.com";
		String code = requestCode(email);

		OtpCodeEntity saved = this.otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(email).orElseThrow();
		assertThat(saved.getCodeHash()).isNotEqualTo(code).doesNotContain(code);
	}

	@Test
	void trasCincoIntentosFallidosNiElCodigoCorrectoSirve() throws Exception {
		String email = "usuario4@prueba.com";
		String code = requestCode(email);
		String wrongCode = code.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 5; i++) {
			verifyCode(email, wrongCode).andExpect(status().isUnauthorized());
		}

		verifyCode(email, code).andExpect(status().isUnauthorized());
	}

	@Test
	void codigoExpiradoEsRechazado() throws Exception {
		String email = "usuario5@prueba.com";
		String code = requestCode(email);

		OtpCodeEntity saved = this.otpCodeRepository.findFirstByEmailOrderByCreatedAtDesc(email).orElseThrow();
		saved.setExpiresAt(Instant.now().minusSeconds(1));
		this.otpCodeRepository.save(saved);

		verifyCode(email, code).andExpect(status().isUnauthorized());
	}

	@Test
	void noSePuedePedirOtroCodigoAntesDelTiempoDeEspera() throws Exception {
		String email = "usuario6@prueba.com";
		requestCode(email);

		this.mockMvc.perform(post("/api/auth/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
	}

	@Test
	void verificarSinHaberPedidoCodigoEsRechazado() throws Exception {
		verifyCode("nadie@prueba.com", "123456").andExpect(status().isUnauthorized());
	}

	@Test
	void correoOCodigoConFormatoInvalidoDevuelve400() throws Exception {
		this.mockMvc.perform(post("/api/auth/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"esto-no-es-un-correo\"}"))
				.andExpect(status().isBadRequest());

		verifyCode("usuario7@prueba.com", "12ab").andExpect(status().isBadRequest());
	}

	@Test
	void endpointProtegidoExigeTokenValido() throws Exception {
		this.mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized());

		me("token-falso").andExpect(status().isUnauthorized());
	}

	// ---------- Sesiones por dispositivo ----------

	@Test
	void alVerificarSeAbreUnaSesionYElTokenDeRenovacionSeGuardaComoHash() throws Exception {
		String email = "usuario8@prueba.com";
		String refreshToken = JsonPath.read(login(email, "Telefono de pruebas"), "$.refreshToken");

		List<SessionEntity> sessions = openSessions(email);
		assertThat(sessions).hasSize(1);
		assertThat(sessions.get(0).getDeviceName()).isEqualTo("Telefono de pruebas");
		assertThat(sessions.get(0).getRefreshTokenHash()).hasSize(64).isNotEqualTo(refreshToken);
	}

	@Test
	void dosDispositivosDelMismoUsuarioTienenDosSesionesDistintas() throws Exception {
		String email = "usuario9@prueba.com";
		String first = login(email, "Telefono");
		// Se borra el codigo anterior para no esperar los 60 segundos entre un login y otro
		this.otpCodeRepository.deleteAllByEmail(email);
		String second = login(email, "Laptop");

		assertThat((String) JsonPath.read(first, "$.refreshToken"))
				.isNotEqualTo(JsonPath.read(second, "$.refreshToken"));
		assertThat(openSessions(email)).extracting(SessionEntity::getDeviceName)
				.containsExactlyInAnyOrder("Telefono", "Laptop");
	}

	// ---------- Renovar el token ----------

	@Test
	void conElTokenDeRenovacionSeObtieneOtroTokenDeAccesoSinPedirCodigo() throws Exception {
		String email = "usuario10@prueba.com";
		String refreshToken = JsonPath.read(login(email, "Telefono"), "$.refreshToken");

		String body = refresh(refreshToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").value(refreshToken))
				.andExpect(jsonPath("$.expiresInSeconds").value(900))
				.andReturn().getResponse().getContentAsString();

		me(JsonPath.read(body, "$.accessToken"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void tokenDeAccesoCaducadoEsRechazadoPeroLaSesionSePuedeRenovar() throws Exception {
		String email = "usuario11@prueba.com";
		String refreshToken = JsonPath.read(login(email, "Telefono"), "$.refreshToken");
		Long sessionId = openSessions(email).get(0).getId();

		// Un token firmado con la misma clave pero ya caducado (duracion negativa)
		String expired = new JwtUtil(this.jwtSecret, -1).create(email, sessionId);

		me(expired).andExpect(status().isUnauthorized());
		refresh(refreshToken).andExpect(status().isOk());
	}

	@Test
	void tokenDeRenovacionDesconocidoEsRechazado() throws Exception {
		refresh("este-token-no-existe").andExpect(status().isUnauthorized());
	}

	@Test
	void sesionCerradaNoSePuedeRenovar() throws Exception {
		String email = "usuario12@prueba.com";
		String refreshToken = JsonPath.read(login(email, "Telefono"), "$.refreshToken");

		SessionEntity session = openSessions(email).get(0);
		session.setRevoked(true);
		this.sessionRepository.save(session);

		refresh(refreshToken).andExpect(status().isUnauthorized());
	}

	@Test
	void sesionSinUsarDuranteMasDe180DiasNoSePuedeRenovar() throws Exception {
		String email = "usuario13@prueba.com";
		String refreshToken = JsonPath.read(login(email, "Telefono"), "$.refreshToken");

		SessionEntity session = openSessions(email).get(0);
		session.setLastUsedAt(Instant.now().minus(Duration.ofDays(181)));
		this.sessionRepository.save(session);

		refresh(refreshToken).andExpect(status().isUnauthorized());
	}

	// ---------- Cerrar sesion ----------

	@Test
	void alCerrarSesionElTokenDeAccesoYElDeRenovacionDejanDeValerAlInstante() throws Exception {
		String email = "usuario14@prueba.com";
		String body = login(email, "Telefono");
		String accessToken = JsonPath.read(body, "$.accessToken");
		String refreshToken = JsonPath.read(body, "$.refreshToken");

		me(accessToken).andExpect(status().isOk());

		logout(accessToken).andExpect(status().isNoContent());

		me(accessToken).andExpect(status().isUnauthorized());
		refresh(refreshToken).andExpect(status().isUnauthorized());
	}

	@Test
	void cerrarSesionEnUnDispositivoNoAfectaAlOtro() throws Exception {
		String email = "usuario15@prueba.com";
		String phoneTokens = login(email, "Telefono");
		this.otpCodeRepository.deleteAllByEmail(email);
		String laptopTokens = login(email, "Laptop");

		logout(JsonPath.read(phoneTokens, "$.accessToken")).andExpect(status().isNoContent());

		me(JsonPath.read(laptopTokens, "$.accessToken")).andExpect(status().isOk());
		refresh(JsonPath.read(laptopTokens, "$.refreshToken")).andExpect(status().isOk());
		assertThat(openSessions(email)).extracting(SessionEntity::getDeviceName).containsExactly("Laptop");
	}

	@Test
	void cerrarSesionSinTokenEsRechazado() throws Exception {
		this.mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
	}

	// ---------- Lista de dispositivos ----------

	@Test
	void laListaMuestraLosDispositivosDelUsuarioYMarcaElActual() throws Exception {
		String email = "usuario16@prueba.com";
		String phoneTokens = login(email, "Telefono");
		this.otpCodeRepository.deleteAllByEmail(email);
		login(email, "Laptop");

		sessions(JsonPath.read(phoneTokens, "$.accessToken"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.deviceName == 'Telefono')].current").value(true))
				.andExpect(jsonPath("$[?(@.deviceName == 'Laptop')].current").value(false))
				// El hash del token de renovacion nunca sale del servidor
				.andExpect(jsonPath("$[0].refreshTokenHash").doesNotExist());
	}

	@Test
	void desdeUnDispositivoSePuedeCerrarLaSesionDeOtro() throws Exception {
		String email = "usuario17@prueba.com";
		String phoneAccess = JsonPath.read(login(email, "Telefono"), "$.accessToken");
		this.otpCodeRepository.deleteAllByEmail(email);
		String laptopAccess = JsonPath.read(login(email, "Laptop"), "$.accessToken");
		Long laptopSessionId = sessionIdOf(email, "Laptop");

		closeSession(phoneAccess, laptopSessionId).andExpect(status().isNoContent());

		me(laptopAccess).andExpect(status().isUnauthorized());
		me(phoneAccess).andExpect(status().isOk());
		sessions(phoneAccess).andExpect(jsonPath("$.length()").value(1));
		// Cerrarla por segunda vez ya no encuentra nada
		closeSession(phoneAccess, laptopSessionId).andExpect(status().isNotFound());
	}

	@Test
	void unUsuarioNoPuedeVerNiCerrarLasSesionesDeOtro() throws Exception {
		String victimEmail = "victima@prueba.com";
		String victimAccess = JsonPath.read(login(victimEmail, "Telefono de la victima"), "$.accessToken");
		Long victimSessionId = sessionIdOf(victimEmail, "Telefono de la victima");
		String attackerAccess = JsonPath.read(login("atacante@prueba.com", "Telefono del atacante"),
				"$.accessToken");

		closeSession(attackerAccess, victimSessionId).andExpect(status().isNotFound());

		me(victimAccess).andExpect(status().isOk());
		sessions(attackerAccess)
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].deviceName").value("Telefono del atacante"));
	}

	@Test
	void laListaDeSesionesExigeToken() throws Exception {
		this.mockMvc.perform(get("/api/auth/sessions")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(delete("/api/auth/sessions/1")).andExpect(status().isUnauthorized());
	}

	// ---------- Ayudas ----------

	private String requestCode(String email) throws Exception {
		this.mockMvc.perform(post("/api/auth/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.expiresInSeconds").value(300))
				.andExpect(jsonPath("$.resendInSeconds").value(60));

		// Si a ese correo se le envio mas de un codigo, getValue() devuelve el ultimo
		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(this.otpSender, atLeastOnce()).send(eq(email), code.capture());
		assertThat(code.getValue()).matches("\\d{6}");
		return code.getValue();
	}

	private ResultActions verifyCode(String email, String code) throws Exception {
		return this.mockMvc.perform(post("/api/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
	}

	// Login completo desde un dispositivo: pide el codigo y lo verifica. Devuelve el JSON con los tokens
	private String login(String email, String deviceName) throws Exception {
		String code = requestCode(email);
		return this.mockMvc.perform(post("/api/auth/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"deviceName\":\"" + deviceName + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return this.mockMvc.perform(post("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private ResultActions me(String accessToken) throws Exception {
		return this.mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private ResultActions logout(String accessToken) throws Exception {
		return this.mockMvc
				.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private ResultActions sessions(String accessToken) throws Exception {
		return this.mockMvc
				.perform(get("/api/auth/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private ResultActions closeSession(String accessToken, Long sessionId) throws Exception {
		return this.mockMvc.perform(delete("/api/auth/sessions/" + sessionId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
	}

	private Long sessionIdOf(String email, String deviceName) {
		return openSessions(email).stream()
				.filter(session -> deviceName.equals(session.getDeviceName()))
				.findFirst().orElseThrow().getId();
	}

	private List<SessionEntity> openSessions(String email) {
		return this.sessionRepository.findByUserEmailAndRevokedFalseOrderByLastUsedAtDesc(email);
	}
}

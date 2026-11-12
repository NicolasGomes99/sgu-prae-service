package br.edu.ufape.sguPraeService.auth;

import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoEvent;
import br.edu.ufape.sguPraeService.config.EnvironmentSecurityValidator;
import br.edu.ufape.sguPraeService.config.KeycloakJwtAuthenticationConverter;
import br.edu.ufape.sguPraeService.config.RedisConfig;
import br.edu.ufape.sguPraeService.config.filters.TokenResponseRewriteFilter;
import br.edu.ufape.sguPraeService.exceptions.GlobalAccessDeniedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SecurityAndAuthInfrastructureTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockJwtInSecurityContext(UUID userId, String... roles) {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("preferred_username", "usuario@ufape.edu.br")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority(r.startsWith("ROLE_") ? r : "ROLE_" + r))
                .toList();

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("AuthenticatedUserProvider: Deve extrair UUID do usuário autenticado e validar roles corretamente")
    void deveExtrairUserIdEValidarRoles() {
        UUID expectedId = UUID.randomUUID();
        mockJwtInSecurityContext(expectedId, "ESTUDANTE");

        AuthenticatedUserProvider provider = new AuthenticatedUserProvider();

        assertEquals(expectedId, provider.getUserId());
        assertTrue(provider.hasAnyRole("ESTUDANTE"));
        assertTrue(provider.hasAnyRole("GESTOR", "estudante"));
        assertFalse(provider.hasAnyRole("GESTOR", "ADMINISTRADOR"));
    }

    @Test
    @DisplayName("AuthenticatedUserProvider: Deve permitir acesso ao titular ou gestor/admin e bloquear terceiros (Anti-IDOR)")
    void deveValidarOwnershipOuRolesContraIdor() {
        UUID titularId = UUID.randomUUID();
        UUID outroEstudanteId = UUID.randomUUID();
        AuthenticatedUserProvider provider = new AuthenticatedUserProvider();

        // 1. Próprio estudante titular acessando seu recurso -> Permitido
        mockJwtInSecurityContext(titularId, "ESTUDANTE");
        assertDoesNotThrow(() -> provider.validateOwnershipOrRoles(titularId, "GESTOR", "ADMINISTRADOR"));

        // 2. Outro estudante tentando acessar recurso de titularId -> Bloqueado (403 GlobalAccessDeniedException)
        mockJwtInSecurityContext(outroEstudanteId, "ESTUDANTE");
        assertThrows(GlobalAccessDeniedException.class,
                () -> provider.validateOwnershipOrRoles(titularId, "GESTOR", "ADMINISTRADOR"));

        // 3. Gestor acessando recurso de estudante -> Permitido
        mockJwtInSecurityContext(outroEstudanteId, "GESTOR");
        assertDoesNotThrow(() -> provider.validateOwnershipOrRoles(titularId, "GESTOR", "ADMINISTRADOR"));
    }

    @Test
    @DisplayName("KeycloakJwtAuthenticationConverter: Deve converter roles do realm_access para ROLE_UPPERCASE")
    void deveConverterRolesDoKeycloakParaSpringSecurity() {
        KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("preferred_username", "gestor@ufape.edu.br")
                .claim("realm_access", Map.of("roles", List.of("gestor", "profissional")))
                .build();

        AbstractAuthenticationToken auth = converter.convert(jwt);
        assertNotNull(auth);
        assertEquals("gestor@ufape.edu.br", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_GESTOR")));
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PROFISSIONAL")));
    }

    @Test
    @DisplayName("TokenResponseRewriteFilter: Deve injetar Authorization Bearer a partir do cookie accessToken em rotas restritas")
    void deveInjetarHeaderAuthorizationDeCookieEmRotaProtegida() throws Exception {
        TokenResponseRewriteFilter filter = new TokenResponseRewriteFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/estudante/current");
        request.setCookies(new Cookie("accessToken", "meu-jwt-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedHeader = new AtomicReference<>();
        AtomicReference<String> capturedLowerHeader = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            HttpServletRequest httpReq = (HttpServletRequest) req;
            capturedHeader.set(httpReq.getHeader(HttpHeaders.AUTHORIZATION));
            capturedLowerHeader.set(httpReq.getHeader("authorization"));
        };

        filter.doFilter(request, response, chain);

        assertEquals("Bearer meu-jwt-token", capturedHeader.get());
        assertEquals("Bearer meu-jwt-token", capturedLowerHeader.get());
    }

    @Test
    @DisplayName("TokenResponseRewriteFilter: NÃO deve injetar accessToken expirado em /refresh, mas deve injetar refresh_token")
    void naoDeveInjetarAccessTokenNoRefreshMasDeveInjetarRefreshToken() throws Exception {
        TokenResponseRewriteFilter filter = new TokenResponseRewriteFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/refresh");
        request.setCookies(
                new Cookie("accessToken", "token-expirado"),
                new Cookie("refreshToken", "meu-refresh-valido")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        AtomicReference<String> capturedRefreshParam = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            HttpServletRequest httpReq = (HttpServletRequest) req;
            capturedAuthHeader.set(httpReq.getHeader(HttpHeaders.AUTHORIZATION));
            capturedRefreshParam.set(httpReq.getParameter("refresh_token"));
        };

        filter.doFilter(request, response, chain);

        assertNull(capturedAuthHeader.get(), "Não deve injetar Authorization em /refresh para evitar 401 por token expirado");
        assertEquals("meu-refresh-valido", capturedRefreshParam.get());
    }

    @Test
    @DisplayName("TokenResponseRewriteFilter: Deve reescrever corpo JSON de /login ocultando access_token e refresh_token")
    void deveReescreverCorpoDeLoginOcultandoTokens() throws Exception {
        TokenResponseRewriteFilter filter = new TokenResponseRewriteFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"123\",\"exp\":1893456000}".getBytes(StandardCharsets.UTF_8));
        String fakeJwt = "eyJhbGciOiJSUzI1NiJ9." + payload + ".assinatura";

        FilterChain chain = (req, res) -> {
            res.setContentType("application/json");
            res.getWriter().write("{\"access_token\":\"" + fakeJwt + "\",\"refresh_token\":\"ref-123\"}");
        };

        filter.doFilter(request, response, chain);

        String body = response.getContentAsString();
        assertTrue(body.contains("\"message\":\"Login bem-sucedido\""));
        assertTrue(body.contains("\"exp\":1893456000"));
        assertFalse(body.contains("access_token"), "O access_token não deve vazar no corpo JSON reescrito");
        assertFalse(body.contains("refresh_token"), "O refresh_token não deve vazar no corpo JSON reescrito");
    }

    @Test
    @DisplayName("EnvironmentSecurityValidator: Deve bloquear inicialização em produção se usar segredos de dev")
    void deveBloquearSegredosDeDesenvolvimentoEmProducao() {
        EnvironmentSecurityValidator validator = new EnvironmentSecurityValidator();

        // 1. Secret legado ou secret local de dev ou placeholder não expandido -> Falha
        ReflectionTestUtils.setField(validator, "keycloakClientSecret", "HntTWny9OT0d3sCBc9qwc6asCNuHXY5e");
        ReflectionTestUtils.setField(validator, "dbPassword", "SenhaForteProd#2026");
        ReflectionTestUtils.setField(validator, "rabbitPassword", "RabbitProd#2026");
        ReflectionTestUtils.setField(validator, "cookieSecure", true);

        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));

        ReflectionTestUtils.setField(validator, "keycloakClientSecret", "sgu-prae-local-dev-secret");
        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));

        ReflectionTestUtils.setField(validator, "keycloakClientSecret", "${KEYCLOAK_CLIENT_SECRET}");
        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));

        // 2. Senha padrão do Postgres ('password') -> Falha
        ReflectionTestUtils.setField(validator, "keycloakClientSecret", "SecretExclusivoDeProducao999");
        ReflectionTestUtils.setField(validator, "dbPassword", "password");
        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));

        // 3. Senha padrão do RabbitMQ ('guest') -> Falha
        ReflectionTestUtils.setField(validator, "dbPassword", "SenhaForteProd#2026");
        ReflectionTestUtils.setField(validator, "rabbitPassword", "guest");
        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));

        // 4. Cookie Secure = false em produção -> Falha
        ReflectionTestUtils.setField(validator, "rabbitPassword", "RabbitProd#2026");
        ReflectionTestUtils.setField(validator, "cookieSecure", false);
        assertThrows(IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));

        // 5. Configuração íntegra de produção -> Sucesso
        ReflectionTestUtils.setField(validator, "cookieSecure", true);
        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    @DisplayName("RedisConfig: Deve serializar e desserializar record NotificacaoEvent preservando tipo e bloqueando classes externas")
    @SuppressWarnings("unchecked")
    void deveSerializarEDesserializarNotificacaoEventComSeguranca() {
        RedisConfig redisConfig = new RedisConfig();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisTemplate<String, Object> template = redisConfig.redisTemplate(connectionFactory);

        RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getHashValueSerializer();
        assertNotNull(serializer);

        UUID destId = UUID.randomUUID();
        NotificacaoEvent original = NotificacaoEvent.paraUsuario(destId, "Título Teste", "Mensagem Teste", "AGENDAMENTO");

        byte[] bytes = serializer.serialize(original);
        assertNotNull(bytes);

        Object deserialized = serializer.deserialize(bytes);
        assertInstanceOf(NotificacaoEvent.class, deserialized);
        NotificacaoEvent recuperado = (NotificacaoEvent) deserialized;
        assertEquals(original.id(), recuperado.id());
        assertEquals(destId, recuperado.destinatarioId());
        assertEquals("Título Teste", recuperado.titulo());

        // Tentativa de desserializar gadget malicioso fora da whitelist deve falhar
        byte[] maliciousPayload = "[\"javax.naming.InitialContext\",{\"environment\":{}}]".getBytes(StandardCharsets.UTF_8);
        assertThrows( Exception.class, () -> serializer.deserialize(maliciousPayload));
    }

    @Test
    @DisplayName("KeycloakDevEnvironmentSynchronizer: Deve sincronizar automaticamente secret divergente no Keycloak local (Auto-Heal)")
    void deveSincronizarClientSecretAutomaticamenteNoAmbienteDeTestes() {
        br.edu.ufape.sguPraeService.servicos.interfaces.KeycloakService keycloakService =
                mock(br.edu.ufape.sguPraeService.servicos.interfaces.KeycloakService.class);
        org.keycloak.admin.client.Keycloak mockAdminKeycloak =
                mock(org.keycloak.admin.client.Keycloak.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        org.keycloak.admin.client.resource.ClientResource mockClientResource =
                mock(org.keycloak.admin.client.resource.ClientResource.class);

        org.keycloak.representations.idm.ClientRepresentation clientRep =
                new org.keycloak.representations.idm.ClientRepresentation();
        clientRep.setId("client-uuid-123");
        clientRep.setClientId("sgu-application");

        org.keycloak.representations.idm.CredentialRepresentation oldSecret =
                new org.keycloak.representations.idm.CredentialRepresentation();
        oldSecret.setValue("HntTWny9OT0d3sCBc9qwc6asCNuHXY5e");

        org.mockito.Mockito.when(mockAdminKeycloak.realm("sgu-ufape").clients().findByClientId("sgu-application"))
                .thenReturn(List.of(clientRep));
        org.mockito.Mockito.when(mockAdminKeycloak.realm("sgu-ufape").clients().get("client-uuid-123"))
                .thenReturn(mockClientResource);
        org.mockito.Mockito.when(mockClientResource.getSecret()).thenReturn(oldSecret);

        br.edu.ufape.sguPraeService.config.KeycloakDevEnvironmentSynchronizer synchronizer =
                new br.edu.ufape.sguPraeService.config.KeycloakDevEnvironmentSynchronizer(keycloakService) {
                    @Override
                    protected org.keycloak.admin.client.Keycloak createAdminClient() {
                        return mockAdminKeycloak;
                    }
                };

        ReflectionTestUtils.setField(synchronizer, "realm", "sgu-ufape");
        ReflectionTestUtils.setField(synchronizer, "clientId", "sgu-application");
        ReflectionTestUtils.setField(synchronizer, "expectedClientSecret", "sgu-prae-local-dev-secret");

        synchronizer.run(new DefaultApplicationArguments());

        assertEquals("sgu-prae-local-dev-secret", clientRep.getSecret());
        org.mockito.Mockito.verify(mockClientResource).update(clientRep);
        org.mockito.Mockito.verify(keycloakService).init();
    }
}

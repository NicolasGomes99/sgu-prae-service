package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.auth.TokenResponse;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "01. Autenticação e Sessão")
public class AuthController {

    private final Fachada fachada;

    @Value("${common.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${common.cookie-same-site:Lax}")
    private String cookieSameSite;

    @Operation(
            summary = "Autenticar usuário (Login)",
            description = """
                    Autentica o usuário junto ao Keycloak utilizando e-mail e senha.
                    - **Público**: Não requer autenticação prévia.
                    - **Comportamento para Web/SPA**: Grava os tokens em cookies `HttpOnly` (`access_token` de 15 min e `refresh_token` de 7 dias) e o filtro `TokenResponseRewriteFilter` remove os tokens do corpo JSON para proteção contra XSS.
                    - **Flags de Segurança**: A flag `Secure` e `SameSite` dos cookies se adapta automaticamente ao perfil ativo (`dev`/`docker` vs `prod`).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticação realizada com sucesso e cookies de sessão definidos."),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas (e-mail ou senha incorretos)."),
            @ApiResponse(responseCode = "503", description = "Serviço de identidade (Keycloak) temporariamente indisponível.")
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Parameter(description = "E-mail institucional ou cadastrado do usuário", required = true, example = "estudante@ufape.edu.br")
            @RequestParam("email") String username,
            @Parameter(description = "Senha do usuário", required = true)
            @RequestParam("senha") String password,
            HttpServletResponse response) {

        TokenResponse tokenData = fachada.login(username, password);

        // 1. Embutindo o Refresh Token no Cookie (flag 'secure' controlada automaticamente pelo profile dev/docker vs prod)
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokenData.getRefresh_token())
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(7 * 24 * 3600) // 7 dias
                .sameSite(cookieSameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // 2. Embutindo o Access Token no Cookie
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokenData.getAccess_token())
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(15 * 60) // 15 minutos
                .sameSite(cookieSameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        return ResponseEntity.ok(tokenData);
    }

    @Operation(
            summary = "Renovar token de acesso (Refresh Token)",
            description = """
                    Renova o `access_token` e o `refresh_token` utilizando o cookie `refreshToken` (ou `refresh_token`) enviado automaticamente pelo navegador, ou via parâmetro de query `refresh_token`.
                    - **Público**: Aceita o refresh token mesmo após a expiração do `access_token`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessão renovada com sucesso e novos cookies emitidos."),
            @ApiResponse(responseCode = "401", description = "Refresh token expirado, inválido ou revogado.")
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Parameter(description = "Cookie HttpOnly de refresh token enviado automaticamente pelo browser")
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @Parameter(description = "Parâmetro opcional de fallback caso o cliente não utilize cookies")
            @RequestParam(value = "refresh_token", required = false) String paramRefreshToken,
            HttpServletResponse response) {

        // Pega o token do cookie (automático do browser) ou do parâmetro (fallback)
        String refreshToken = (cookieRefreshToken != null) ? cookieRefreshToken : paramRefreshToken;

        TokenResponse tokenData = fachada.refresh(refreshToken);

        // Renova os Cookies na resposta
        ResponseCookie newRefreshCookie = ResponseCookie.from("refreshToken", tokenData.getRefresh_token())
                .httpOnly(true).secure(cookieSecure).path("/").maxAge(7 * 24 * 3600).sameSite(cookieSameSite).build();
        response.addHeader(HttpHeaders.SET_COOKIE, newRefreshCookie.toString());

        ResponseCookie newAccessCookie = ResponseCookie.from("accessToken", tokenData.getAccess_token())
                .httpOnly(true).secure(cookieSecure).path("/").maxAge(15 * 60).sameSite(cookieSameSite).build();
        response.addHeader(HttpHeaders.SET_COOKIE, newAccessCookie.toString());

        return ResponseEntity.ok(tokenData);
    }

    @Operation(
            summary = "Encerrar sessão (Logout Unificado)",
            description = """
                    Encerra a sessão do usuário de ponta a ponta:
                    1. Invalida os cookies `access_token` e `refresh_token` no navegador (`Max-Age=0`).
                    2. Revoga a sessão ativa no Keycloak.
                    3. Encerra imediatamente qualquer conexão SSE (`SseEmitter`) aberta pelo usuário em `NotificacaoSseService`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logout concluído, sessão revogada e cookies limpos.")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "accessToken", required = false) String cookieAccessToken,
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestHeader(value = "Authorization", required = false) String headerToken,
            @RequestParam(value = "refresh_token", required = false) String paramRefreshToken,
            HttpServletResponse response) {

        String accessToken = (cookieAccessToken != null) ? cookieAccessToken : ((headerToken != null) ? headerToken.replace("Bearer ", "") : "");
        String refreshToken = (cookieRefreshToken != null) ? cookieRefreshToken : paramRefreshToken;

        // Limpa (Invalida) os cookies no navegador do usuário configurando maxAge(0)
        ResponseCookie cleanRefreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true).secure(cookieSecure).path("/").maxAge(0).sameSite(cookieSameSite).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cleanRefreshCookie.toString());

        ResponseCookie cleanAccessCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true).secure(cookieSecure).path("/").maxAge(0).sameSite(cookieSameSite).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cleanAccessCookie.toString());

        fachada.logout(accessToken, refreshToken);

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Solicitar redefinição de senha",
            description = "Aciona o fluxo de recuperação de senha do Keycloak enviando um e-mail com link de redefinição (`UPDATE_PASSWORD`) para o endereço informado. Rota pública."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Solicitação processada com sucesso.")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Parameter(description = "E-mail cadastrado do usuário que deseja redefinir a senha", required = true)
            @RequestParam("email") String email) {
        fachada.resetPassword(email);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Consultar perfis (roles) do usuário autenticado",
            description = "Retorna a lista de roles extraídas do token JWT da sessão atual (ex: `ROLE_ESTUDANTE`, `ROLE_PROFISSIONAL`, `ROLE_GESTOR`, `ROLE_ADMINISTRADOR`). Requer usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de roles retornada com sucesso."),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado.")
    })
    @GetMapping("/roles")
    public ResponseEntity<List<String>> getUserRoles(){
        List<String> roles = fachada.getUserRoles();
        return ResponseEntity.ok(roles);
    }
}

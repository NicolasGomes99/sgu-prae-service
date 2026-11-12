package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.auth.AuthenticatedUserProvider;
import br.edu.ufape.sguPraeService.comunicacao.dto.notificacao.NotificacaoBroadcastRequest;
import br.edu.ufape.sguPraeService.comunicacao.mensageria.NotificacaoEvent;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/notificacoes")
@RequiredArgsConstructor
@Tag(name = "14. Notificações (SSE, Histórico Redis e Broadcast)")
public class NotificacaoController {

    private final Fachada fachada;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    @Operation(
            summary = "Subscrever ao stream de notificações em tempo real (SSE)",
            description = """
                    Abre uma conexão persistente **Server-Sent Events (`text/event-stream`)** para o usuário autenticado.
                    - **Handshake Inicial**: Emite imediatamente o evento `INIT` (`"Conexão SSE estabelecida com sucesso."`).
                    - **Heartbeat**: Envia um comentário de ping a cada 45 segundos para manter a conexão ativa em proxies reversos (Nginx / Cloudflare), além dos headers `Cache-Control: no-cache` e `X-Accel-Buffering: no`.
                    - **Sinal de Nova Notificação**: Sempre que o `NotificacaoConsumer` processa um evento do RabbitMQ para o usuário, emite o evento nomeado `SINAL_NOVA_NOTIFICACAO`, instruindo o frontend a atualizar a listagem em `GET /notificacoes`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stream SSE iniciado com sucesso (`text/event-stream`)."),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado.")
    })
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotificacoes(HttpServletResponse response) {
        // Evita bufferização do stream SSE em proxies reversos (ex: Nginx)
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        UUID userId = authenticatedUserProvider.getUserId();
        return fachada.subscreverNotificacoes(userId);
    }

    @Operation(
            summary = "Consultar histórico paginado de notificações não lidas (Redis)",
            description = """
                    Retorna a página de notificações não lidas do usuário autenticado armazenadas no cache **Redis** (ordenadas da mais recente para a mais antiga, com TTL de 7 dias).
                    - Atende tanto em `GET /notificacoes` quanto no alias `GET /notificacoes/historico`.
                    """
    )
    @GetMapping({"", "/historico"})
    public ResponseEntity<Page<NotificacaoEvent>> getHistorico(
            @Parameter(description = "Número da página (iniciando em 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de itens por página") @RequestParam(defaultValue = "10") int size) {

        UUID userId = authenticatedUserProvider.getUserId();
        return ResponseEntity.ok(fachada.buscarNotificacoesNaoLidas(userId, page, size));
    }

    @Operation(
            summary = "Marcar notificação como lida (Remove do cache Redis)",
            description = """
                    Remove a notificação específica (`notificacaoId`) da lista de não lidas do usuário autenticado no Redis.
                    - Aceita tanto `PATCH /notificacoes/{notificacaoId}/lida` quanto `DELETE /notificacoes/{notificacaoId}/lida`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notificação marcada como lida e removida do cache.")
    })
    @RequestMapping(value = "/{notificacaoId}/lida", method = {RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<Void> marcarComoLida(
            @Parameter(description = "UUID da notificação", required = true) @PathVariable UUID notificacaoId) {
        UUID userId = authenticatedUserProvider.getUserId();
        fachada.marcarNotificacaoComoLida(userId, notificacaoId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Limpar todas as notificações não lidas do usuário logado",
            description = "Remove todas as notificações pendentes do usuário autenticado no Redis. Aceita `PATCH` ou `DELETE`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Todas as notificações do usuário foram limpas.")
    })
    @RequestMapping(value = "/limpar-todas", method = {RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<Void> limparTudo() {
        UUID userId = authenticatedUserProvider.getUserId();
        fachada.limparTodasNotificacoes(userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Enviar notificação em massa (Broadcast por perfil)",
            description = """
                    Publica um comunicado institucional assíncrono via RabbitMQ (in-app SSE + Redis + E-mail BCC) para todos os usuários ativos do perfil selecionado (`TODOS`, `ESTUDANTE`, `PROFISSIONAL`, `GESTOR`).
                    - **Perfis permitidos**: `ADMINISTRADOR` e `GESTOR`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Broadcast enfileirado com sucesso para processamento assíncrono."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou perfil de destino desconhecido."),
            @ApiResponse(responseCode = "403", description = "Acesso negado (requer role GESTOR ou ADMINISTRADOR).")
    })
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GESTOR')")
    @PostMapping("/broadcast")
    public ResponseEntity<Void> enviarBroadcast(@Valid @RequestBody NotificacaoBroadcastRequest request) {
        fachada.enviarNotificacaoBroadcast(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}

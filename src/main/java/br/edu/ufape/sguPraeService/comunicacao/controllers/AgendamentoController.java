package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.agendamento.AgendamentoModalidadeRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.agendamento.AgendamentoRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.agendamento.AgendamentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.cancelamentoAgendamento.CancelamentoRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.cancelamentoAgendamento.CancelamentoResponse;
import br.edu.ufape.sguPraeService.exceptions.UnavailableVagaException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.AgendamentoNotFoundException;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.VagaNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Agendamento;
import br.edu.ufape.sguPraeService.models.CancelamentoAgendamento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/agendamento")
@Tag(name = "10. Agendamentos e Cancelamentos")
public class AgendamentoController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Agendar atendimento em uma vaga disponível",
            description = """
                    Reserva uma vaga de atendimento para o estudante autenticado.
                    - **Regras de Negócio e Proteção Anti-Abuso**:
                      1. A vaga deve estar disponível (`disponivel = true`), caso contrário retorna `409 Conflict` (`UnavailableVagaException`).
                      2. O estudante não pode possuir mais de um agendamento ativo para a mesma data.
                      3. Intervalo mínimo (*cooldown*) de **15 minutos** entre a criação de agendamentos consecutivos pelo mesmo estudante.
                    - **Notificação**: Ao confirmar, a vaga passa para `disponivel = false` e tanto o estudante quanto o profissional recebem notificação automática (SSE + Redis + E-mail).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Agendamento realizado com sucesso."),
            @ApiResponse(responseCode = "400", description = "Estudante já possui agendamento na mesma data ou tentativa dentro do cooldown de 15 minutos."),
            @ApiResponse(responseCode = "404", description = "Vaga não encontrada."),
            @ApiResponse(responseCode = "409", description = "Vaga já está reservada por outro estudante.")
    })
    @PreAuthorize("hasRole('ESTUDANTE')")
    @PostMapping
    public ResponseEntity<AgendamentoResponse> agendar(@Valid @RequestBody AgendamentoRequest request)
            throws VagaNotFoundException, UnavailableVagaException {
        AgendamentoResponse response = fachada.agendarVaga(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Buscar agendamento por ID",
            description = """
                    Retorna os detalhes de um agendamento específico.
                    - **Proteção Anti-IDOR**: Apenas o estudante titular do agendamento, o profissional responsável pela vaga ou usuários com role `GESTOR`/`ADMINISTRADOR` têm permissão para visualizar.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agendamento encontrado."),
            @ApiResponse(responseCode = "403", description = "Acesso negado (usuário não é parte deste agendamento)."),
            @ApiResponse(responseCode = "404", description = "Agendamento não encontrado.")
    })
    @PreAuthorize("hasAnyRole('ESTUDANTE', 'PROFISSIONAL', 'GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/{id}")
    public ResponseEntity<AgendamentoResponse> buscarAgendamento(
            @Parameter(description = "ID do agendamento", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(fachada.buscarAgendamento(id));
    }

    @Operation(
            summary = "Listar agendamentos ativos do estudante logado",
            description = "Retorna a lista paginada de agendamentos ativos pertencentes ao estudante autenticado."
    )
    @PreAuthorize("hasRole('ESTUDANTE')")
    @GetMapping("/estudante")
    public Page<AgendamentoResponse> listarAgendamentosPorEstudanteAtual(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarAgendamentoPorEstudanteAtual(pageable);
    }

    @Operation(
            summary = "Listar agendamentos ativos por ID do estudante",
            description = """
                    Lista os agendamentos de um estudante específico.
                    - **Proteção Anti-IDOR**: Caso o requisitante seja `ESTUDANTE`, só pode informar seu próprio `UUID`.
                    """
    )
    @PreAuthorize("hasAnyRole('PROFISSIONAL', 'GESTOR', 'ADMINISTRADOR', 'ESTUDANTE')")
    @GetMapping("/estudante/{estudanteId}")
    public Page<AgendamentoResponse> listarAgendamentosPorEstudante(
            @Parameter(description = "UUID do estudante", required = true) @PathVariable UUID estudanteId,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarAgendamentosPorEstudante(estudanteId, pageable);
    }

    @Operation(
            summary = "Listar agenda de atendimentos do profissional logado",
            description = "Retorna de forma paginada todos os agendamentos ativos vinculados aos cronogramas do profissional autenticado."
    )
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @GetMapping("/profissional")
    public Page<AgendamentoResponse> listarAgendamentosPorProfissionalAtual(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarAgendamentoPorProfissionalAtual(pageable);
    }

    @Operation(
            summary = "Listar agendamentos por ID do profissional",
            description = "Consulta os agendamentos vinculados a um profissional específico. Restrito ao próprio `PROFISSIONAL`, `GESTOR` ou `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('PROFISSIONAL', 'GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/profissional/{profissionalId}")
    public Page<AgendamentoResponse> listarAgendamentosPorProfissional(
            @Parameter(description = "UUID do profissional", required = true) @PathVariable UUID profissionalId,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarAgendamentosPorProfissional(profissionalId, pageable);
    }

    @Operation(
            summary = "Cancelar agendamento existente",
            description = """
                    Cancela um agendamento ativo informando o motivo do cancelamento (`CancelamentoRequest`).
                    - **Efeitos Automáticos**:
                      1. Inativa o agendamento (`ativo = false`).
                      2. Libera imediatamente a vaga (`disponivel = true`) para novos agendamentos.
                      3. Dispara evento de notificação de cancelamento para o estudante e para o profissional (SSE + Redis + E-mail).
                    - **Proteção Anti-IDOR**: Apenas o estudante titular ou o profissional responsável pela vaga podem cancelar o agendamento.
                    """
    )
    @PreAuthorize("hasAnyRole('ESTUDANTE', 'PROFISSIONAL')")
    @PostMapping("{id}/cancelar")
    public ResponseEntity<CancelamentoResponse> cancelarAgendamento(
            @Parameter(description = "ID do agendamento a ser cancelado", required = true) @PathVariable Long id,
            @Valid @RequestBody CancelamentoRequest cancelamentoRequest) {
        CancelamentoAgendamento cancelamento = cancelamentoRequest.convertToEntity(cancelamentoRequest, modelMapper);
        return ResponseEntity.ok(new CancelamentoResponse(fachada.cancelarAgendamento(id, cancelamento), modelMapper));
    }

    @Operation(
            summary = "Buscar detalhes de um cancelamento por ID",
            description = "Retorna os detalhes do termo de cancelamento de agendamento. Valida permissão das partes envolvidas (estudante titular, profissional da vaga ou gestão)."
    )
    @PreAuthorize("hasAnyRole('ESTUDANTE', 'PROFISSIONAL', 'GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/cancelamento/{id}")
    public ResponseEntity<CancelamentoResponse> buscarCancelamentoAgendamento(
            @Parameter(description = "ID do cancelamento", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(new CancelamentoResponse(fachada.buscarCancelamento(id), modelMapper));
    }

    @Operation(
            summary = "Listar histórico de cancelamentos do estudante logado",
            description = "Retorna a lista paginada de agendamentos cancelados pertencentes ao estudante autenticado."
    )
    @PreAuthorize("hasRole('ESTUDANTE')")
    @GetMapping("/cancelamento/estudante")
    public Page<CancelamentoResponse> listarCancelamentosPorEstudanteAtual(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarCancelamentosPorEstudanteAtual(pageable).map(cancelamento -> new CancelamentoResponse(cancelamento, modelMapper));
    }

    @Operation(
            summary = "Listar histórico de cancelamentos do profissional logado",
            description = "Retorna a lista paginada de agendamentos cancelados vinculados à agenda do profissional autenticado."
    )
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @GetMapping("/cancelamento/profissional")
    public Page<CancelamentoResponse> listarCancelamentosPorProfissionalAtual(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarCancelamentosPorProfissionalAtual(pageable).map(cancelamento -> new CancelamentoResponse(cancelamento, modelMapper));
    }

    @Operation(
            summary = "Alterar modalidade do agendamento (Presencial / Remoto)",
            description = "Permite que o estudante titular altere a modalidade (`PRESENCIAL` ou `REMOTO`) de um agendamento ativo e dispara notificação de atualização."
    )
    @PreAuthorize("hasRole('ESTUDANTE')")
    @PatchMapping("/{id}/modalidade")
    public ResponseEntity<AgendamentoResponse> alterarModalidade(
            @Parameter(description = "ID do agendamento", required = true) @PathVariable Long id,
            @Valid @RequestBody AgendamentoModalidadeRequest request) throws AgendamentoNotFoundException {

        Agendamento atualizado = fachada.alterarModalidadeAgendamento(id, request.getModalidade());

        return ResponseEntity.ok(new AgendamentoResponse(atualizado, modelMapper));
    }
}

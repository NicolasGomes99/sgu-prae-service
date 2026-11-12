package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.tipoatendimento.TipoAtendimentoUpdateRequest;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.TipoAtendimento;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoatendimento.TipoAtendimentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.tipoatendimento.TipoAtendimentoRequest;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.TipoAtendimentoNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelmapper.ModelMapper;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import com.querydsl.core.types.Predicate;
import org.springframework.data.querydsl.binding.QuerydslPredicate;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tipo-atendimento")
@Tag(name = "13. Tipos de Atendimento")
public class TipoAtendimentoController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Listar tipos de atendimento (Paginado)",
            description = "Lista as modalidades de atendimento de saúde/assistência (ex: Psicologia, Serviço Social, Pedagogia), seu tempo padrão de sessão e grade de horários."
    )
    @GetMapping
    public Page<TipoAtendimentoResponse> listar(
            @Parameter(hidden = true) @QuerydslPredicate(root = TipoAtendimento.class) Predicate predicate,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarTipoAtendimentos(predicate, pageable)
                .map(tipoAtendimento -> new TipoAtendimentoResponse(tipoAtendimento, modelMapper));
    }

    @Operation(
            summary = "Buscar tipo de atendimento por ID",
            description = "Retorna os detalhes de um tipo de atendimento específico."
    )
    @GetMapping("/{id}")
    public ResponseEntity<TipoAtendimentoResponse> buscar(
            @Parameter(description = "ID do tipo de atendimento", required = true) @PathVariable Long id) throws TipoAtendimentoNotFoundException {
        TipoAtendimento response = fachada.buscarTipoAtendimento(id);
        return new ResponseEntity<>(new TipoAtendimentoResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Criar novo tipo de atendimento",
            description = "Cadastra uma modalidade de atendimento definindo `nome` (apenas letras e espaços), `tempoAtendimento` e lista de `horarios` iniciais. Restrito a `PROFISSIONAL`."
    )
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @PostMapping
    public ResponseEntity<TipoAtendimentoResponse> salvar(@Valid @RequestBody TipoAtendimentoRequest entity) {
        TipoAtendimento response = fachada.salvarTipoAtendimento(entity.convertToEntity(entity, modelMapper));
        return new ResponseEntity<>(new TipoAtendimentoResponse(response, modelMapper), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Editar tipo de atendimento por ID",
            description = "Atualiza o nome, duração de consulta ou grade de horários de um tipo de atendimento. Restrito a `PROFISSIONAL`."
    )
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @PatchMapping("/{id}")
    public ResponseEntity<TipoAtendimentoResponse> editar(
            @Parameter(description = "ID do tipo de atendimento", required = true) @PathVariable Long id,
            @Valid @RequestBody TipoAtendimentoUpdateRequest dto) throws TipoAtendimentoNotFoundException {
        TipoAtendimento response = fachada.editarTipoAtendimento(id, dto);
        return new ResponseEntity<>(new TipoAtendimentoResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Remover horário específico da grade de um tipo de atendimento",
            description = "Remove o horário localizado na posição `index` da lista de horários do tipo de atendimento. Restrito a `PROFISSIONAL`."
    )
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @DeleteMapping("/{id}/horarios/{index}")
    public ResponseEntity<TipoAtendimentoResponse> deleteHorario(
            @Parameter(description = "ID do tipo de atendimento", required = true) @PathVariable Long id,
            @Parameter(description = "Índice (base 0) do horário na lista", required = true) @PathVariable int index) throws TipoAtendimentoNotFoundException {
        TipoAtendimento response = fachada.deletarHorarioTipoAtendimento(id, index);
        return new ResponseEntity<>(new TipoAtendimentoResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Deletar tipo de atendimento por ID",
            description = "Remove um tipo de atendimento caso não existam cronogramas vinculados a ele (retorna `409 Conflict` caso esteja em uso). Restrito a `PROFISSIONAL`."
    )
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do tipo de atendimento", required = true) @PathVariable Long id) throws TipoAtendimentoNotFoundException {
        fachada.deletarTipoAtendimento(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

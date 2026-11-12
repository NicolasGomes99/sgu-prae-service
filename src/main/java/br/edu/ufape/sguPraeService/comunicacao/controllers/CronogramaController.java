package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.cronograma.CronogramaUpdateRequest;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.TipoAtendimentoNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Cronograma;
import br.edu.ufape.sguPraeService.comunicacao.dto.cronograma.CronogramaResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.cronograma.CronogramaRequest;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.CronogramaNotFoundException;

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
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cronograma")
@Tag(name = "11. Cronogramas de Atendimento")
public class CronogramaController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Listar cronogramas ativos (Paginado)",
            description = """
                    Lista os cronogramas de atendimento ativos com suas respectivas vagas disponíveis.
                    - Suporta filtros QueryDSL por `data` (data exata ou intervalo), `profissional.id` e `tipoAtendimento.id`.
                    """
    )
    @GetMapping
    public Page<CronogramaResponse> listar(
            @Parameter(hidden = true) @QuerydslPredicate(root = Cronograma.class) Predicate predicate,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarCronogramas(predicate, pageable).map(cronograma -> new CronogramaResponse(cronograma, modelMapper));
    }

    @Operation(
            summary = "Buscar cronograma por ID",
            description = "Retorna os detalhes de um cronograma de atendimento, incluindo profissional, tipo de atendimento e lista de vagas."
    )
    @GetMapping("/{id}")
    public ResponseEntity<CronogramaResponse> buscar(
            @Parameter(description = "ID do cronograma", required = true) @PathVariable Long id) throws CronogramaNotFoundException {
        Cronograma response = fachada.buscarCronograma(id);
        return new ResponseEntity<>(new CronogramaResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Listar cronogramas por Tipo de Atendimento",
            description = "Filtra os cronogramas ativos vinculados a uma especialidade/tipo de atendimento específico."
    )
    @GetMapping("/tipo-atendimento/{id}")
    public ResponseEntity<Page<CronogramaResponse>> buscarPorTipoAtendimento(
            @Parameter(description = "ID do tipo de atendimento", required = true) @PathVariable Long id,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) throws TipoAtendimentoNotFoundException {
        Page<CronogramaResponse> response = fachada.listarCronogramasPorTipoAtendimento(id, pageable).map(cronograma -> new CronogramaResponse(cronograma, modelMapper));
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Criar cronogramas em lote com geração automática de vagas",
            description = """
                    Permite que o `PROFISSIONAL` autenticado cadastre um ou mais dias de atendimento (`datas`).
                    - Para cada data informada, o sistema gera automaticamente todas as `Vaga`s com base nos horários e no `tempoAtendimento` configurados no `TipoAtendimento`.
                    """
    )
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @PostMapping
    public ResponseEntity<List<CronogramaResponse>> salvar(@Valid @RequestBody CronogramaRequest entity) throws TipoAtendimentoNotFoundException {
        List<Cronograma> entities = entity.convertToEntities(modelMapper);

        List<CronogramaResponse> responses = entities.stream()
                .map(cronogramaEntity -> {
                    Cronograma criado = fachada.salvarCronograma(
                            cronogramaEntity,
                            entity.getTipoAtendimentoId()
                    );
                    return new CronogramaResponse(criado, modelMapper);
                })
                .collect(Collectors.toList());

        return new ResponseEntity<>(responses, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Listar cronogramas do profissional logado",
            description = "Retorna de forma paginada todos os cronogramas ativos pertencentes ao profissional autenticado."
    )
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @GetMapping("/profissional")
    public Page<CronogramaResponse> listarPorProfissional(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarCronogramasPorProfissional(pageable).map(cronograma -> new CronogramaResponse(cronograma, modelMapper));
    }

    @Operation(
            summary = "Editar cronograma existente",
            description = """
                    Atualiza a data ou tipo de atendimento de um cronograma e recria as vagas disponíveis.
                    - **Proteção Anti-IDOR**: Um `PROFISSIONAL` só pode editar seus próprios cronogramas; `GESTOR` e `ADMINISTRADOR` podem editar qualquer cronograma.
                    """
    )
    @PreAuthorize("hasAnyRole('PROFISSIONAL', 'GESTOR', 'ADMINISTRADOR')")
    @PatchMapping("/{id}")
    public ResponseEntity<CronogramaResponse> editar(
            @Parameter(description = "ID do cronograma", required = true) @PathVariable Long id,
            @Valid @RequestBody CronogramaUpdateRequest entity) throws CronogramaNotFoundException {
        Cronograma response = fachada.editarCronograma(id, entity.convertToEntity(entity, modelMapper), entity.getTipoAtendimentoId());
        return new ResponseEntity<>(new CronogramaResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Desativar cronograma por ID",
            description = "Desativa logicamente um cronograma de atendimento. Valida propriedade para `PROFISSIONAL`."
    )
    @PreAuthorize("hasAnyRole('PROFISSIONAL', 'GESTOR', 'ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do cronograma", required = true) @PathVariable Long id) {
        fachada.deletarCronograma(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

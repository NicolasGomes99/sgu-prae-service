package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.models.Vaga;
import br.edu.ufape.sguPraeService.servicos.VagaService;
import br.edu.ufape.sguPraeService.comunicacao.dto.vaga.VagaResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.vaga.VagaRequest;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.VagaNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.http.HttpStatus;

@RestController
@RequiredArgsConstructor
@RequestMapping("/vaga")
@Tag(name = "12. Vagas de Atendimento")
public class VagaController {
    private final VagaService service;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Listar todas as vagas de atendimento",
            description = "Retorna a lista de horários (vagas) cadastrados nos cronogramas ativos, indicando sua disponibilidade (`disponivel`)."
    )
    @GetMapping
    public List<VagaResponse> listar() {
        return service.listar().stream().map(vaga -> new VagaResponse(vaga, modelMapper)).toList();
    }

    @Operation(
            summary = "Buscar vaga por ID",
            description = "Consulta os detalhes de uma vaga específica (horário de início, fim e disponibilidade)."
    )
    @GetMapping("/{id}")
    public ResponseEntity<VagaResponse> buscar(
            @Parameter(description = "ID da vaga", required = true) @PathVariable Long id) throws VagaNotFoundException {
        Vaga response = service.buscar(id);
        return new ResponseEntity<>(new VagaResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Editar horário ou disponibilidade de uma vaga",
            description = """
                    Atualiza uma vaga específica.
                    - **Proteção Anti-IDOR**: Um `PROFISSIONAL` só pode alterar vagas pertencentes aos seus próprios cronogramas.
                    """
    )
    @PreAuthorize("hasAnyRole('PROFISSIONAL', 'GESTOR', 'ADMINISTRADOR')")
    @PatchMapping("/{id}")
    public ResponseEntity<VagaResponse> editar(
            @Parameter(description = "ID da vaga", required = true) @PathVariable Long id,
            @Valid @RequestBody VagaRequest entity) throws VagaNotFoundException {
        Vaga response = service.editar(id, entity.convertToEntity(entity, modelMapper));
        return new ResponseEntity<>(new VagaResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Remover vaga por ID",
            description = "Exclui uma vaga de atendimento. Valida propriedade do cronograma quando acionado por `PROFISSIONAL`."
    )
    @PreAuthorize("hasAnyRole('PROFISSIONAL', 'GESTOR', 'ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID da vaga", required = true) @PathVariable Long id) {
        service.deletar(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}

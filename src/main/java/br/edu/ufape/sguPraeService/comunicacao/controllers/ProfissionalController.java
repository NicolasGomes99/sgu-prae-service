package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.profissional.ProfissionalUpdateRequest;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.comunicacao.dto.profissional.ProfissionalResponse;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.ProfissionalNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;

@RestController
@RequiredArgsConstructor
@RequestMapping("/profissional")
@Tag(name = "04. Profissionais de Saúde/Assistência")
public class ProfissionalController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Listar todos os profissionais ativos",
            description = "Retorna a lista de profissionais ativos da PRAE (psicólogos, assistentes sociais, etc.) para exibição em agendas e filtros. Requer usuário autenticado."
    )
    @GetMapping
    public List<ProfissionalResponse> listar() {
        return fachada.listarProfissionais();
    }

    @Operation(
            summary = "Buscar profissional por ID (UUID)",
            description = "Consulta os dados públicos/profissionais (nome, especialidade, SIAPE) de um profissional ativo pelo seu UUID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profissional encontrado."),
            @ApiResponse(responseCode = "404", description = "Profissional não encontrado.")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProfissionalResponse> buscar(
            @Parameter(description = "UUID do profissional", required = true) @PathVariable UUID id) throws ProfissionalNotFoundException {
        return ResponseEntity.ok(fachada.buscarProfissional(id));
    }

    @Operation(
            summary = "Obter perfil do profissional autenticado (/current)",
            description = "Retorna os dados completos do profissional logado na sessão atual. Restrito à role `PROFISSIONAL`."
    )
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @GetMapping("/current")
    public ResponseEntity<ProfissionalResponse> buscarProfissionalAtual() throws ProfissionalNotFoundException {
        ProfissionalResponse profissional = fachada.buscarProfissionalAtual();
        return ResponseEntity.ok(profissional);
    }

    @Operation(
            summary = "Atualizar dados profissionais do usuário logado",
            description = "Atualiza a especialidade ou matrícula SIAPE do próprio profissional autenticado. Restrito à role `PROFISSIONAL`."
    )
    @PreAuthorize("hasRole('PROFISSIONAL')")
    @PatchMapping
    public ResponseEntity<ProfissionalResponse> editar(@Valid @RequestBody ProfissionalUpdateRequest dto) throws ProfissionalNotFoundException {
        ProfissionalResponse response = fachada.editarProfissional(dto);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Desativar registro de profissional",
            description = """
                    Realiza a exclusão lógica de um profissional.
                    - **Proteção Anti-IDOR**: Um `PROFISSIONAL` só pode desativar o próprio registro; `GESTOR` e `ADMINISTRADOR` podem desativar qualquer profissional.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'PROFISSIONAL')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "UUID do profissional", required = true) @PathVariable UUID id) throws ProfissionalNotFoundException {
        fachada.deletarProfissional(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

}

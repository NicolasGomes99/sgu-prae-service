package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.dadosBancarios.DadosBancariosRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.dadosBancarios.DadosBancariosResponse;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.DadosBancarios;
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
@RequestMapping("/dadosBancarios")
@RequiredArgsConstructor
@Tag(name = "08. Dados Bancários")
public class DadosBancariosController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Buscar dados bancários por ID",
            description = """
                    Consulta uma conta bancária cadastrada pelo seu ID.
                    - **Proteção Anti-IDOR**: Estudantes só podem visualizar dados bancários vinculados ao seu próprio cadastro; `GESTOR` e `ADMINISTRADOR` podem consultar qualquer registro.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados bancários encontrados."),
            @ApiResponse(responseCode = "403", description = "Acesso negado (conta pertence a outro estudante)."),
            @ApiResponse(responseCode = "404", description = "Registro não encontrado.")
    })
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'ESTUDANTE')")
    @GetMapping("/{id}")
    public ResponseEntity<DadosBancariosResponse> buscarDadosBancarios(
            @Parameter(description = "ID dos dados bancários", required = true) @PathVariable Long id) {
        DadosBancarios dadosBancarios = fachada.buscarDadosBancarios(id);
        if (dadosBancarios == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(new DadosBancariosResponse(dadosBancarios, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Listar todos os dados bancários (Paginado)",
            description = "Lista todas as contas bancárias cadastradas para conferência financeira. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping
    public Page<DadosBancariosResponse> listarDadosBancarios(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarDadosBancarios(pageable)
                .map(dadosBancarios -> new DadosBancariosResponse(dadosBancarios, modelMapper));
    }

    @Operation(
            summary = "Cadastrar ou vincular dados bancários a um estudante",
            description = "Cria e associa os dados bancários (titular, banco, agência, conta, tipo de conta) ao estudante informado. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PostMapping("/{idEstudante}")
    public ResponseEntity<DadosBancariosResponse> criarDadosBancarios(
            @Parameter(description = "UUID do estudante titular", required = true) @PathVariable UUID idEstudante,
            @Valid @RequestBody DadosBancariosRequest dadosBancariosRequest) {
        DadosBancarios dadosBancarios = dadosBancariosRequest.convertToEntity(dadosBancariosRequest, modelMapper);
        DadosBancarios novoDadosBancarios = fachada.salvarDadosBancarios(idEstudante, dadosBancarios);
        return new ResponseEntity<>(new DadosBancariosResponse(novoDadosBancarios, modelMapper), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Atualizar dados bancários por ID",
            description = "Atualiza as informações de agência, conta ou instituição bancária de um registro existente. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PatchMapping("/{id}")
    public ResponseEntity<DadosBancariosResponse> atualizarDadosBancarios(
            @Parameter(description = "ID dos dados bancários", required = true) @PathVariable Long id,
            @Valid @RequestBody DadosBancariosRequest dadosBancariosRequest) {
        DadosBancarios dadosBancarios = dadosBancariosRequest.convertToEntity(dadosBancariosRequest, modelMapper);
        DadosBancarios dadosBancariosAtualizados = fachada.atualizarDadosBancarios(id, dadosBancarios);
        if (dadosBancariosAtualizados == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(new DadosBancariosResponse(dadosBancariosAtualizados, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Deletar dados bancários por ID",
            description = "Remove o registro de dados bancários informado. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarDadosBancarios(
            @Parameter(description = "ID dos dados bancários", required = true) @PathVariable Long id) {
        fachada.deletarDadosBancarios(id);
        return ResponseEntity.noContent().build();
    }
}

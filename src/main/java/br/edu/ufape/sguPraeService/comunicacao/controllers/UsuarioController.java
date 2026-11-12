package br.edu.ufape.sguPraeService.comunicacao.controllers;

import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.EstudanteRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.profissional.ProfissionalRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.gestor.GestorRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.usuario.UsuarioPatchRequest;
import br.edu.ufape.sguPraeService.comunicacao.dto.usuario.UsuarioResponse;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.UsuarioNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Usuario;
import com.querydsl.core.types.Predicate;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.binding.QuerydslPredicate;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/usuario")
@Tag(name = "02. Usuários e Perfis")
public class UsuarioController {

    private final Fachada fachada;
    private final ModelMapper modelMapper;

    // ================= ROTAS DE CRIAÇÃO (ESPECÍFICAS) ================= //

    @Operation(
            summary = "Auto-cadastro de Estudante (Multipart)",
            description = """
                    Realiza o cadastro unificado de um novo **Estudante** no Keycloak (atribuindo a role `ESTUDANTE`) e no banco de dados local.
                    - **Público (`permitAll`)**: Permite que alunos criem sua conta no portal da PRAE.
                    - **Multipart Form-Data**: Envie o JSON `EstudanteRequest` na part `dados` (`application/json`) e os documentos comprobatórios opcionais na part `arquivos` (`PDF`, `PNG` ou `JPG`, máx. 10 arquivos, validados por assinatura binária *magic bytes*).
                    - **Transação Compensatória**: Caso ocorra erro de validação ou integridade no banco local (ex: CPF ou matrícula duplicada), o usuário recém-criado no Keycloak e os arquivos físicos são revertidos automaticamente (rollback).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Estudante cadastrado com sucesso."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou arquivo com formato/assinatura binária não permitida."),
            @ApiResponse(responseCode = "409", description = "Conflito de unicidade (CPF, e-mail ou matrícula já cadastrados).")
    })
    @PostMapping(value = "/estudante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UsuarioResponse> cadastrarEstudante(
            @Parameter(description = "JSON contendo os dados pessoais, acadêmicos e de endereço do estudante", required = true)
            @RequestPart("dados") @Valid EstudanteRequest request,
            @Parameter(description = "Lista opcional de documentos comprobatórios (PDF, PNG, JPG)")
            @RequestPart(value = "arquivos", required = false) List<MultipartFile> arquivos) throws Exception {
        Usuario response = fachada.cadastrarEstudante(request, arquivos);
        return new ResponseEntity<>(new UsuarioResponse(response, modelMapper), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Cadastrar novo Profissional",
            description = """
                    Cria um usuário com perfil **Profissional** (psicólogo, assistente social, pedagogo, etc.) no Keycloak e no banco local com rollback compensatório automático.
                    - **Perfis permitidos**: `GESTOR` ou `ADMINISTRADOR`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Profissional cadastrado com sucesso."),
            @ApiResponse(responseCode = "400", description = "Dados de requisição inválidos."),
            @ApiResponse(responseCode = "403", description = "Acesso negado (requer role GESTOR ou ADMINISTRADOR).")
    })
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @PostMapping("/profissional")
    public ResponseEntity<UsuarioResponse> cadastrarProfissional(@Valid @RequestBody ProfissionalRequest request) throws Exception {
        Usuario response = fachada.cadastrarProfissional(request);
        return new ResponseEntity<>(new UsuarioResponse(response, modelMapper), HttpStatus.CREATED);
    }

    @Operation(
            summary = "Cadastrar novo Gestor",
            description = """
                    Cria um usuário com perfil **Gestor** da PRAE no Keycloak e no banco local.
                    - **Perfis permitidos**: Exclusivo para `ADMINISTRADOR`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Gestor cadastrado com sucesso."),
            @ApiResponse(responseCode = "400", description = "Dados de requisição inválidos."),
            @ApiResponse(responseCode = "403", description = "Acesso negado (requer role ADMINISTRADOR).")
    })
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/gestor")
    public ResponseEntity<UsuarioResponse> cadastrarGestor(@Valid @RequestBody GestorRequest request) throws Exception {
        Usuario response = fachada.cadastrarGestor(request);
        return new ResponseEntity<>(new UsuarioResponse(response, modelMapper), HttpStatus.CREATED);
    }

    // ================= ROTAS GERAIS DE USUÁRIO ================= //

    @Operation(
            summary = "Buscar usuário por ID (UUID)",
            description = """
                    Retorna os dados básicos de um usuário pelo seu `UUID`.
                    - **Proteção Anti-IDOR**: O usuário autenticado só pode consultar o próprio ID, exceto se possuir a role `ADMINISTRADOR`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário encontrado."),
            @ApiResponse(responseCode = "403", description = "Tentativa de consultar dados de outro usuário sem permissão administrativa."),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> buscar(
            @Parameter(description = "UUID do usuário", required = true)
            @PathVariable UUID id) throws UsuarioNotFoundException {
        Usuario response = fachada.buscarUsuario(id);
        return new ResponseEntity<>(new UsuarioResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Listar todos os usuários ativos (Paginado)",
            description = """
                    Lista todos os usuários ativos com suporte a paginação e filtros dinâmicos via QueryDSL (`nome`, `email`, `cpf`, `telefone`).
                    - **Perfis permitidos**: Exclusivo para `ADMINISTRADOR`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de usuários retornada com sucesso."),
            @ApiResponse(responseCode = "403", description = "Acesso negado.")
    })
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @GetMapping
    public Page<UsuarioResponse> listarUsuarios(
            @Parameter(hidden = true) @QuerydslPredicate(root = Usuario.class) Predicate predicate,
            @ParameterObject @PageableDefault(value = 10)
            @SortDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return fachada.listarUsuarios(predicate, pageable).map(usuario -> new UsuarioResponse(usuario, modelMapper));
    }

    @Operation(
            summary = "Obter dados do usuário autenticado atual (/current)",
            description = "Retorna os dados cadastrais do usuário logado com base no `sub` (UUID) extraído do token JWT."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados do usuário atual retornados com sucesso."),
            @ApiResponse(responseCode = "401", description = "Não autenticado."),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado na base local.")
    })
    @GetMapping("/current")
    public ResponseEntity<UsuarioResponse> buscarUsuarioAtual() throws UsuarioNotFoundException {
        Usuario response = fachada.buscarUsuarioAtual();
        return new ResponseEntity<>(new UsuarioResponse(response, modelMapper), HttpStatus.OK);
    }

    @Operation(
            summary = "Atualizar dados básicos do usuário autenticado",
            description = "Atualiza parcialmente os dados cadastrais básicos (nome, nome social, telefone, data de nascimento, etnia) do próprio usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados atualizados com sucesso."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos.")
    })
    @PatchMapping
    public ResponseEntity<UsuarioResponse> atualizar(@Valid @RequestBody UsuarioPatchRequest usuario) throws UsuarioNotFoundException {
        Usuario atualizado = fachada.editarUsuario(usuario);
        return ResponseEntity.ok(new UsuarioResponse(atualizado, modelMapper));
    }

    @Operation(
            summary = "Desativar a própria conta de usuário",
            description = "Realiza a exclusão lógica (`ativo = false`) da conta do próprio usuário autenticado no banco local e remove o registro no Keycloak."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Conta desativada com sucesso.")
    })
    @DeleteMapping
    public ResponseEntity<Void> deletar() throws UsuarioNotFoundException {
        fachada.deletarUsuario();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(
            summary = "Desativar conta de um usuário por ID (Admin)",
            description = """
                    Realiza a exclusão lógica de qualquer usuário pelo seu `UUID` e remove sua credencial no Keycloak.
                    - **Perfis permitidos**: Exclusivo para `ADMINISTRADOR`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuário desativado com sucesso."),
            @ApiResponse(responseCode = "403", description = "Acesso negado."),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")
    })
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuario(
            @Parameter(description = "UUID do usuário a ser desativado", required = true)
            @PathVariable UUID id) throws UsuarioNotFoundException {
        fachada.deletarUsuario(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

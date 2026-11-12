package br.edu.ufape.sguPraeService.comunicacao.controllers;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import br.edu.ufape.sguPraeService.comunicacao.dto.documento.DocumentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.*;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.EstudanteNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Estudante;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.querydsl.core.types.Predicate;
import org.springframework.data.querydsl.binding.QuerydslPredicate;

@RestController
@RequestMapping("/estudantes")
@RequiredArgsConstructor
@Tag(name = "03. Estudantes e Pasta Documental")
public class EstudanteController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @Operation(
            summary = "Listar estudantes ativos com filtros dinâmicos",
            description = """
                    Retorna uma página de estudantes ativos. Permite filtragem combinada por `nome`, `cpf`, `curso.id`, `matricula`, `deficiente`, etc.
                    - **Perfis permitidos**: `GESTOR`, `ADMINISTRADOR` e `PROFISSIONAL`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de estudantes retornada com sucesso."),
            @ApiResponse(responseCode = "403", description = "Acesso negado (estudantes não podem listar outros estudantes).")
    })
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'PROFISSIONAL')")
    @GetMapping
    public Page<EstudanteResponse> listarEstudantes(
            @Parameter(hidden = true) @QuerydslPredicate(root = Estudante.class) Predicate predicate,
            @Parameter(description = "Filtro opcional por nome (contém, case-insensitive)") @RequestParam(required = false) String nome,
            @Parameter(description = "Filtro opcional por CPF") @RequestParam(required = false) String cpf,
            @Parameter(description = "Filtro opcional pelo ID do curso") @RequestParam(required = false, name = "curso.id") Long cursoId,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {

        return fachada.listarEstudantesComFiltrosExternos(predicate, nome, cpf, cursoId, pageable);
    }

    @Operation(
            summary = "Listar estudantes por Curso",
            description = """
                    Lista de forma paginada todos os estudantes ativos matriculados em um curso específico.
                    - **Perfis permitidos**: `GESTOR`, `ADMINISTRADOR` e `PROFISSIONAL`.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'PROFISSIONAL')")
    @GetMapping("/curso/{id}")
    public Page<EstudanteResponse> listarEstudantesPorCurso(
            @Parameter(description = "ID do curso", required = true) @PathVariable Long id,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable
    ) {
        return fachada.listarEstudantesPorCurso(id, pageable);
    }

    @Operation(
            summary = "Buscar estudante por ID (UUID)",
            description = """
                    Retorna os detalhes completos de um estudante.
                    - **Proteção Anti-IDOR**: Um `ESTUDANTE` só pode consultar o próprio `UUID`. `GESTOR`, `ADMINISTRADOR` e `PROFISSIONAL` podem consultar qualquer estudante ativo.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estudante encontrado."),
            @ApiResponse(responseCode = "403", description = "Acesso negado (tentativa de acessar dados de outro estudante)."),
            @ApiResponse(responseCode = "404", description = "Estudante não encontrado.")
    })
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'PROFISSIONAL', 'ESTUDANTE')")
    @GetMapping("/{id}")
    public ResponseEntity<EstudanteResponse> buscarEstudante(
            @Parameter(description = "UUID do estudante", required = true) @PathVariable UUID id) throws EstudanteNotFoundException {
        return ResponseEntity.ok(fachada.buscarEstudante(id));
    }

    @Operation(
            summary = "Atualizar dados cadastrais e endereço do estudante logado",
            description = """
                    Permite que o estudante autenticado atualize seus dados acadêmicos, socioeconômicos (`rendaPercapta`, `contatoFamilia`, `deficiente`, `tipoDeficiencia`) e seu `endereco`.
                    - **Perfis permitidos**: `ESTUDANTE` (opera exclusivamente sobre o usuário da sessão JWT).
                    """
    )
    @PreAuthorize("hasRole('ESTUDANTE')")
    @PatchMapping
    public ResponseEntity<EstudanteResponse> atualizarEstudante(@Valid @RequestBody EstudanteUpdateRequest estudanteUpdateRequest) throws EstudanteNotFoundException {
        EstudanteResponse estudanteAtualizado = fachada.atualizarEstudante(estudanteUpdateRequest);
        return ResponseEntity.ok(estudanteAtualizado);
    }

    @Operation(
            summary = "Desativar registro de estudante",
            description = """
                    Realiza a desativação lógica de um estudante.
                    - **Proteção Anti-IDOR**: Estudantes só podem desativar seu próprio registro; `GESTOR` e `ADMINISTRADOR` podem desativar qualquer estudante.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'ESTUDANTE')")
    @DeleteMapping("/{id}/")
    public ResponseEntity<Void> deletarEstudante(
            @Parameter(description = "UUID do estudante", required = true) @PathVariable UUID id) throws EstudanteNotFoundException {
        fachada.deletarEstudante(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Listar estudantes credores com benefícios ativos",
            description = """
                    Retorna a relação paginada de estudantes que possuem auxílios/bolsas ativos (credores aptos a recebimento financeiro), incluindo dados bancários e curso.
                    - **Perfis permitidos**: `GESTOR` e `ADMINISTRADOR`.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/credores")
    public ResponseEntity<Page<CredorResponse>> listarCredoresComBeneficiosAtivos(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        Page<CredorResponse> credores = fachada.listarCredoresComBeneficiosAtivos(pageable);
        return ResponseEntity.ok(credores);
    }

    @Operation(
            summary = "Listar credores vinculados a um benefício específico",
            description = "Retorna os estudantes credores vinculados ao ID de benefício informado. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/credores/beneficio/{id}")
    public ResponseEntity<Page<CredorResponse>> listarCredoresPorBeneficio(
            @Parameter(description = "ID do benefício", required = true) @PathVariable Long id,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        Page<CredorResponse> credores = fachada.listarCredoresPorBeneficio(id, pageable);
        return ResponseEntity.ok(credores);
    }

    @Operation(
            summary = "Listar credores para publicação de edital/portaria (Dados Minimizado LGPD)",
            description = "Retorna apenas `id` e `nome` (`PublicacaoResponse`) dos estudantes com benefícios ativos, adequado para publicação nominal sem expor CPF ou dados bancários. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/credores/publicacao")
    public ResponseEntity<Page<PublicacaoResponse>> listarCredoresParaPublicacao(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        Page<EstudanteResponse> pageAlunos = fachada.listarCredoresParaPublicacao(pageable);
        Page<PublicacaoResponse> pagePublicacoes = pageAlunos.map(estudanteResponse ->
                new PublicacaoResponse(estudanteResponse.getId(), estudanteResponse.getNome())
        );
        return ResponseEntity.ok(pagePublicacoes);
    }

    @Operation(
            summary = "Listar credores ativos por Curso",
            description = "Retorna a lista paginada de estudantes credores com benefícios ativos filtrada pelo ID do curso. Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/credores/curso/{id}")
    public Page<CredorResponse> listarCredoresPorCurso(
            @Parameter(description = "ID do curso", required = true) @PathVariable Long id,
            @ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarCredoresPorCurso(id, pageable);
    }

    @Operation(
            summary = "Gerar relatório individual de estudante assistido",
            description = "Consolida o dossiê completo de assistência estudantil de um aluno (dados socioeconômicos, curso, benefícios concedidos e valores). Restrito a `GESTOR` e `ADMINISTRADOR`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR')")
    @GetMapping("/{id}/relatorio")
    public ResponseEntity<RelatorioEstudanteAssistidoResponse> gerarRelatorioAssistido(
            @Parameter(description = "UUID do estudante", required = true) @PathVariable UUID id
    ) throws EstudanteNotFoundException {
        RelatorioEstudanteAssistidoResponse relatorio = fachada.gerarRelatorioEstudanteAssistido(id);
        return ResponseEntity.ok(relatorio);
    }

    @Operation(
            summary = "Obter perfil completo do estudante autenticado (/current)",
            description = "Retorna o `EstudanteResponse` completo (incluindo curso, endereço e dados bancários) do estudante logado."
    )
    @PreAuthorize("hasAnyRole('ESTUDANTE', 'ALUNO')")
    @GetMapping("/current")
    public ResponseEntity<EstudanteResponse> buscarEstudanteAtual() throws EstudanteNotFoundException {
        EstudanteResponse estudante = fachada.buscarEstudanteAtual();
        return ResponseEntity.ok(estudante);
    }

    @Operation(
            summary = "Buscar estudante por UserID",
            description = "Consulta os dados do estudante a partir do seu UUID de usuário. Restrito a `GESTOR`, `ADMINISTRADOR` e `PROFISSIONAL`."
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'PROFISSIONAL')")
    @GetMapping("/userid/{userId}")
    public ResponseEntity<EstudanteResponse> buscarEstudantePorUserId(
            @Parameter(description = "UUID do usuário estudante", required = true) @PathVariable UUID userId) throws EstudanteNotFoundException {
        EstudanteResponse estudante = fachada.buscarEstudantePorUserId(userId);
        return ResponseEntity.ok(estudante);
    }

    @Operation(
            summary = "Enviar documentos para a pasta do estudante logado (Multipart)",
            description = """
                    Adiciona novos documentos comprobatórios à pasta do estudante autenticado.
                    - **Validação de Segurança**: Aceita até 10 arquivos por requisição nos formatos `PDF`, `PNG`, `JPG`/`JPEG`, validando a assinatura binária real (*magic bytes*) contra spoofing de extensão.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Documentos validados e armazenados com sucesso."),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido, extensão não permitida ou assinatura binária divergente.")
    })
    @PreAuthorize("hasRole('ESTUDANTE')")
    @PostMapping(value = "/documentos", consumes = "multipart/form-data")
    public ResponseEntity<Void> uploadDocumentos(
            @Valid @ModelAttribute EstudanteDocumentoRequest request) throws EstudanteNotFoundException {

        fachada.adicionarDocumentosEstudante(request.getArquivos());

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Listar e baixar documentos de um estudante em Base64",
            description = """
                    Retorna todos os documentos da pasta do estudante convertidos em `Base64` (`DocumentoResponse`: `nome` + `base64`), sem expor caminhos físicos do servidor.
                    - **Proteção Anti-IDOR**: Estudantes só podem consultar sua própria pasta documental; `GESTOR`, `ADMINISTRADOR` e `PROFISSIONAL` podem consultar documentos de qualquer estudante.
                    """
    )
    @PreAuthorize("hasAnyRole('GESTOR', 'ADMINISTRADOR', 'PROFISSIONAL', 'ESTUDANTE')")
    @GetMapping("/{id}/documentos")
    public ResponseEntity<List<DocumentoResponse>> buscarDocumentosDoEstudante(
            @Parameter(description = "UUID do estudante", required = true) @PathVariable UUID id)
            throws EstudanteNotFoundException, IOException {

        List<DocumentoResponse> documentos = fachada.buscarDocumentosPorEstudante(id);
        return ResponseEntity.ok(documentos);
    }
}

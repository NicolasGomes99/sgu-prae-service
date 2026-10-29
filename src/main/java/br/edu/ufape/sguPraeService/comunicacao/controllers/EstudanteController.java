package br.edu.ufape.sguPraeService.comunicacao.controllers;


import java.io.IOException;
import java.util.List;
import java.util.UUID;

import br.edu.ufape.sguPraeService.comunicacao.dto.documento.DocumentoResponse;
import br.edu.ufape.sguPraeService.comunicacao.dto.estudante.*;
import br.edu.ufape.sguPraeService.comunicacao.dto.usuario.AlunoResponse;
import br.edu.ufape.sguPraeService.exceptions.notFoundExceptions.EstudanteNotFoundException;
import br.edu.ufape.sguPraeService.fachada.Fachada;
import br.edu.ufape.sguPraeService.models.Estudante;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.querydsl.core.types.Predicate;
import org.springframework.data.querydsl.binding.QuerydslPredicate;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/estudantes")
@RequiredArgsConstructor
public class EstudanteController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @GetMapping
    public Page<EstudanteResponse> listarEstudantes(
            @QuerydslPredicate(root = Estudante.class) Predicate predicate,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false, name = "curso.id") Long cursoId,
            @PageableDefault(sort = "id") Pageable pageable) {

        return fachada.listarEstudantesComFiltrosExternos(predicate, nome, cpf, cursoId, pageable);
    }

    @GetMapping("/curso/{id}")
    public Page<EstudanteResponse> listarEstudantesPorCurso(
            @PathVariable Long id,
            @PageableDefault(sort = "id") Pageable pageable
    ) {
        return fachada.listarEstudantesPorCurso(id, pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstudanteResponse> buscarEstudante(@PathVariable UUID id) throws EstudanteNotFoundException {
        return ResponseEntity.ok(fachada.buscarEstudante(id));
    }

//    @PreAuthorize("hasRole('ALUNO')")
//    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) // Importante!
//    public ResponseEntity<EstudanteResponse> criarEstudante(
//            @RequestPart("dados") @Valid EstudanteRequest estudanteRequest, // JSON vem aqui
//            @RequestPart(value = "arquivos", required = false) List<MultipartFile> arquivos // Arquivos vêm aqui
//    ) {
//        Estudante estudante = estudanteRequest.convertToEntity(estudanteRequest, modelMapper);
//        EstudanteResponse novoEstudante = fachada.salvarEstudante(estudante, arquivos);
//        return ResponseEntity.status(HttpStatus.CREATED).body(novoEstudante);
//    }

//    @PreAuthorize("hasRole('ALUNO')")
//    @PostMapping
//    public ResponseEntity<EstudanteResponse> criarEstudante(@Valid @RequestBody EstudanteRequest estudanteRequest)  {
//        Estudante estudante = estudanteRequest.convertToEntity(estudanteRequest, modelMapper);
//        EstudanteResponse novoEstudante = fachada.salvarEstudante(estudante);
//        return ResponseEntity.status(HttpStatus.CREATED).body(novoEstudante);
//    }

    @PreAuthorize("hasRole('ESTUDANTE')")
    @PatchMapping
    public ResponseEntity<EstudanteResponse> atualizarEstudante(@Valid @RequestBody EstudanteUpdateRequest estudanteUpdateRequest) throws EstudanteNotFoundException {
        EstudanteResponse estudanteAtualizado = fachada.atualizarEstudante(estudanteUpdateRequest);
        return ResponseEntity.ok(estudanteAtualizado);
    }

    @DeleteMapping("/{id}/")
    public ResponseEntity<Void> deletarEstudante(@PathVariable UUID id) throws EstudanteNotFoundException{
        fachada.deletarEstudante(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/credores")
    public ResponseEntity<Page<CredorResponse>> listarCredoresComBeneficiosAtivos(@PageableDefault(sort = "id") Pageable pageable) {
        Page<CredorResponse> credores = fachada.listarCredoresComBeneficiosAtivos(pageable);
        return ResponseEntity.ok(credores);
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/credores/beneficio/{id}")
    public ResponseEntity<Page<CredorResponse>> listarCredoresPorBeneficio(@PathVariable Long id, @PageableDefault(sort = "id") Pageable pageable) {
        Page<CredorResponse> credores = fachada.listarCredoresPorBeneficio(id, pageable);
        return ResponseEntity.ok(credores);
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/credores/publicacao")
    public ResponseEntity<Page<PublicacaoResponse>> listarCredoresParaPublicacao( @PageableDefault(sort = "id") Pageable pageable) {
        Page<EstudanteResponse> pageAlunos = fachada.listarCredoresParaPublicacao(pageable);
        Page<PublicacaoResponse> pagePublicacoes = pageAlunos.map(estudanteResponse ->
                new PublicacaoResponse(estudanteResponse.getId(), modelMapper.toString())
        );
        return ResponseEntity.ok(pagePublicacoes);
    }
    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/credores/curso/{id}")
    Page<CredorResponse> listarCredoresPorCurso(@PathVariable Long id, @PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarCredoresPorCurso(id, pageable);
    }

    @PreAuthorize("hasRole('GESTOR')")
    @GetMapping("/{id}/relatorio")
    public ResponseEntity<RelatorioEstudanteAssistidoResponse> gerarRelatorioAssistido(
            @PathVariable UUID id
    ) throws EstudanteNotFoundException {
        RelatorioEstudanteAssistidoResponse relatorio = fachada.gerarRelatorioEstudanteAssistido(id);
        return ResponseEntity.ok(relatorio);
    }

    @PreAuthorize("hasAnyRole('ESTUDANTE', 'ALUNO')")
    @GetMapping("/current")
    public ResponseEntity<EstudanteResponse> buscarEstudanteAtual() throws EstudanteNotFoundException {
        EstudanteResponse estudante = fachada.buscarEstudanteAtual();
        return ResponseEntity.ok(estudante);
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'PROFISSIONAL')")
    @GetMapping("/userid/{userId}")
    public ResponseEntity<EstudanteResponse> buscarEstudantePorUserId(@PathVariable UUID userId) throws EstudanteNotFoundException {
        EstudanteResponse estudante = fachada.buscarEstudantePorUserId(userId);
        return ResponseEntity.ok(estudante);
    }

    @PostMapping(value = "/documentos", consumes = "multipart/form-data")
    public ResponseEntity<Void> uploadDocumentos(
            @Valid @ModelAttribute EstudanteDocumentoRequest request) throws EstudanteNotFoundException {

        fachada.adicionarDocumentosEstudante(request.getArquivos());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/documentos")
    public ResponseEntity<List<DocumentoResponse>> buscarDocumentosDoEstudante(@PathVariable UUID id)
            throws EstudanteNotFoundException, IOException {

        List<DocumentoResponse> documentos = fachada.buscarDocumentosPorEstudante(id);
        return ResponseEntity.ok(documentos);
    }
}
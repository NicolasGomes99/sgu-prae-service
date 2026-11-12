package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.comunicacao.dto.documento.DocumentoResponse;
import br.edu.ufape.sguPraeService.dados.DocumentoRepository;
import br.edu.ufape.sguPraeService.models.Documento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArmazenamentoAndCleanupTest {

    @TempDir
    Path tempDir;

    private ArmazenamentoService armazenamentoService;
    private DocumentoRepository documentoRepository;

    @BeforeEach
    void setUp() {
        documentoRepository = mock(DocumentoRepository.class);
        armazenamentoService = new ArmazenamentoService();
        ReflectionTestUtils.setField(armazenamentoService, "uploadDir", tempDir.toString());
    }

    @Test
    @DisplayName("Deve salvar arquivos PDF, PNG e JPEG legítimos validando magic bytes e converter para Base64")
    void deveSalvarArquivosValidosEConverterParaBase64() throws Exception {
        byte[] pdfBytes = "%PDF-1.7 conteudo seguro".getBytes(StandardCharsets.UTF_8);
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        byte[] jpgBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

        MockMultipartFile pdfFile = new MockMultipartFile("arquivos", "comprovante.pdf", "application/pdf", pdfBytes);
        MockMultipartFile pngFile = new MockMultipartFile("arquivos", "identidade.png", "image/png", pngBytes);
        MockMultipartFile jpgFile = new MockMultipartFile("arquivos", "foto.jpg", "image/jpeg", jpgBytes);

        List<Documento> salvos = armazenamentoService.salvarArquivo(new MultipartFile[]{pdfFile, pngFile, jpgFile});

        assertEquals(3, salvos.size());
        for (Documento doc : salvos) {
            assertTrue(Files.exists(Path.of(doc.getPath())));
        }

        // Verifica que @JsonIgnore impede vazamento do caminho físico na serialização JSON
        String json = new ObjectMapper().writeValueAsString(salvos.getFirst());
        assertFalse(json.contains("path"), "O campo físico 'path' não deve ser serializado no JSON");

        // Converte para Base64
        List<DocumentoResponse> base64Responses = armazenamentoService.converterDocumentosParaBase64(salvos);
        assertEquals(3, base64Responses.size());
        assertNotNull(base64Responses.getFirst().getBase64());
    }

    @Test
    @DisplayName("Deve bloquear arquivo malicioso disfarçado de PDF (spoofing de extensão/MIME) e fazer rollback")
    void deveBloquearArquivoComAssinaturaBinariaFalsaEFazerRollback() {
        byte[] pdfValido = "%PDF-1.7 documento real".getBytes(StandardCharsets.UTF_8);
        byte[] exeDisfarcadoDePdf = "MZ\u0090\u0000ExecutavelMalicioso".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile f1 = new MockMultipartFile("arquivos", "valido.pdf", "application/pdf", pdfValido);
        MockMultipartFile f2 = new MockMultipartFile("arquivos", "malware.pdf", "application/pdf", exeDisfarcadoDePdf);

        assertThrows(IllegalArgumentException.class,
                () -> armazenamentoService.salvarArquivo(new MultipartFile[]{f1, f2}));

        // Garante que o primeiro arquivo foi removido no rollback automático
        try (var stream = Files.list(tempDir)) {
            assertEquals(0, stream.count(), "Nenhum arquivo órfão deve permanecer no diretório após falha no lote");
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    @DisplayName("Deve bloquear tentativa de Path Traversal na leitura de documentos para Base64")
    void deveBloquearPathTraversalNaLeituraBase64() throws Exception {
        Path outsideFile = tempDir.getParent().resolve("segredo-fora-do-storage.txt");
        Files.writeString(outsideFile, "segredo");

        try {
            Documento docTraversalRelativo = new Documento();
            docTraversalRelativo.setNome("../" + outsideFile.getFileName().toString());
            docTraversalRelativo.setPath(outsideFile.toString());

            // O método sanitiza o nome com getFileName() e jamais lê o arquivo externo
            List<DocumentoResponse> resultado = armazenamentoService.converterDocumentosParaBase64(List.of(docTraversalRelativo));
            assertTrue(resultado.isEmpty(), "Arquivo fora do diretório base jamais deve ser lido");

            // E caso o nome seja exatamente '..', o normalize() sai do diretório base e dispara SecurityException
            Documento docParentDir = new Documento();
            docParentDir.setNome("..");
            assertThrows(SecurityException.class,
                    () -> armazenamentoService.converterDocumentosParaBase64(List.of(docParentDir)));
        } finally {
            Files.deleteIfExists(outsideFile);
        }
    }

    @Test
    @DisplayName("FileCleanupScheduler: Deve remover apenas arquivos órfãos e preservar arquivos essenciais")
    void deveRemoverApenasArquivosOrfaosNoCleanupScheduler() throws Exception {
        Path essencial = tempDir.resolve("essencial.pdf");
        Path orfao = tempDir.resolve("orfao.pdf");
        Files.writeString(essencial, "%PDF-essencial");
        Files.writeString(orfao, "%PDF-orfao");

        when(documentoRepository.isArquivoEssencialParaOSistema("essencial.pdf")).thenReturn(true);
        when(documentoRepository.isArquivoEssencialParaOSistema("orfao.pdf")).thenReturn(false);

        FileCleanupScheduler scheduler = new FileCleanupScheduler(documentoRepository);
        ReflectionTestUtils.setField(scheduler, "uploadDir", tempDir.toString());

        scheduler.varreduraSupremaDeArquivosFisicos();

        assertTrue(Files.exists(essencial), "Arquivo vinculado no banco deve ser mantido");
        assertFalse(Files.exists(orfao), "Arquivo órfão sem vínculo ativo deve ser removido");
    }
}

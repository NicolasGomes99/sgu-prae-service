package br.edu.ufape.sguPraeService.servicos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.edu.ufape.sguPraeService.comunicacao.dto.documento.DocumentoResponse;
import br.edu.ufape.sguPraeService.models.Documento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArmazenamentoService implements br.edu.ufape.sguPraeService.servicos.interfaces.ArmazenamentoService {
    private static final int MAX_FILES_PER_REQUEST = 10;
    private final Set<String> tiposPermitidos = Set.of("application/pdf", "image/jpeg", "image/png", "image/jpg");
    private final Set<String> extensoesPermitidas = Set.of("pdf", "jpeg", "jpg", "png");

    @Value("${arquivo.diretorio-upload}")
    private String uploadDir;

    @Transactional
    @Override
    public List<Documento> salvarArquivo(MultipartFile[] arquivos) {
        if (arquivos == null || arquivos.length == 0) {
            return List.of();
        }
        if (arquivos.length > MAX_FILES_PER_REQUEST) {
            throw new IllegalArgumentException("Quantidade máxima de arquivos por envio excedida (máximo: " + MAX_FILES_PER_REQUEST + ").");
        }

        List<Documento> documentosSalvos = new ArrayList<>();
        Path diretorioBase = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            if (!Files.exists(diretorioBase)) {
                Files.createDirectories(diretorioBase);
            }

            for (MultipartFile arquivo : arquivos) {
                if (arquivo == null || arquivo.isEmpty()) {
                    throw new IllegalArgumentException("O arquivo enviado não pode ser vazio.");
                }

                String contentType = arquivo.getContentType();
                if (contentType == null || !tiposPermitidos.contains(contentType.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Tipo de arquivo não permitido!");
                }

                String originalFilename = arquivo.getOriginalFilename();
                String extensao = originalFilename != null
                        ? FilenameUtils.getExtension(originalFilename).toLowerCase(Locale.ROOT)
                        : "";
                if (!extensoesPermitidas.contains(extensao)) {
                    throw new IllegalArgumentException("Extensão de arquivo não permitida!");
                }

                validarAssinaturaBinaria(arquivo, extensao);

                String uuid = UUID.randomUUID().toString();
                String nomeArquivoComUUID = uuid + "." + extensao;

                Path caminho = diretorioBase.resolve(nomeArquivoComUUID).normalize();
                if (!caminho.startsWith(diretorioBase)) {
                    throw new IllegalArgumentException("Caminho de arquivo inválido!");
                }

                try (InputStream is = arquivo.getInputStream()) {
                    Files.copy(is, caminho, StandardCopyOption.REPLACE_EXISTING);
                }

                Documento documento = new Documento();
                documento.setNome(nomeArquivoComUUID);
                documento.setPath(caminho.toString());
                documentosSalvos.add(documento);
            }
            return documentosSalvos;
        } catch (RuntimeException e) {
            deletarArquivosFisicos(documentosSalvos);
            throw e;
        } catch (IOException e) {
            deletarArquivosFisicos(documentosSalvos);
            throw new RuntimeException("Falha ao salvar arquivo!");
        }
    }

    private void validarAssinaturaBinaria(MultipartFile arquivo, String extensao) throws IOException {
        byte[] header = new byte[8];
        int lidos;
        try (InputStream is = arquivo.getInputStream()) {
            lidos = is.read(header);
        }
        if (lidos < 4) {
            throw new IllegalArgumentException("Arquivo inválido ou corrompido.");
        }

        boolean assinaturaValida = switch (extensao) {
            case "pdf" -> header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46; // %PDF
            case "png" -> lidos >= 8
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                    && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A;
            case "jpg", "jpeg" -> (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            default -> false;
        };

        if (!assinaturaValida) {
            throw new IllegalArgumentException("O conteúdo do arquivo não corresponde ao formato permitido (" + extensao + ").");
        }
    }

    @Override
    public List<DocumentoResponse> converterDocumentosParaBase64(List<Documento> documentos) throws IOException {
        List<DocumentoResponse> documentosBase64 = new ArrayList<>();
        Path diretorioBase = Paths.get(uploadDir).toAbsolutePath().normalize();

        for (Documento documento : documentos) {
            if (documento == null || documento.getNome() == null) {
                continue;
            }
            Path filePath = diretorioBase.resolve(Paths.get(documento.getNome()).getFileName()).normalize();
            if (!filePath.startsWith(diretorioBase)) {
                throw new SecurityException("Tentativa de acesso a caminho fora do diretório permitido.");
            }
            if (Files.exists(filePath)) {
                byte[] fileBytes = Files.readAllBytes(filePath);
                String base64 = Base64.getEncoder().encodeToString(fileBytes);
                documentosBase64.add(new DocumentoResponse(documento.getNome(), base64));
            }
        }
        return documentosBase64;
    }

    @Override
    public void deletarArquivosFisicos(List<Documento> documentos) {
        if (documentos == null || documentos.isEmpty()) {
            return;
        }
        Path diretorioBase = Paths.get(uploadDir).toAbsolutePath().normalize();
        for (Documento doc : documentos) {
            if (doc != null && doc.getNome() != null) {
                try {
                    Path filePath = diretorioBase.resolve(Paths.get(doc.getNome()).getFileName()).normalize();
                    if (filePath.startsWith(diretorioBase)) {
                        Files.deleteIfExists(filePath);
                    }
                } catch (IOException ex) {
                    log.warn("Não foi possível excluir arquivo temporário/órfão {}: {}", doc.getNome(), ex.getMessage());
                }
            }
        }
    }
}

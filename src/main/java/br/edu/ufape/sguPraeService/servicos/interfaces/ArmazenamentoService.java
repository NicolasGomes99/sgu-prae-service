package br.edu.ufape.sguPraeService.servicos.interfaces;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.edu.ufape.sguPraeService.comunicacao.dto.documento.DocumentoResponse;
import br.edu.ufape.sguPraeService.models.Documento;

import java.io.IOException;
import java.util.List;

public interface ArmazenamentoService {
    @Transactional
    List<Documento> salvarArquivo(MultipartFile[] arquivos);

    List<DocumentoResponse> converterDocumentosParaBase64(List<Documento> documentos) throws IOException;

    void deletarArquivosFisicos(List<Documento> documentos);
}

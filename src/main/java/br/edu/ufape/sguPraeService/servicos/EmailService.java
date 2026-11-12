package br.edu.ufape.sguPraeService.servicos;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:nao-responda@ufape.edu.br}")
    private String remetente;

    @Async
    public void enviarEmailEmMassa(List<String> destinatariosBcc, String assunto, String mensagemHtml) {
        if (destinatariosBcc == null || destinatariosBcc.isEmpty()) {
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // "true" habilita modo multipart (HTML) e "UTF-8" corrige a acentuação
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String fromAddress = (remetente != null && !remetente.isBlank() && remetente.contains("@"))
                    ? remetente
                    : "nao-responda@ufape.edu.br";

            helper.setFrom(fromAddress);
            helper.setSubject(assunto);
            helper.setText(mensagemHtml, true); // true indica que o texto é HTML

            // Coloca a lista inteira em Cópia Oculta (BCC) para garantir privacidade (LGPD)
            helper.setBcc(destinatariosBcc.toArray(new String[0]));

            mailSender.send(message);
            log.info("E-mail disparado assincronamente para {} destinatários.", destinatariosBcc.size());

        } catch (Exception e) {
            log.error("Falha crítica ao disparar e-mail em massa.", e);
        }
    }
}

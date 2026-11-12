package br.edu.ufape.sguPraeService.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Trava de segurança executada automaticamente no boot quando o profile 'prod' está ativo.
 * Impede que a aplicação suba em produção caso alguém tenha copiado segredos de desenvolvimento
 * (como o client-secret público do realm-export.json ou senhas padrão do docker-compose)
 * ou desabilitado a flag Secure dos cookies.
 */
@Slf4j
@Component
@Profile("prod")
public class EnvironmentSecurityValidator implements ApplicationRunner {

    private static final java.util.Set<String> FORBIDDEN_DEV_KEYCLOAK_SECRETS = java.util.Set.of(
            "HntTWny9OT0d3sCBc9qwc6asCNuHXY5e",
            "sgu-prae-local-dev-secret",
            "${KEYCLOAK_CLIENT_SECRET}"
    );

    @Value("${keycloak.client-secret}")
    private String keycloakClientSecret;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${spring.rabbitmq.password}")
    private String rabbitPassword;

    @Value("${common.cookie-secure:true}")
    private boolean cookieSecure;

    @Override
    public void run(ApplicationArguments args) {
        if (keycloakClientSecret == null || keycloakClientSecret.isBlank()
                || FORBIDDEN_DEV_KEYCLOAK_SECRETS.contains(keycloakClientSecret.trim())) {
            throw new IllegalStateException(
                    "[FALHA DE SEGURANÇA CRÍTICA - PROD] O 'KEYCLOAK_CLIENT_SECRET' configurado em produção é inválido ou utiliza um secret conhecido de desenvolvimento! Gere um Client Secret exclusivo no Keycloak de produção."
            );
        }

        if ("password".equalsIgnoreCase(dbPassword) || "postgres".equalsIgnoreCase(dbPassword)) {
            throw new IllegalStateException(
                    "[FALHA DE SEGURANÇA CRÍTICA - PROD] A senha do banco de dados ('DB_PASS') está usando um valor padrão de desenvolvimento em produção!"
            );
        }

        if ("guest".equalsIgnoreCase(rabbitPassword)) {
            throw new IllegalStateException(
                    "[FALHA DE SEGURANÇA CRÍTICA - PROD] A senha do RabbitMQ ('RABBITMQ_PASS') está usando a credencial padrão 'guest' em produção!"
            );
        }

        if (!cookieSecure) {
            throw new IllegalStateException(
                    "[FALHA DE SEGURANÇA CRÍTICA - PROD] A propriedade 'COOKIE_SECURE' não pode ser 'false' no profile de produção ('prod')!"
            );
        }

        log.info("Validação de segurança do ambiente de produção ('prod') concluída com sucesso.");
    }
}

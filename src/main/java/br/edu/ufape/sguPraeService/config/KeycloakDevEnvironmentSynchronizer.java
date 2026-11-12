package br.edu.ufape.sguPraeService.config;

import br.edu.ufape.sguPraeService.servicos.interfaces.KeycloakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sincronizador automático de ambiente local/testes ('dev' e 'docker').
 * Garante que jamais haja inconsistência entre o 'keycloak.client-secret' configurado na aplicação
 * e o secret armazenado no banco do Keycloak local — mesmo que:
 * 1. O desenvolvedor já possua um volume Docker antigo ('keycloak-pg-data') criado antes da remoção do secret hardcoded;
 * 2. O 'realm-export.json' tenha sido importado manualmente pela interface web sem expandir '${KEYCLOAK_CLIENT_SECRET}';
 * 3. O desenvolvedor altere 'KEYCLOAK_CLIENT_SECRET' sem recriar os volumes do banco.
 *
 * Nunca é carregado em produção (@Profile({"dev", "docker"})).
 */
@Slf4j
@Component
@Profile({"dev", "docker"})
@RequiredArgsConstructor
public class KeycloakDevEnvironmentSynchronizer implements ApplicationRunner {

    private final KeycloakService keycloakService;

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String expectedClientSecret;

    @Value("${keycloak.admin-username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin-password:admin}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        try (Keycloak adminKeycloak = createAdminClient()) {
            List<ClientRepresentation> clients = adminKeycloak.realm(realm).clients().findByClientId(clientId);
            if (clients == null || clients.isEmpty()) {
                log.debug("Client '{}' não encontrado no realm '{}' durante verificação de sincronia local.", clientId, realm);
                return;
            }

            ClientRepresentation clientRep = clients.getFirst();
            ClientResource clientResource = adminKeycloak.realm(realm).clients().get(clientRep.getId());
            CredentialRepresentation secretCredential = clientResource.getSecret();
            String currentSecretInKeycloak = secretCredential != null ? secretCredential.getValue() : clientRep.getSecret();

            if (!expectedClientSecret.equals(currentSecretInKeycloak)) {
                clientRep.setSecret(expectedClientSecret);
                clientResource.update(clientRep);
                keycloakService.init();
                log.info("Auto-Heal [dev/docker]: Client secret do client '{}' foi sincronizado automaticamente no Keycloak local.", clientId);
            } else {
                log.debug("Client secret do client '{}' já está sincronizado no Keycloak local.", clientId);
            }
        } catch (Exception e) {
            log.debug("Sincronização automática do Keycloak local ignorada (Keycloak offline ou em ambiente de teste isolado): {}", e.getMessage());
        }
    }

    protected Keycloak createAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl)
                .realm("master")
                .clientId("admin-cli")
                .username(adminUsername)
                .password(adminPassword)
                .grantType("password")
                .build();
    }
}

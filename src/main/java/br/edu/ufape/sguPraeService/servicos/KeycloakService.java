package br.edu.ufape.sguPraeService.servicos;

import br.edu.ufape.sguPraeService.comunicacao.dto.auth.TokenResponse;
import br.edu.ufape.sguPraeService.exceptions.auth.KeycloakAuthenticationException;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakService implements br.edu.ufape.sguPraeService.servicos.interfaces.KeycloakService {

    private Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${common.emailEnabled:false}")
    private boolean emailEnabled;

    @Override
    @PostConstruct
    public void init() {
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl)
                .realm(realm) // Lendo da variável do application.yml (sgu-ufape)
                .clientId(clientId) // sgu-application
                .clientSecret(clientSecret) // Lendo do yml
                .grantType("client_credentials") // Autenticação de máquina (Service Account)
                .build();
    }

    @Override
    public TokenResponse login(String email, String password) throws KeycloakAuthenticationException {
        String tokenUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("username", email);
        formData.add("password", password);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, request, TokenResponse.class);
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                TokenResponse tokenResponse = response.getBody();
                String userId = getUserId(email);
                List<RoleRepresentation> roles = keycloak.realm(realm).users().get(userId).roles().realmLevel().listEffective();
                tokenResponse.setRoles(roles.stream().map(RoleRepresentation::getName).toList());
                return tokenResponse;
            }
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST && !verifyEmailValid(email)) {
                throw new KeycloakAuthenticationException("E-mail não verificado. Verifique sua caixa de entrada.");
            }
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new KeycloakAuthenticationException("Credenciais inválidas. Verifique o email e a senha.");
            }
            throw new KeycloakAuthenticationException("Erro ao autenticar no Keycloak: " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            throw new KeycloakAuthenticationException("Não foi possível acessar o servidor Keycloak. Verifique sua conexão.", e);
        } catch (Exception e) {
            throw new KeycloakAuthenticationException("Erro inesperado durante o login.", e);
        }
        throw new KeycloakAuthenticationException("Erro ao autenticar: resposta inesperada do servidor.");
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        String keycloakTokenUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(keycloakTokenUrl, request, TokenResponse.class);
        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        }
        throw new KeycloakAuthenticationException("Falha ao atualizar token de autenticação.");
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String logoutUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/logout";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (accessToken != null && !accessToken.isBlank()) {
            headers.add("Authorization", "Bearer " + accessToken);
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(logoutUrl, HttpMethod.POST, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Keycloak retornou status não-2xx durante logout: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.warn("Não foi possível revogar sessão no Keycloak durante logout (sessão possivelmente já expirada): {}", e.getMessage());
        }
    }

    @Override
    public UUID createUser(String email, String password, String role) throws KeycloakAuthenticationException {
        String userId = null;
        try {
            UserRepresentation user = getUserRepresentation(email, password);
            try (Response response = keycloak.realm(realm).users().create(user)) {
                if (response.getStatus() != 201) {
                    if (response.getStatus() == 409) {
                        throw new KeycloakAuthenticationException("Credenciais já existentes. Tente outro email.");
                    }
                    throw new KeycloakAuthenticationException("Erro ao criar o usuário no Keycloak. Status: " + response.getStatus());
                }
                userId = CreatedResponseUtil.getCreatedId(response);
            }

            if (userId == null || userId.isBlank()) {
                userId = getUserId(email);
            }

            RoleRepresentation userRole = keycloak.realm(realm).roles().get(role.toLowerCase()).toRepresentation();
            keycloak.realm(realm).users().get(userId).roles().realmLevel().add(Collections.singletonList(userRole));

            if (emailEnabled) {
                try {
                    keycloak.realm(realm).users().get(userId).executeActionsEmail(Collections.singletonList("VERIFY_EMAIL"));
                } catch (Exception e) {
                    log.error("Erro ao enviar e-mail de verificação para {}: {}", email, e.getMessage());
                }
            }

            return UUID.fromString(userId);

        } catch (NotFoundException e) {
            if (userId != null) deleteUser(userId);
            throw new KeycloakAuthenticationException("Role " + role + " não encontrada no Keycloak.", e);
        } catch (KeycloakAuthenticationException e) {
            if (userId != null) deleteUser(userId);
            throw e;
        } catch (Exception e) {
            if (userId != null) deleteUser(userId);
            throw new KeycloakAuthenticationException("Falha ao registrar usuário no Keycloak: " + e.getMessage(), e);
        }
    }

    @Override
    public void addRoleToUser(String userId, String role) {
        try {
            RoleRepresentation userRole = keycloak.realm(realm).roles().get(role.toLowerCase()).toRepresentation();
            keycloak.realm(realm).users().get(userId).roles().realmLevel().add(Collections.singletonList(userRole));
        } catch (Exception e) {
            log.error("Erro ao adicionar role {} ao usuário {}", role, userId, e);
            throw new KeycloakAuthenticationException("Erro ao adicionar role ao usuário.", e);
        }
    }

    @Override
    public void deleteUser(String userId) {
        keycloak.realm(realm).users().get(userId).remove();
    }

    @Override
    public String getUserId(String username) {
        List<UserRepresentation> users = keycloak.realm(realm).users().search(username, true);
        if (!users.isEmpty()) {
            return users.getFirst().getId();
        }
        throw new KeycloakAuthenticationException("Usuário não encontrado no Keycloak: " + username);
    }

    @Override
    public void resetPassword(String email) throws KeycloakAuthenticationException {
        try {
            List<UserRepresentation> users = keycloak.realm(realm).users().searchByEmail(email, true);
            if (users == null || users.isEmpty()) {
                // Não revela se o e-mail existe ou não (prevenção de User Enumeration)
                log.debug("Solicitação de redefinição de senha para e-mail não encontrado: {}", email);
                return;
            }
            String userId = users.getFirst().getId();
            keycloak.realm(realm).users().get(userId).executeActionsEmail(Collections.singletonList("UPDATE_PASSWORD"));
        } catch (Exception e) {
            log.error("Erro ao processar solicitação de redefinição de senha: {}", e.getMessage());
        }
    }

    @Override
    public List<UserRepresentation> listUnverifiedUsers() {
        return keycloak.realm(realm).users().list().stream()
                .filter(user -> !user.isEmailVerified())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getUserRoles(String userId) {
        try {
            return keycloak.realm(realm).users().get(userId).roles().realmLevel().listEffective()
                    .stream()
                    .map(RoleRepresentation::getName)
                    .toList();
        } catch (Exception e) {
            throw new KeycloakAuthenticationException("Erro ao obter roles do usuário.", e);
        }
    }

    @Override
    public List<UUID> obterUsuariosPorRole(String roleName) {
        return keycloak.realm(realm).roles().get(roleName.toLowerCase()).getUserMembers()
                .stream()
                .map(user -> UUID.fromString(user.getId()))
                .collect(Collectors.toList());
    }

    private boolean verifyEmailValid(String email) {
        List<UserRepresentation> users = keycloak.realm(realm).users().searchByEmail(email, true);
        Optional<UserRepresentation> userOptional = users.stream()
                .filter(user -> email.equalsIgnoreCase(user.getEmail()))
                .findFirst();
        return userOptional.map(UserRepresentation::isEmailVerified).orElse(true);
    }

    private UserRepresentation getUserRepresentation(String email, String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setTemporary(false);
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(email);
        user.setFirstName(email);
        user.setLastName(email);
        user.setEmail(email);
        user.setEnabled(true);
        user.setEmailVerified(!emailEnabled);
        user.setCredentials(Collections.singletonList(credential));
        return user;
    }
}

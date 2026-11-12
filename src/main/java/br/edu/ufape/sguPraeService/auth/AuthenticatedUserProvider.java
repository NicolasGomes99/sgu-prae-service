package br.edu.ufape.sguPraeService.auth;

import br.edu.ufape.sguPraeService.exceptions.GlobalAccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AuthenticatedUserProvider {

    public UUID getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            // Tenta pegar pelas vias nativas
            String subject = jwtToken.getToken().getSubject();
            if (subject == null) {
                subject = jwtToken.getToken().getClaimAsString("sub");
            }

            // Fallbacks para claims customizadas (comuns em ambientes refatorados)
            if (subject == null) subject = jwtToken.getToken().getClaimAsString("user_id");
            if (subject == null) subject = jwtToken.getToken().getClaimAsString("id");
            if (subject == null) subject = jwtToken.getToken().getClaimAsString("uid");

            // Trava final
            if (subject == null) {
                throw new IllegalStateException("ID do usuário não encontrado no token JWT.");
            }

            try {
                return UUID.fromString(subject);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("O identificador do usuário no JWT não é um UUID válido.");
            }
        }

        throw new IllegalStateException("Usuário não autenticado via JWT.");
    }

    public boolean hasAnyRole(String... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Set<String> authorityNames = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        for (String role : roles) {
            String normalized = role.toUpperCase().startsWith("ROLE_")
                    ? role.toUpperCase()
                    : "ROLE_" + role.toUpperCase();
            if (authorityNames.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    public void validateOwnershipOrRoles(UUID resourceOwnerId, String... allowedRoles) {
        if (hasAnyRole(allowedRoles)) {
            return;
        }
        UUID currentUserId = getUserId();
        if (resourceOwnerId == null || !resourceOwnerId.equals(currentUserId)) {
            throw new GlobalAccessDeniedException("Você não tem permissão para acessar este recurso");
        }
    }
}

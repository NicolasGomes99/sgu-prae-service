package br.edu.ufape.sguPraeService.config;

import br.edu.ufape.sguPraeService.config.filters.TokenResponseRewriteFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@EnableWebSecurity
@Configuration
public class WebConfig {

    private final TokenResponseRewriteFilter tokenResponseRewriteFilter;

    // Resgata as URLs do frontend permitidas a partir do application.yml
    @Value("${common.front}")
    private String allowedOrigins;

    public WebConfig(TokenResponseRewriteFilter tokenResponseRewriteFilter) {
        this.tokenResponseRewriteFilter = tokenResponseRewriteFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // <-- CORS absorvido do Gateway
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(tokenResponseRewriteFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(authz -> authz
                        // Rotas de Documentação (Swagger)
                        .requestMatchers("/api-doc/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Rotas de Autenticação Base (AuthController)
                        .requestMatchers("/login", "/refresh", "/logout", "/reset-password").permitAll()

                        // Rotas de Domínio Genérico / Legado
                        .requestMatchers("/public/**").permitAll()

                        // ==========================================
                        // ROTAS DE CADASTRO E PREENCHIMENTO PÚBLICO
                        // ==========================================
                        // CORREÇÃO CRÍTICA: Rota exata do UsuarioController
                        .requestMatchers(HttpMethod.POST, "/usuario/estudante").permitAll()

                        // Necessário para o front listar opções nos selects do cadastro
                        .requestMatchers(HttpMethod.GET, "/curso").permitAll()

                        // ==========================================
                        // ROTAS DO PAINEL DE TRANSPARÊNCIA (Públicas)
                        // ==========================================
                        // Libera os relatórios e totalizadores do BeneficioController
                        .requestMatchers(HttpMethod.GET, "/beneficio/relatorio/financeiro").permitAll()
                        .requestMatchers(HttpMethod.GET, "/beneficio/quantidade/beneficiados").permitAll()
                        .requestMatchers(HttpMethod.GET, "/beneficio/quantidade/cursos").permitAll()
                        .requestMatchers(HttpMethod.GET, "/beneficio/quantidade/beneficiados/por/curso").permitAll()

                        // Todas as outras rotas exigem token JWT e Validação de Roles (@PreAuthorize)
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(auth -> auth.jwt(token ->
                        token.jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Configuração de CORS extraída da antiga GatewaySecurityConfig.
     * Fundamental para que o Frontend consiga ler/enviar Cookies e realizar chamadas.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Permite as origens configuradas no application.yml (normalizando espaços e barras finais)
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                .filter(origin -> !origin.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);

        // Permite os métodos HTTP comuns
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Permite o tráfego de cabeçalhos de autenticação e controle
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));

        // CRÍTICO: allowCredentials(true) é obrigatório se quisermos usar Cookies (HttpOnly) para os Tokens
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Aplica essa regra a todas as rotas do Monolito
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}

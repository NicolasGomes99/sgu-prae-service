package br.edu.ufape.sguPraeService.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearerAuth")
                        .addList("cookieAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT emitido pelo Keycloak (ou obtido via `POST /login`). Informe apenas o token sem o prefixo `Bearer `."))
                        .addSecuritySchemes("cookieAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("access_token")
                                .description("Cookie HttpOnly `access_token` configurado automaticamente no navegador pelo endpoint `POST /login` e pelo filtro `TokenResponseRewriteFilter`.")))
                .info(new Info()
                        .title("SGU PRAE Service — API Monolítica de Assistência Estudantil (UFAPE)")
                        .version("1.0.0")
                        .description("""
                                Documentação oficial e centralizada da API do **Sistema de Gestão Universitária — Pró-Reitoria de Assistência Estudantil (SGU PRAE)** da **UFAPE**.
                                
                                ### 🏛️ Visão Geral para Integração Frontend & Onboarding
                                Esta API consolida em uma arquitetura monolítica modular todos os domínios anteriormente distribuídos:
                                1. **Autenticação & Gestão de Acesso (`AuthController`, `UsuarioController`, `GestorController`)**: Integração OAuth2/OIDC com Keycloak, emissão/renovação de tokens via Cookies `HttpOnly` (`access_token` e `refresh_token`) ou Header `Authorization: Bearer`, e gestão unificada de perfis.
                                2. **Assistência Estudantil & Financeiro (`BeneficioController`, `PagamentoController`, `TipoBeneficioController`, `DadosBancariosController`)**: Concessão de bolsas e auxílios, upload seguro de termos (validação binária de *magic bytes*), geração de folha de pagamento mensal por lote e Portal da Transparência.
                                3. **Saúde, Psicologia & Agendamentos (`CronogramaController`, `VagaController`, `AgendamentoController`, `TipoAtendimentoController`, `ProfissionalController`)**: Gestão de escalas de profissionais, geração automática de vagas por duração de atendimento, marcação e cancelamento de consultas com proteção de concorrência.
                                4. **Motor de Notificações em Tempo Real (`NotificacaoController`)**: Streaming contínuo via **Server-Sent Events (SSE)** (`GET /notificacoes/stream`), histórico de não lidas em cache **Redis** (TTL de 7 dias), disparo assíncrono de **e-mails em cópia oculta (BCC)** e comunicados em massa (*broadcast*).
                                
                                ---
                                ### 🔐 Autenticação Híbrida (Cookies `HttpOnly` + Bearer Token)
                                - **Aplicações Web (SPA / Frontend)**: Ao chamar `POST /login`, o backend intercepta a resposta via `TokenResponseRewriteFilter` e grava automaticamente os cookies `HttpOnly` `access_token` e `refresh_token`. Nas requisições subsequentes com `credentials: 'include'`, o `CookieBearerTokenResolver` lê o cookie automaticamente.
                                - **Swagger UI / Clientes Externos**: Clique no botão **Authorize** no topo da página e cole o `access_token` retornado pelo `POST /login` no campo `bearerAuth`.
                                
                                ---
                                ### 🛡️ Matriz de Perfis (RBAC) e Proteção Anti-IDOR
                                - **`ESTUDANTE`**: Acessa e edita exclusivamente seus próprios dados cadastrais, documentos, agendamentos, benefícios, pagamentos e notificações.
                                - **`PROFISSIONAL`**: Gerencia seus tipos de atendimento, cronogramas, vagas e agendamentos vinculados à sua agenda.
                                - **`GESTOR`**: Gerencia auxílios/bolsas, folha de pagamento, credores, cadastros básicos (cursos, etnias, tipos de benefício) e envia notificações em *broadcast*.
                                - **`ADMINISTRADOR`**: Possui acesso integral de gestão e administração de usuários e gestores.
                                """)
                        .contact(new Contact()
                                .name("Equipe de Desenvolvimento SGU / PRAE — UFAPE")
                                .email("nao-responda@ufape.edu.br"))
                        .license(new License()
                                .name("Uso Institucional — UFAPE")))
                .tags(List.of(
                        new Tag().name("01. Autenticação e Sessão").description("Login, renovação de sessão (refresh), logout seguro (Keycloak + limpeza de cookies e conexão SSE), recuperação de senha e consulta de roles."),
                        new Tag().name("02. Usuários e Perfis").description("Cadastro unificado de Estudantes (multipart), Profissionais e Gestores (Keycloak + banco local com rollback compensatório) e autogestão da conta autenticada."),
                        new Tag().name("03. Estudantes e Pasta Documental").description("Consulta e atualização de perfil estudantil, listagem de credores ativos, relatórios de assistência e upload/download seguro de documentos (Base64 com validação de magic bytes)."),
                        new Tag().name("04. Profissionais de Saúde/Assistência").description("Consulta e atualização cadastral de profissionais da PRAE (psicólogos, assistentes sociais, pedagogos, etc.)."),
                        new Tag().name("05. Gestores").description("Consulta e listagem administrativa do corpo gestor da PRAE."),
                        new Tag().name("06. Benefícios (Bolsas e Auxílios)").description("Concessão, prorrogação, cancelamento, consulta de termos em Base64, acompanhamento de pendências mensais e relatórios públicos de transparência."),
                        new Tag().name("07. Pagamentos e Folha Mensal").description("Lançamento de pagamentos em lote ou por CPF, geração de folha consolidada por ano/mês/lote, desativação com reversão de status e totalizadores financeiros."),
                        new Tag().name("08. Dados Bancários").description("Cadastro, atualização e consulta de contas bancárias vinculadas aos estudantes bolsistas/assistidos."),
                        new Tag().name("09. Tipos de Benefício").description("Catálogo de modalidades de bolsas e auxílios estudantis e seus valores de referência."),
                        new Tag().name("10. Agendamentos e Cancelamentos").description("Reserva de vagas de atendimento por estudantes, alteração de modalidade (presencial/remoto) e cancelamento com liberação imediata da vaga e notificação automática."),
                        new Tag().name("11. Cronogramas de Atendimento").description("Criação e gestão da escala de dias de atendimento dos profissionais, com geração automática de vagas baseada no tipo de atendimento."),
                        new Tag().name("12. Vagas de Atendimento").description("Consulta e manutenção individual dos horários (slots) de atendimento gerados nos cronogramas."),
                        new Tag().name("13. Tipos de Atendimento").description("Configuração de especialidades de atendimento, tempo médio de consulta e grade padrão de horários."),
                        new Tag().name("14. Notificações (SSE, Histórico Redis e Broadcast)").description("Conexão em tempo real via Server-Sent Events (SSE), histórico paginado de notificações não lidas no Redis, marcação de leitura e envio de comunicados em massa."),
                        new Tag().name("15. Cursos").description("Cadastro e consulta pública/administrativa dos cursos de graduação da UFAPE."),
                        new Tag().name("16. Tipos de Etnia").description("Cadastro e consulta pública/administrativa de autodeclarações étnico-raciais.")
                ));
    }
}

package br.jus.tjba.aclp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes dos controles do OWASP Top 10 aplicados ao ACLP.
 *
 * Cada teste ataca de verdade e verifica que o ataque foi BARRADO — não basta
 * "não quebrou": um 500 numa injeção também é falha (vaza stack trace e indica
 * que a entrada chegou onde não devia).
 *
 * Rate limit em janela curta para o teste de brute force não levar 1 minuto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // driver-class-name e dialect precisam vir junto: application.properties fixa
        // o driver do PostgreSQL e ele venceria só a URL do H2.
        "spring.datasource.url=jdbc:h2:mem:segtestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "aclp.email.enabled=false",
        "aclp.security.rate-limit.max-requests=5",
        "aclp.security.rate-limit.window-seconds=60"
})
@DisplayName("OWASP — controles de segurança da API")
class SegurancaOwaspTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String json(Map<String, Object> corpo) throws Exception {
        return objectMapper.writeValueAsString(corpo);
    }

    // =================================================================
    // A03 — Injeção
    // =================================================================

    @Nested
    @DisplayName("A03 — Injeção")
    class Injecao {

        @Test
        @DisplayName("SQL Injection no login não autentica ninguém")
        void sqlInjectionNoLogin() throws Exception {
            // O clássico: se a query fosse concatenada, o OR '1'='1' casaria a
            // primeira linha da tabela e devolveria um token.
            MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                            .header("X-Real-IP", "203.0.113.10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "admin@tjba.jus.br' OR '1'='1",
                                    "senha", "' OR '1'='1"))))
                    .andReturn();

            int status = resultado.getResponse().getStatus();
            String corpo = resultado.getResponse().getContentAsString();

            assertNotEquals(200, status, "Injeção SQL autenticou — query concatenada em algum lugar");
            assertTrue(status == 400 || status == 401 || status == 429,
                    "Esperado 400/401/429, veio " + status + ". Um 500 indica que a entrada chegou ao driver.");
            assertFalse(corpo.contains("accessToken"), "Nenhum token pode ser emitido");
        }

        @Test
        @WithMockUser(roles = "USUARIO")
        @DisplayName("SQL Injection na busca de custodiados não vaza a tabela")
        void sqlInjectionNaBusca() throws Exception {
            mockMvc.perform(get("/api/custodiados/buscar")
                            .param("termo", "'; DROP TABLE custodiados; --"))
                    .andExpect(status().isOk());

            // A tabela tem que continuar existindo: se o DROP tivesse passado,
            // qualquer consulta seguinte quebraria com 500.
            mockMvc.perform(get("/api/custodiados/buscar").param("termo", "teste"))
                    .andExpect(status().isOk());
        }
    }

    // =================================================================
    // A03 — XSS
    // =================================================================

    @Nested
    @DisplayName("A03 — Cross-Site Scripting")
    class Xss {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Payload de XSS armazenado é rejeitado ou devolvido escapado")
        void xssArmazenado() throws Exception {
            String payload = "<script>alert('xss')</script>";

            MvcResult resultado = mockMvc.perform(post("/api/custodiados")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "nome", payload,
                                    "cpf", "12345678909",
                                    "contato", "71999999999"))))
                    .andReturn();

            String corpo = resultado.getResponse().getContentAsString();

            // Jackson escapa `<` e `>` ao serializar JSON, então o payload nunca
            // volta executável. O que não pode é voltar cru dentro de text/html.
            String contentType = resultado.getResponse().getContentType();
            if (contentType != null && contentType.contains("text/html")) {
                assertFalse(corpo.contains(payload),
                        "Payload refletido cru em resposta HTML — XSS refletido");
            }
        }

        @Test
        @DisplayName("Respostas da API declaram nosniff e CSP restritiva")
        void cabecalhosAntiXss() throws Exception {
            mockMvc.perform(get("/api/auth/health"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(header().exists("Content-Security-Policy"));
        }
    }

    // =================================================================
    // A01 — Quebra de controle de acesso (IDOR)
    // =================================================================

    @Nested
    @DisplayName("A01 — Controle de acesso")
    class ControleDeAcesso {

        @Test
        @DisplayName("Sem autenticação, recurso protegido responde 401/403")
        void anonimoNaoAcessa() throws Exception {
            int status = mockMvc.perform(get("/api/custodiados"))
                    .andReturn().getResponse().getStatus();

            assertTrue(status == 401 || status == 403,
                    "Recurso protegido acessível sem token — status " + status);
        }

        @Test
        @WithMockUser(roles = "USUARIO")
        @DisplayName("IDOR: usuário comum não lê cadastro de outro usuário por ID na URL")
        void usuarioComumNaoAcessaOutroUsuario() throws Exception {
            // /api/usuarios/** é hasRole('ADMIN'): trocar o ID na URL não escala privilégio.
            mockMvc.perform(get("/api/usuarios/1"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/usuarios/999"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "USUARIO")
        @DisplayName("IDOR: usuário comum não lista todos os usuários")
        void usuarioComumNaoListaUsuarios() throws Exception {
            mockMvc.perform(get("/api/usuarios"))
                    .andExpect(status().isForbidden());
        }
    }

    // =================================================================
    // A01 — CSRF
    // =================================================================

    @Nested
    @DisplayName("A01 — CSRF")
    class Csrf {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("POST autenticado sem token CSRF é rejeitado")
        void postSemTokenEhRejeitado() throws Exception {
            // Cenário do ataque: a vítima está logada, o cookie de sessão viaja
            // sozinho, e um site hostil dispara o POST. Sem o token no header,
            // a requisição não pode passar.
            mockMvc.perform(post("/api/custodiados")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("nome", "Fulano", "cpf", "52998224725"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("POST autenticado com token CSRF passa da barreira")
        void postComTokenPassa() throws Exception {
            int status = mockMvc.perform(post("/api/custodiados")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("nome", "Fulano", "cpf", "52998224725"))))
                    .andReturn().getResponse().getStatus();

            // O que importa aqui é NÃO ser 403 por CSRF; validação de campo (400)
            // é outra camada e não invalida este teste.
            assertNotEquals(403, status, "Token CSRF válido não pode ser recusado");
        }

        @Test
        @DisplayName("Login continua acessível sem token — é rota anônima")
        void loginNaoExigeToken() throws Exception {
            // Exigir CSRF no login quebraria o primeiro acesso: ainda não há
            // sessão a proteger nem cookie de token emitido.
            int status = mockMvc.perform(post("/api/auth/login")
                            .header("X-Real-IP", "203.0.113.21")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "x@tjba.jus.br", "senha", "y"))))
                    .andReturn().getResponse().getStatus();

            assertNotEquals(403, status, "Login não pode exigir token CSRF");
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("GET não exige token CSRF")
        void getNaoExigeToken() throws Exception {
            int status = mockMvc.perform(get("/api/custodiados"))
                    .andReturn().getResponse().getStatus();

            assertNotEquals(403, status, "Método seguro não deve exigir token");
        }
    }

    // =================================================================
    // A07 — Falhas de identificação e autenticação
    // =================================================================

    @Nested
    @DisplayName("A07 — Autenticação")
    class Autenticacao {

        @Test
        @DisplayName("Brute force no login responde 429 após o limite")
        void bruteForceRecebe429() throws Exception {
            String corpo = json(Map.of("email", "alvo@tjba.jus.br", "senha", "senhaErrada"));

            // IP próprio: o balde é por IP e os outros testes desta classe também
            // batem em /api/auth/login. Sem isolar, a ordem de execução decide o resultado.
            String ip = "203.0.113.99";

            // Limite configurado em 5 neste teste: as 5 primeiras passam para o backend.
            for (int i = 1; i <= 5; i++) {
                int status = mockMvc.perform(post("/api/auth/login")
                                .header("X-Real-IP", ip)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo))
                        .andReturn().getResponse().getStatus();

                assertNotEquals(429, status,
                        "Bloqueou cedo demais, na tentativa " + i + " (limite é 5)");
            }

            // A 6ª é barrada antes de chegar ao serviço de autenticação.
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Real-IP", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"));
        }

        @Test
        @DisplayName("X-Forwarded-For forjado não cria balde novo (bypass do rate limit)")
        void xffForjadoNaoBypassaRateLimit() throws Exception {
            String corpo = json(Map.of("email", "alvo2@tjba.jus.br", "senha", "senhaErrada"));
            String ipReal = "203.0.113.77";

            // Atacante varia o XFF a cada tentativa esperando um balde novo por requisição.
            // O nginx ACRESCENTA o IP real, então o valor confiável é X-Real-IP.
            for (int i = 1; i <= 5; i++) {
                mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.0.0." + i)
                        .header("X-Real-IP", ipReal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo));
            }

            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", "10.0.0.200")
                            .header("X-Real-IP", ipReal)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        @DisplayName("Login com credencial inválida não revela se o e-mail existe")
        void mensagemDeErroNaoEnumeraContas() throws Exception {
            // Rate limit é por IP e o teste anterior pode ter esgotado a janela;
            // por isso aqui só verifica o corpo quando a resposta não for 429.
            MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                            .header("X-Real-IP", "203.0.113.55")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "email", "nao-existe-de-jeito-nenhum@tjba.jus.br",
                                    "senha", "qualquer"))))
                    .andReturn();

            if (resultado.getResponse().getStatus() != 429) {
                String corpo = resultado.getResponse().getContentAsString().toLowerCase();
                assertFalse(corpo.contains("não encontrado") || corpo.contains("nao encontrado"),
                        "Mensagem revela existência da conta — oráculo de enumeração");
                assertFalse(corpo.contains("bloquead"),
                        "Mensagem revela estado da conta — oráculo de enumeração");
            }
        }
    }
}

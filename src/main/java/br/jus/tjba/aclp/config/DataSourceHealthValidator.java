package br.jus.tjba.aclp.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * Valida a conexão com o banco de dados logo após o boot da aplicação
 * e produz mensagens de erro claras e acionáveis quando há falha.
 *
 * Esta classe não substitui a configuração do HikariCP — ela apenas roda
 * uma vez no startup para diagnosticar e logar problemas de credencial
 * antes que eles se manifestem em produção como "Connection is not available,
 * request timed out".
 *
 * Ativado apenas em prod/render onde o problema costuma aparecer.
 */
@Slf4j
@Component
@Profile({"prod", "render"})
@RequiredArgsConstructor
public class DataSourceHealthValidator {

    private final DataSource dataSource;

    @Value("${spring.datasource.url:not-configured}")
    private String datasourceUrl;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @PostConstruct
    public void validateConnection() {
        log.info("============================================================");
        log.info("  VALIDAÇÃO DE CONECTIVIDADE COM BANCO DE DADOS");
        log.info("  Profile ativo: {}", activeProfile);
        log.info("  JDBC URL: {}", maskUrl(datasourceUrl));
        log.info("============================================================");

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            log.info("✅ Conexão estabelecida com sucesso");
            log.info("   Database: {} {}", md.getDatabaseProductName(), md.getDatabaseProductVersion());
            log.info("   URL ativa: {}", maskUrl(md.getURL()));
            log.info("   Usuário: {}", md.getUserName());
            log.info("============================================================");
        } catch (SQLException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            log.error("❌ FALHA NA CONEXÃO COM O BANCO DE DADOS");
            log.error("   Mensagem original: {}", msg);
            log.error("   SQLState: {}", ex.getSQLState());
            log.error("   ErrorCode: {}", ex.getErrorCode());
            log.error("------------------------------------------------------------");
            log.error("   DIAGNÓSTICO PROVÁVEL:");

            if (msg.contains("ENOTFOUND") && msg.contains("tenant/user")) {
                log.error("   ➜ O pooler do Supabase NÃO encontrou seu projeto/usuário.");
                log.error("     Causas comuns:");
                log.error("       1) Projeto Supabase está PAUSADO (free tier após 7 dias inativo)");
                log.error("          → Acesse https://supabase.com/dashboard e reative o projeto.");
                log.error("       2) Username no pooler precisa ser 'postgres.<project-ref>'");
                log.error("          (não apenas 'postgres').");
                log.error("       3) Senha foi rotacionada no Supabase mas não foi atualizada");
                log.error("          nas variáveis de ambiente do Render.");
                log.error("       4) Project-ref no hostname está incorreto.");
            } else if (msg.contains("password authentication failed")) {
                log.error("   ➜ Senha incorreta. Verifique SPRING_DATASOURCE_PASSWORD/");
                log.error("     DATABASE_PASSWORD no Render contra a senha atual do Supabase.");
            } else if (msg.contains("does not exist")) {
                log.error("   ➜ Database/role não existe. Verifique a URL e username.");
            } else if (msg.contains("timed out") || msg.contains("timeout")) {
                log.error("   ➜ Timeout de conexão. Possíveis causas:");
                log.error("       1) Firewall/IP bloqueado");
                log.error("       2) Servidor de banco indisponível");
                log.error("       3) Porta incorreta (pooler usa 6543, direct 5432)");
            } else if (msg.toLowerCase().contains("unknown host")
                    || msg.toLowerCase().contains("name or service not known")) {
                log.error("   ➜ Hostname não resolve. Verifique a URL JDBC.");
            } else {
                log.error("   ➜ Verifique URL, usuário, senha e SSL.");
            }

            log.error("------------------------------------------------------------");
            log.error("   CHECKLIST RÁPIDO:");
            log.error("     [ ] Projeto Supabase está ativo (não pausado)?");
            log.error("     [ ] SPRING_DATASOURCE_URL contém o project-ref correto?");
            log.error("     [ ] SPRING_DATASOURCE_USERNAME = 'postgres.<project-ref>'?");
            log.error("     [ ] SPRING_DATASOURCE_PASSWORD bate com a senha atual?");
            log.error("     [ ] Porta = 6543 (pooler) ou 5432 (direct)?");
            log.error("     [ ] sslmode=require na URL?");
            log.error("============================================================");

            // Re-lança encapsulada para abortar o boot — o Render reiniciará e
            // o operador verá a mensagem de diagnóstico claramente no log.
            throw new IllegalStateException(
                    "Não foi possível conectar ao banco de dados. Veja diagnóstico acima.", ex);
        }
    }

    /**
     * Mascara credenciais embutidas em URLs JDBC para logs seguros.
     */
    static String maskUrl(String url) {
        if (url == null) return "null";
        // Mascarar senha em URLs no formato jdbc:...//user:senha@host
        return url.replaceAll("(://[^:]+:)[^@]+(@)", "$1***$2");
    }
}

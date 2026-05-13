package br.jus.tjba.aclp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifica, antes do contexto Spring subir, se as variáveis de ambiente
 * necessárias para a conexão com o banco estão presentes e bem formadas.
 *
 * Detecta problemas comuns em deploys (Render / Heroku-like):
 *   - URL JDBC ausente ou vazia
 *   - URL usando o pooler do Supabase mas username sem o sufixo .<project-ref>
 *   - URL com hostname mas porta padrão do Postgres direto (5432) — geralmente
 *     o pooler do Supabase exige 6543.
 *
 * Os avisos saem como WARN — não bloqueiam o boot — porque a configuração
 * pode ser intencional. Apenas chamam atenção do operador antes do erro
 * de "Connection is not available" aparecer 20 segundos depois.
 */
@Slf4j
@Component
public class DataSourceConfigChecker
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final List<String> URL_KEYS = Arrays.asList(
            "SPRING_DATASOURCE_URL", "DATABASE_URL", "spring.datasource.url");
    private static final List<String> USER_KEYS = Arrays.asList(
            "SPRING_DATASOURCE_USERNAME", "DATABASE_USERNAME", "spring.datasource.username");
    private static final List<String> PASS_KEYS = Arrays.asList(
            "SPRING_DATASOURCE_PASSWORD", "DATABASE_PASSWORD", "spring.datasource.password");

    // Padrão de hostname do Supabase Pooler: pool*.supabase.com ou *.pooler.supabase.com
    private static final Pattern SUPABASE_POOLER = Pattern.compile(
            "(?i)(aws-\\d+-[\\w-]+\\.pooler\\.supabase\\.com|pooler\\.supabase\\.com)");

    // Project-ref está embutido no hostname antigo (postgres.<ref>) ou no username
    private static final Pattern PROJECT_REF = Pattern.compile(
            "(?i)postgres\\.([a-z0-9]{16,})");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        String[] profiles = env.getActiveProfiles();
        boolean isProdLike = profiles.length > 0 && Arrays.stream(profiles)
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("render"));

        if (!isProdLike) return;

        String url = firstNonBlank(env, URL_KEYS);
        String user = firstNonBlank(env, USER_KEYS);
        boolean hasPass = firstNonBlank(env, PASS_KEYS) != null;

        if (url == null) {
            log.warn("⚠️  Nenhuma JDBC URL configurada (esperado: SPRING_DATASOURCE_URL ou DATABASE_URL).");
            return;
        }
        if (user == null) {
            log.warn("⚠️  JDBC URL definida mas username NÃO foi configurado.");
        }
        if (!hasPass) {
            log.warn("⚠️  JDBC URL definida mas password NÃO foi configurado.");
        }

        boolean isPooler = SUPABASE_POOLER.matcher(url).find();

        if (isPooler && user != null && !user.startsWith("postgres.")) {
            log.warn("⚠️  URL aparenta ser do Supabase Pooler, mas o username '{}' não está no formato esperado.", user);
            log.warn("    O pooler exige username 'postgres.<project-ref>'.");
            log.warn("    Isso costuma gerar o erro 'FATAL: (ENOTFOUND) tenant/user ... not found'.");
        }

        // Aviso clássico: pooler na porta 5432
        if (isPooler && url.contains(":5432/")) {
            log.warn("⚠️  URL usa pooler do Supabase mas porta 5432 — verifique se não deveria ser 6543.");
        }

        // Conexão direta (sem pooler) na porta 6543 também é suspeita
        if (!isPooler && url.contains(":6543/")) {
            log.warn("⚠️  Porta 6543 sem pooler — verifique se a URL está correta.");
        }

        // Project-ref no host antigo
        Matcher m = PROJECT_REF.matcher(url);
        if (m.find()) {
            log.info("ℹ️  Project-ref detectado na URL: {}", m.group(1));
            if (user != null && !user.contains(m.group(1))) {
                log.warn("⚠️  Project-ref no username NÃO bate com o da URL. Verifique.");
            }
        }
    }

    private static String firstNonBlank(ConfigurableEnvironment env, List<String> keys) {
        for (String k : keys) {
            String v = env.getProperty(k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}

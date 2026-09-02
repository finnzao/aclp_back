package br.jus.tjba.aclp.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Recusa iniciar em producao com segredo fraco, de dev ou que ja esteve versionado
 * no codigo-fonte. Em dev os placeholders passam com aviso.
 */
@Slf4j
@Component
@RequiredArgsConstructor
// Desligavel por config (uso local com profile prod). Default: ligado.
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "aclp.security.secrets-validator.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SecretsStartupValidator {

    /** Perfis tratados como produção para efeito desta validação. */
    private static final Set<String> PERFIS_DE_PRODUCAO = Set.of("prod", "render");

    /**
     * Valores que não podem aparecer em produção: os que vazaram no código-fonte e os
     * placeholders de desenvolvimento. Comparação em minúsculas, sem espaços.
     */
    private static final Set<String> VALORES_PROIBIDOS = Set.of(
            // segredos de JWT que estiveram versionados
            "ef0df005bd23fd4c85016a23ffe69d739a2e8737ceefbd7b106b1088f11b0d58feac1afce11a3037cd12d771b101c733",
            "17603f59832493e86c292cb40dbe9f1e135b38954bab99b0af0166439481627062b999463b49bcf551a874a3230cfed31ab5c2a9",
            // senha de aplicativo do Gmail que estava em texto puro
            "yfhxdebaeqksyygj",
            // senhas fracas que estavam fixas no arquivo
            "aclp123",
            "admin123456",
            // placeholders de desenvolvimento introduzidos ao limpar o arquivo
            "dev-local-only",
            "dev-local-only-nao-use-em-producao-0123456789abcdef"
    );

    /** HS256 exige chave de no mínimo 256 bits — abaixo disso a própria JJWT recusa. */
    private static final int MINIMO_BYTES_SEGREDO_JWT = 32;

    private final Environment environment;

    @Value("${aclp.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String senhaBanco;

    @Value("${aclp.admin.bootstrap.password:}")
    private String senhaAdminInicial;

    @Value("${spring.mail.password:}")
    private String senhaEmail;

    @PostConstruct
    public void validar() {
        List<String> problemas = new ArrayList<>();

        // O segredo do JWT é verificado em todo perfil: chave curta demais quebra a
        // assinatura em runtime, e falhar no boot é melhor que falhar no primeiro login.
        if (jwtSecret == null || jwtSecret.isBlank()) {
            problemas.add("aclp.jwt.secret não está definido (variável JWT_SECRET)");
        } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MINIMO_BYTES_SEGREDO_JWT) {
            problemas.add("aclp.jwt.secret é curto demais para HS256: precisa de pelo menos "
                    + MINIMO_BYTES_SEGREDO_JWT + " bytes");
        }

        if (!emProducao()) {
            if (!problemas.isEmpty()) {
                throw new IllegalStateException(montarMensagem(problemas));
            }
            log.warn("Perfil de desenvolvimento: segredos de placeholder são aceitos. "
                    + "NUNCA use este ambiente com dados reais.");
            return;
        }

        // Daqui para baixo, só produção.
        verificarProibido(problemas, "aclp.jwt.secret (JWT_SECRET)", jwtSecret);
        verificarProibido(problemas, "spring.datasource.password (DB_PASSWORD)", senhaBanco);
        verificarProibido(problemas, "aclp.admin.bootstrap.password (ADMIN_BOOTSTRAP_PASSWORD)", senhaAdminInicial);
        verificarProibido(problemas, "spring.mail.password (MAIL_PASSWORD)", senhaEmail);

        if (!problemas.isEmpty()) {
            throw new IllegalStateException(montarMensagem(problemas));
        }

        log.info("Validação de segredos: nenhum valor comprometido ou de desenvolvimento em uso.");
    }

    private void verificarProibido(List<String> problemas, String nomeDaChave, String valor) {
        if (valor == null || valor.isBlank()) {
            return; // ausência é tratada por quem exige a propriedade, não aqui
        }
        if (VALORES_PROIBIDOS.contains(valor.trim().toLowerCase(Locale.ROOT))) {
            problemas.add(nomeDaChave + " está usando um valor que já esteve no código-fonte "
                    + "ou é placeholder de desenvolvimento — precisa ser rotacionado");
        }
    }

    private boolean emProducao() {
        for (String perfil : environment.getActiveProfiles()) {
            if (PERFIS_DE_PRODUCAO.contains(perfil)) {
                return true;
            }
        }
        return false;
    }

    private String montarMensagem(List<String> problemas) {
        StringBuilder sb = new StringBuilder("\n")
                .append("=====================================================================\n")
                .append(" INICIALIZAÇÃO ABORTADA — problema de configuração de segredos\n")
                .append("=====================================================================\n");
        problemas.forEach(p -> sb.append("  - ").append(p).append('\n'));
        sb.append("\n")
                .append(" Gere um segredo novo com:\n")
                .append("     openssl rand -hex 32\n")
                .append(" e defina-o na variável de ambiente correspondente.\n")
                .append(" Ver Checklist.md, seção 7.\n")
                .append("=====================================================================");
        return sb.toString();
    }
}

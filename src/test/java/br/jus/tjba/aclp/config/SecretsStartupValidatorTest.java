package br.jus.tjba.aclp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O valor desta classe está inteiramente em RECUSAR o boot. Um teste que só confirma
 * o caminho feliz não prova nada — o que importa é que cada segredo vazado seja
 * efetivamente barrado em produção.
 */
@DisplayName("Validação de segredos na inicialização")
class SecretsStartupValidatorTest {

    /** Segredo forte fictício, com folga sobre o mínimo de 32 bytes do HS256. */
    private static final String SEGREDO_FORTE =
            "b7e4c1a09f3d5e8b2a6c4f7d1e9b3a5c8d2f6e4b7a1c9d3f5e8b2a6c4f7d1e9b";

    private static final String JWT_VAZADO_DEFAULT =
            "ef0df005bd23fd4c85016a23ffe69d739a2e8737ceefbd7b106b1088f11b0d58feac1afce11a3037cd12d771b101c733";

    private static final String JWT_VAZADO_PROD =
            "17603f59832493e86c292cb40dbe9f1e135b38954bab99b0af0166439481627062b999463b49bcf551a874a3230cfed31ab5c2a9";

    private SecretsStartupValidator validador(String perfil, String jwt, String banco,
                                              String admin, String email) {
        MockEnvironment env = new MockEnvironment();
        if (perfil != null) {
            env.setActiveProfiles(perfil);
        }
        SecretsStartupValidator v = new SecretsStartupValidator(env);
        ReflectionTestUtils.setField(v, "jwtSecret", jwt);
        ReflectionTestUtils.setField(v, "senhaBanco", banco);
        ReflectionTestUtils.setField(v, "senhaAdminInicial", admin);
        ReflectionTestUtils.setField(v, "senhaEmail", email);
        return v;
    }

    private SecretsStartupValidator emProducaoCom(String jwt) {
        return validador("prod", jwt, "senha-forte-do-banco", "senha-forte-admin", "senha-forte-email");
    }

    @Test
    @DisplayName("Produção sobe quando todos os segredos são fortes e próprios")
    void producaoComSegredosProprios() {
        assertDoesNotThrow(() -> emProducaoCom(SEGREDO_FORTE).validar());
    }

    @Test
    @DisplayName("Produção recusa o segredo de JWT que estava no application.properties")
    void producaoRecusaJwtVazadoDoPerfilPadrao() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> emProducaoCom(JWT_VAZADO_DEFAULT).validar());
        assertTrue(e.getMessage().contains("JWT_SECRET"));
        assertTrue(e.getMessage().contains("rotacionado"));
    }

    @Test
    @DisplayName("Produção recusa o segredo de JWT que era o default embutido do perfil prod")
    void producaoRecusaJwtVazadoDoPerfilProd() {
        assertThrows(IllegalStateException.class,
                () -> emProducaoCom(JWT_VAZADO_PROD).validar());
    }

    @Test
    @DisplayName("Produção recusa placeholder de desenvolvimento")
    void producaoRecusaPlaceholderDeDesenvolvimento() {
        assertThrows(IllegalStateException.class,
                () -> emProducaoCom("dev-local-only-nao-use-em-producao-0123456789abcdef").validar());
    }

    @Test
    @DisplayName("Produção recusa a senha do banco e a do admin inicial que estavam fixas")
    void producaoRecusaSenhasFracasConhecidas() {
        assertThrows(IllegalStateException.class, () ->
                validador("prod", SEGREDO_FORTE, "aclp123", "senha-forte-admin", "x").validar());

        assertThrows(IllegalStateException.class, () ->
                validador("prod", SEGREDO_FORTE, "senha-forte", "admin123456", "x").validar());
    }

    @Test
    @DisplayName("Produção recusa a senha de aplicativo do Gmail que estava em texto puro")
    void producaoRecusaSenhaDeEmailVazada() {
        assertThrows(IllegalStateException.class, () ->
                validador("prod", SEGREDO_FORTE, "senha-forte", "senha-forte", "yfhxdebaeqksyygj").validar());
    }

    @Test
    @DisplayName("Segredo curto demais para HS256 é recusado em qualquer perfil")
    void segredoCurtoEhRecusado() {
        // HS256 exige 256 bits; a propria JJWT lancaria excecao ao assinar — melhor
        // falhar no boot que no primeiro login de um servidor da vara.
        assertThrows(IllegalStateException.class,
                () -> emProducaoCom("curto-demais").validar());

        assertThrows(IllegalStateException.class,
                () -> validador("dev", "curto-demais", "x", "y", "z").validar());
    }

    @Test
    @DisplayName("Segredo ausente é recusado")
    void segredoAusenteEhRecusado() {
        assertThrows(IllegalStateException.class,
                () -> emProducaoCom("").validar());
    }

    @Test
    @DisplayName("Desenvolvimento aceita placeholders, mas exige segredo de tamanho válido")
    void desenvolvimentoAceitaPlaceholder() {
        assertDoesNotThrow(() -> validador(
                "dev",
                "dev-local-only-nao-use-em-producao-0123456789abcdef",
                "dev-local-only", "dev-local-only", "").validar());
    }

    @Test
    @DisplayName("Sem perfil ativo o comportamento é de desenvolvimento, não de produção")
    void semPerfilTrataComoDesenvolvimento() {
        assertDoesNotThrow(() -> validador(
                null,
                "dev-local-only-nao-use-em-producao-0123456789abcdef",
                "aclp123", "admin123456", "").validar());
    }
}

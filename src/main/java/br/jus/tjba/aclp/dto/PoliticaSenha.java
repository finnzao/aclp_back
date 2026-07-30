package br.jus.tjba.aclp.dto;

/**
 * Política única de senha do sistema.
 *
 * Existia antes uma regra diferente por porta de entrada: o setup inicial exigia
 * composição forte, o convite exigia só 8 caracteres e a troca de senha do perfil
 * aceitava 6. Na prática valia sempre a mais fraca — bastava criar a conta por
 * convite, ou trocar a senha depois, para contornar a política do setup.
 *
 * As constantes ficam aqui porque valores de anotação precisam ser constantes de
 * compilação: referenciá-las nos DTOs é o que garante que as regras não voltem a
 * divergir silenciosamente.
 */
public final class PoliticaSenha {

    private PoliticaSenha() {
    }

    public static final int TAMANHO_MINIMO = 8;
    public static final int TAMANHO_MAXIMO = 100;

    /**
     * Exige minúscula, maiúscula, dígito e ao menos um caractere não alfanumérico.
     *
     * Deliberadamente não há lista branca de símbolos. A regra anterior só aceitava
     * {@code @$!%*?&#} e reprovava senhas como {@code Senha_2026} — mais fortes, não
     * mais fracas. Restringir o alfabeto reduz o espaço de busca sem ganho algum, e
     * ainda divergia do formulário do frontend, que sempre aceitou qualquer símbolo.
     */
    public static final String REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$";

    public static final String MENSAGEM_COMPOSICAO =
            "Senha deve conter: 1 minúscula, 1 maiúscula, 1 número e 1 caractere especial";

    public static final String MENSAGEM_TAMANHO =
            "Senha deve ter entre 8 e 100 caracteres";
}

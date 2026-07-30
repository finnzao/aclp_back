package br.jus.tjba.aclp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para alteração de senha do próprio usuário
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlterarSenhaDTO {

    @NotBlank(message = "Senha atual é obrigatória")
    private String senhaAtual;

    @NotBlank(message = "Nova senha é obrigatória")
    @Size(min = PoliticaSenha.TAMANHO_MINIMO, max = PoliticaSenha.TAMANHO_MAXIMO,
            message = PoliticaSenha.MENSAGEM_TAMANHO)
    @Pattern(regexp = PoliticaSenha.REGEX, message = PoliticaSenha.MENSAGEM_COMPOSICAO)
    private String novaSenha;

    @NotBlank(message = "Confirmação de senha é obrigatória")
    private String confirmarSenha;
}
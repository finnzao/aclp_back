package br.jus.tjba.aclp.dto.convite;

import br.jus.tjba.aclp.dto.PoliticaSenha;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class AtivarConviteRequest {
    @NotBlank(message = "Token é obrigatório")
    private String token;

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = PoliticaSenha.TAMANHO_MINIMO, max = PoliticaSenha.TAMANHO_MAXIMO,
            message = PoliticaSenha.MENSAGEM_TAMANHO)
    @Pattern(regexp = PoliticaSenha.REGEX, message = PoliticaSenha.MENSAGEM_COMPOSICAO)
    private String senha;

    @NotBlank(message = "Confirmação de senha é obrigatória")
    private String confirmaSenha;

    // Campos opcionais que o usuário pode preencher
    private String telefone;
    private String cargo;

    public boolean senhasCoincidentes() {
        return senha != null && senha.equals(confirmaSenha);
    }
}

package br.jus.tjba.aclp.dto.convite;

import jakarta.validation.constraints.NotBlank;
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
    @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
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

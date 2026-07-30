package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioDTO {

    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    private String email;

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = PoliticaSenha.TAMANHO_MINIMO, max = PoliticaSenha.TAMANHO_MAXIMO,
            message = PoliticaSenha.MENSAGEM_TAMANHO)
    @Pattern(regexp = PoliticaSenha.REGEX, message = PoliticaSenha.MENSAGEM_COMPOSICAO)
    private String senha;

    @NotNull(message = "Tipo é obrigatório")
    private TipoUsuario tipo;

    private String departamento;

    private String comarca;

    private String cargo;

    private Boolean ativo;
}
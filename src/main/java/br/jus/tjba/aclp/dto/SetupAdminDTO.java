package br.jus.tjba.aclp.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetupAdminDTO {

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 150, message = "Nome deve ter entre 3 e 150 caracteres")
    @Pattern(regexp = "^[A-Za-zÀ-ÿ\\s'.-]+$", message = "Nome deve conter apenas letras, espaços e caracteres especiais válidos")
    private String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    @Pattern(regexp = ".*@tjba\\.jus\\.br$", message = "Email deve ser institucional (@tjba.jus.br)")
    @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
    private String email;

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, max = 100, message = "Senha deve ter entre 8 e 100 caracteres")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Senha deve conter: 1 minúscula, 1 maiúscula, 1 número e 1 símbolo (@$!%*?&)")
    private String senha;

    @NotBlank(message = "Confirmação de senha é obrigatória")
    private String confirmaSenha;

    @Size(max = 100, message = "Departamento deve ter no máximo 100 caracteres")
    private String departamento;

    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
    @Pattern(regexp = "\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}",
            message = "Telefone deve ter formato válido (ex: (71) 99999-9999)")
    private String telefone;

    /**
     * Valida se as senhas coincidem
     */
    @AssertTrue(message = "Senhas não coincidem")
    public boolean isSenhasCoincidentes() {
        return senha != null && senha.equals(confirmaSenha);
    }

    /**
     * Valida se o nome é completo (pelo menos nome e sobrenome)
     */
    @AssertTrue(message = "Informe nome completo (nome e sobrenome)")
    public boolean isNomeCompleto() {
        return nome != null && nome.trim().split("\\s+").length >= 2;
    }

    /**
     * Limpa e formata os dados
     */
    public void limparEFormatarDados() {
        if (nome != null) {
            nome = nome.trim();
        }
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        if (departamento != null) {
            departamento = departamento.trim();
        }
        if (telefone != null) {
            telefone = telefone.trim();
        }
    }

    /**
     * Valida força da senha
     */
    public boolean isSenhaForte() {
        if (senha == null || senha.length() < 8) {
            return false;
        }

        boolean temMinuscula = senha.chars().anyMatch(Character::isLowerCase);
        boolean temMaiuscula = senha.chars().anyMatch(Character::isUpperCase);
        boolean temNumero = senha.chars().anyMatch(Character::isDigit);
        boolean temSimbolo = senha.chars().anyMatch(ch -> "@$!%*?&".indexOf(ch) >= 0);

        return temMinuscula && temMaiuscula && temNumero && temSimbolo;
    }
}
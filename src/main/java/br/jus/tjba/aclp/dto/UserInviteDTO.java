package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTOs para o sistema de convites de usuários
 */
public class UserInviteDTO {

    /**
     * DTO para criar novo convite (usado por ADMIN)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CriarConviteDTO {

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 3, max = 150, message = "Nome deve ter entre 3 e 150 caracteres")
        private String nome;

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ser válido")
        @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
        private String email;

        @NotNull(message = "Tipo de usuário é obrigatório")
        private TipoUsuario tipoUsuario;

        @Size(max = 100, message = "Departamento deve ter no máximo 100 caracteres")
        private String departamento;

        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
        @Pattern(regexp = "\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}",
                message = "Telefone deve ter formato válido")
        private String telefone;

        @Size(max = 200, message = "Escopo deve ter no máximo 200 caracteres")
        private String escopo; // Unidade/Lotação

        @Min(value = 24, message = "Validade mínima é 24 horas")
        @Max(value = 168, message = "Validade máxima é 168 horas (7 dias)")
        private Integer validadeHoras = 72; // Padrão: 72 horas

        private String mensagemPersonalizada; // Mensagem adicional no email
    }

    /**
     * DTO de resposta ao criar convite
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConviteResponseDTO {
        private Long id;
        private String token;
        private String email;
        private String nome;
        private TipoUsuario tipoUsuario;
        private String linkAtivacao;
        private LocalDateTime expiraEm;
        private Long horasValidade;
        private String status;
        private String message;
    }

    /**
     * DTO para ativar convite (primeiro acesso)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AtivarConviteDTO {

        @NotBlank(message = "Token é obrigatório")
        private String token;

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, max = 100, message = "Senha deve ter entre 8 e 100 caracteres")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
                message = "Senha deve conter: 1 minúscula, 1 maiúscula, 1 número e 1 símbolo")
        private String senha;

        @NotBlank(message = "Confirmação de senha é obrigatória")
        private String confirmaSenha;

        private Boolean habilitarMFA = false; // Opcional: habilitar 2FA

        @AssertTrue(message = "Senhas não coincidem")
        public boolean isSenhasCoincidentes() {
            return senha != null && senha.equals(confirmaSenha);
        }
    }

    /**
     * DTO para validar token
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidarTokenDTO {

        @NotBlank(message = "Token é obrigatório")
        private String token;
    }

    /**
     * DTO de resposta da validação do token
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenInfoDTO {
        private boolean valido;
        private String status;
        private String email;
        private String nome;
        private TipoUsuario tipoUsuario;
        private String departamento;
        private LocalDateTime expiraEm;
        private Long horasRestantes;
        private String message;
    }

    /**
     * DTO para listar convites (admin view)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConviteListDTO {
        private Long id;
        private String email;
        private String nome;
        private TipoUsuario tipoUsuario;
        private String departamento;
        private String status;
        private LocalDateTime criadoEm;
        private LocalDateTime expiraEm;
        private LocalDateTime aceitoEm;
        private String criadoPorNome;
        private Long horasRestantes;
        private Integer tentativasAcesso;
    }

    /**
     * DTO para reenviar convite
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReenviarConviteDTO {

        @NotNull(message = "ID do convite é obrigatório")
        private Long conviteId;

        @Min(value = 24, message = "Validade mínima é 24 horas")
        @Max(value = 168, message = "Validade máxima é 168 horas")
        private Integer novaValidadeHoras = 72;

        private String mensagemPersonalizada;
    }

    /**
     * DTO para cancelar convite
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CancelarConviteDTO {

        @NotNull(message = "ID do convite é obrigatório")
        private Long conviteId;

        private String motivo;
    }
}
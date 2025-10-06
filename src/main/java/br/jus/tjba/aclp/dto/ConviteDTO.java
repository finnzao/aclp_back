package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.enums.StatusConvite;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTOs relacionados ao sistema de convites
 */
public class ConviteDTO {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriarConviteRequest {
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        private String email;

        @NotNull(message = "Tipo de usuário é obrigatório")
        private TipoUsuario tipoUsuario;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConviteResponse {
        private Long id;
        private String token;
        private String email;
        private TipoUsuario tipoUsuario;
        private StatusConvite status;
        private String linkConvite;
        private String comarca;
        private String departamento;
        private LocalDateTime criadoEm;
        private LocalDateTime expiraEm;
        private String criadoPorNome;
        private Long criadoPorId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidarConviteResponse {
        private Boolean valido;
        private String email;
        private TipoUsuario tipoUsuario;
        private LocalDateTime expiraEm;
        private String mensagem;
        private String comarca;
        private String departamento;

        // Método auxiliar para compatibilidade
        public boolean isValido() {
            return Boolean.TRUE.equals(valido);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtivarConviteRequest {
        @NotBlank(message = "Token é obrigatório")
        private String token;

        @NotBlank(message = "Nome é obrigatório")
        private String nome;

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        private String senha;

        @NotBlank(message = "Confirmação de senha é obrigatória")
        private String confirmaSenha;

        private String telefone;
        private String cargo;

        public boolean senhasCoincidentes() {
            return senha != null && senha.equals(confirmaSenha);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtivarConviteResponse {
        private Boolean success;
        private String message;
        private UsuarioInfoDTO usuario;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsuarioInfoDTO {
        private Long id;
        private String nome;
        private String email;
        private TipoUsuario tipo;
        private String comarca;
        private String departamento;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConviteListItem {
        private Long id;
        private String email;
        private TipoUsuario tipoUsuario;
        private StatusConvite status;
        private String comarca;
        private String departamento;
        private LocalDateTime criadoEm;
        private LocalDateTime expiraEm;
        private LocalDateTime ativadoEm;
        private Boolean expirado;
        private String criadoPorNome;
        private String usuarioCriadoNome;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConviteStats {
        private Long totalConvites;
        private Long pendentes;
        private Long ativados;
        private Long expirados;
        private Long cancelados;
    }
}
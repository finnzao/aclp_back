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
 * Apenas convites específicos com email obrigatório
 */
public class ConviteDTO {

    /**
     * DTO para validar convite
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidarConviteResponse {
        private Boolean valido;

        @NotBlank
        private String email;  // Email sempre presente

        private TipoUsuario tipoUsuario;
        private String comarca;
        private String departamento;
        private LocalDateTime expiraEm;
        private String mensagem;

        public boolean isValido() {
            return Boolean.TRUE.equals(valido);
        }
    }

    /**
     * DTO para criar convite específico (com email)
     */
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

    /**
     * DTO para resposta de convite
     */
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

    /**
     * DTO para ativar convite
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtivarConviteRequest {
        @NotBlank(message = "Token é obrigatório")
        private String token;

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
        private String nome;

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$",
                message = "Senha deve conter: maiúscula, minúscula, número e caractere especial")
        private String senha;

        @NotBlank(message = "Confirmação de senha é obrigatória")
        private String confirmaSenha;

        @Size(max = 100, message = "Cargo deve ter no máximo 100 caracteres")
        private String cargo;

        public boolean senhasCoincidentes() {
            return senha != null && senha.equals(confirmaSenha);
        }
    }

    /**
     * DTO para resposta de ativação
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtivarConviteResponse {
        private Boolean success;
        private String message;
        private UsuarioInfoDTO usuario;
    }

    /**
     * DTO com informações do usuário criado
     */
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
        private String cargo;
    }

    /**
     * DTO para listagem de convites
     */
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
        private Boolean usado;
    }

    /**
     * DTO para estatísticas de convites
     */
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

    /**
     * DTO para pré-cadastro
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreCadastroRequest {
        @NotBlank(message = "Token é obrigatório")
        private String token;

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        private String email;

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
        private String nome;

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$",
                message = "Senha deve conter: maiúscula, minúscula, número e caractere especial")
        private String senha;

        @NotBlank(message = "Confirmação de senha é obrigatória")
        private String confirmaSenha;

        @Size(max = 100, message = "Cargo deve ter no máximo 100 caracteres")
        private String cargo;

        public boolean senhasCoincidentes() {
            return senha != null && senha.equals(confirmaSenha);
        }
    }

    /**
     * DTO para resposta de pré-cadastro
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreCadastroResponse {
        private Boolean success;
        private String message;
        private String email;
        private LocalDateTime expiracaoVerificacao;
    }

    /**
     * DTO para verificação de email
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificarEmailRequest {
        @NotBlank(message = "Token de verificação é obrigatório")
        private String token;
    }

    /**
     * DTO para resposta de verificação de email
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificarEmailResponse {
        private Boolean success;
        private String message;
        private UsuarioInfoDTO usuario;

        @Builder.Default
        private String loginUrl = "/login";
    }

    /**
     * DTO para gerar link de convite (removido - não há mais convites genéricos)
     */
    @Deprecated
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GerarLinkConviteRequest {
        @NotNull(message = "Tipo de usuário é obrigatório")
        private TipoUsuario tipoUsuario;

        @Builder.Default
        private Integer diasValidade = 30;
    }

    /**
     * DTO para resposta de link de convite (removido - não há mais convites genéricos)
     */
    @Deprecated
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkConviteResponse {
        private Long id;
        private String token;
        private String link;
        private TipoUsuario tipoUsuario;
        private String comarca;
        private String departamento;
        private LocalDateTime expiraEm;
        private String criadoPorNome;
        private Boolean usado;
    }
}
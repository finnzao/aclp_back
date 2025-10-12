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

    /**
     * DTO para gerar link de convite (sem email específico)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GerarLinkConviteRequest {
        @NotNull(message = "Tipo de usuário é obrigatório")
        private TipoUsuario tipoUsuario;

        private Integer quantidadeUsos = 1; // Quantas vezes o link pode ser usado

        private Integer diasValidade = 30; // Dias de validade do link
    }

    /**
     * DTO para resposta de geração de link
     */
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
        private Integer usosRestantes;
        private LocalDateTime expiraEm;
        private String criadoPorNome;
    }

    /**
     * DTO para validar convite
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidarConviteResponse {
        private Boolean valido;
        private TipoUsuario tipoUsuario;
        private String comarca;        // Pré-preenchido do admin
        private String departamento;   // Pré-preenchido do admin
        private LocalDateTime expiraEm;
        private String mensagem;
        private String[] camposEditaveis = {"email", "nome", "cargo", "senha"};

        public boolean isValido() {
            return Boolean.TRUE.equals(valido);
        }
    }

    /**
     * DTO para pré-cadastro (primeira etapa)
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
        private String nome;

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$",
                message = "Senha deve conter: maiúscula, minúscula, número e símbolo")
        private String senha;

        @NotBlank(message = "Confirmação de senha é obrigatória")
        private String confirmaSenha;

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
     * DTO para verificação de email (segunda etapa)
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
        private String loginUrl = "/login";
    }

    /**
     * DTOs existentes mantidos para compatibilidade
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
    public static class AtivarConviteRequest {
        @NotBlank(message = "Token é obrigatório")
        private String token;

        @NotBlank(message = "Nome é obrigatório")
        private String nome;

        @Email(message = "Email inválido")
        private String email;

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        private String senha;

        @NotBlank(message = "Confirmação de senha é obrigatória")
        private String confirmaSenha;

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
        private String cargo;
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
        private Integer usosRestantes;
        private Integer totalUsos;
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
        private Long aguardandoVerificacao;
    }
}
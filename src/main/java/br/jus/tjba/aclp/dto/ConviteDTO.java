package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.model.enums.StatusConvite;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTOs para operações com convites
 */
public class ConviteDTO {

    /**
     * Request para criar convite (usado pelo Admin)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriarConviteRequest {

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ser válido")
        private String email;

        @NotNull(message = "Tipo de usuário é obrigatório")
        private TipoUsuario tipoUsuario;
    }

    /**
     * Response ao criar convite
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConviteResponse {
        private Long id;
        private String token;
        private String email;
        private TipoUsuario tipoUsuario;
        private StatusConvite status;
        private String linkConvite;
        private LocalDateTime criadoEm;
        private LocalDateTime expiraEm;
        private String criadoPorNome;
        private Long criadoPorId;
    }

    /**
     * Response da validação de convite (público)
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValidarConviteResponse {
        private boolean valido;
        private String email;
        private TipoUsuario tipoUsuario;
        private String mensagem;
        private LocalDateTime expiraEm;
    }

    /**
     * Request para ativar convite (criar usuário - público)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtivarConviteRequest {

        @NotBlank(message = "Token é obrigatório")
        private String token;

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
        private String nome;

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter pelo menos 8 caracteres")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$",
                message = "Senha deve conter: maiúscula, minúscula, número e símbolo")
        private String senha;

        @NotBlank(message = "Confirmação de senha é obrigatória")
        private String confirmaSenha;

        @Size(max = 100, message = "Departamento deve ter no máximo 100 caracteres")
        private String departamento;

        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
        private String telefone;

        public boolean senhasCoincidentes() {
            return senha != null && senha.equals(confirmaSenha);
        }
    }

    /**
     * Response ao ativar convite
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AtivarConviteResponse {
        private boolean success;
        private String message;
        private UsuarioInfoDTO usuario;
    }

    /**
     * Info básica do usuário criado
     */
    @Data
    @Builder
    public static class UsuarioInfoDTO {
        private Long id;
        private String nome;
        private String email;
        private TipoUsuario tipo;
    }

    /**
     * Item da lista de convites
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConviteListItem {
        private Long id;
        private String email;
        private TipoUsuario tipoUsuario;
        private StatusConvite status;
        private LocalDateTime criadoEm;
        private LocalDateTime expiraEm;
        private LocalDateTime ativadoEm;
        private boolean expirado;
        private String criadoPorNome;
        private String usuarioCriadoNome;
    }

    /**
     * Estatísticas de convites
     */
    @Data
    @Builder
    public static class ConviteStats {
        private long totalConvites;
        private long pendentes;
        private long ativados;
        private long expirados;
        private long cancelados;
    }
}
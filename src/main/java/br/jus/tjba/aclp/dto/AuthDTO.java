package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * DTOs para operações de autenticação e autorização
 */
public class AuthDTO {

    /**
     * DTO para requisição de login
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoginRequestDTO {

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ser válido")
        private String email;

        @NotBlank(message = "Senha é obrigatória")
        private String senha;

        private String mfaCode; // Código MFA opcional

        private boolean rememberMe = false; // Lembrar login

        private boolean forceLogin = false; // Forçar login mesmo com sessões ativas

        private Map<String, String> deviceInfo; // Informações do dispositivo
    }

    /**
     * DTO para resposta de login
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoginResponseDTO {

        private boolean success;
        private String message;
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Long expiresIn; // Segundos até expirar
        private String sessionId;
        private UsuarioDTO usuario;
        private boolean requiresMfa;
        private boolean requiresPasswordChange;
        private List<String> permissions;
        private LocalDateTime loginTime;
    }

    /**
     * DTO para informações do usuário autenticado
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UsuarioDTO {

        private Long id;
        private String nome;
        private String email;
        private TipoUsuario tipo;
        private String departamento;
        private String comarca;
        private String cargo;
        private String avatar;
        private LocalDateTime ultimoLogin;
        private boolean mfaEnabled;
        private List<String> roles;
        private Map<String, Object> preferences;
    }

    /**
     * DTO para requisição de refresh token
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RefreshTokenRequestDTO {

        @NotBlank(message = "Refresh token é obrigatório")
        private String refreshToken;
    }

    /**
     * DTO para resposta de refresh token
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RefreshTokenResponseDTO {

        private boolean success;
        private String accessToken;
        private String refreshToken; // Novo refresh token se rotacionado
        private String tokenType;
        private Long expiresIn;
    }

    /**
     * DTO para validação de token
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenValidationResponseDTO {

        private boolean valid;
        private String email;
        private Date expiration;
        private List<String> authorities;
        private String message;
        private Map<String, Object> claims;
    }

    /**
     * DTO para requisição de reset de senha
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PasswordResetRequestDTO {

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ser válido")
        private String email;

        private String recaptchaToken; // Token reCAPTCHA opcional
    }

    /**
     * DTO para confirmação de reset de senha
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PasswordResetConfirmDTO {

        @NotBlank(message = "Token é obrigatório")
        private String token;

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter pelo menos 8 caracteres")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$",
                message = "Senha deve conter: maiúscula, minúscula, número e símbolo")
        private String novaSenha;

        @NotBlank(message = "Confirmação de senha é obrigatória")
        private String confirmaSenha;

        public boolean senhasCoincidentes() {
            return novaSenha != null && novaSenha.equals(confirmaSenha);
        }
    }

    /**
     * DTO para alteração de senha
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChangePasswordDTO {

        @NotBlank(message = "Senha atual é obrigatória")
        private String senhaAtual;

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter pelo menos 8 caracteres")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$",
                message = "Senha deve conter: maiúscula, minúscula, número e símbolo")
        private String novaSenha;

        @NotBlank(message = "Confirmação de senha é obrigatória")
        private String confirmaSenha;

        public boolean senhasCoincidentes() {
            return novaSenha != null && novaSenha.equals(confirmaSenha);
        }
    }

    /**
     * DTO para logout
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LogoutRequestDTO {

        private String refreshToken; // Opcional para invalidar refresh token
        private boolean logoutAllDevices = false; // Logout de todos os dispositivos
    }

    /**
     * DTO para informações de sessão
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SessionInfoDTO {

        private String sessionId;
        private String userEmail;
        private String ipAddress;
        private String userAgent;
        private LocalDateTime loginTime;
        private LocalDateTime lastActivity;
        private LocalDateTime expiresAt;
        private boolean current;
        private String device;
        private String location;
    }

    /**
     * DTO para configuração MFA
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MfaSetupDTO {

        private boolean enable;
        private String secret; // Para QR Code
        private String qrCodeUrl;
        private List<String> backupCodes;
    }

    /**
     * DTO para verificação MFA
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MfaVerificationDTO {

        @NotBlank(message = "Código é obrigatório")
        @Pattern(regexp = "\\d{6}", message = "Código deve ter 6 dígitos")
        private String code;

        private boolean trustDevice = false;
    }

    /**
     * DTO para resposta de erro de autenticação
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AuthErrorDTO {

        private String error;
        private String errorDescription;
        private String errorCode;
        private LocalDateTime timestamp;
        private String path;
        private Integer remainingAttempts;
        private LocalDateTime lockedUntil;
    }

    /**
     * DTO para auditoria de login
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoginAuditDTO {

        private Long id;
        private String email;
        private String ipAddress;
        private String userAgent;
        private boolean success;
        private String failureReason;
        private LocalDateTime attemptTime;
        private String location;
        private String device;
    }

    /**
     * DTO para política de senha
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PasswordPolicyDTO {

        private int minLength;
        private boolean requireUppercase;
        private boolean requireLowercase;
        private boolean requireNumbers;
        private boolean requireSpecialChars;
        private int expirationDays;
        private int historyCount; // Não reutilizar últimas N senhas
        private boolean requireChangeOnFirstLogin;
    }

    /**
     * DTO para verificação de força da senha
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PasswordStrengthDTO {

        private String password;
        private int score; // 0-100
        private String strength; // WEAK, FAIR, GOOD, STRONG, VERY_STRONG
        private List<String> suggestions;
        private boolean meetsPolicy;
        private Map<String, Boolean> requirements;
    }
}
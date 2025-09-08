package br.jus.tjba.aclp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTOs para o sistema de verificação por email
 */
public class EmailVerificationDTO {

    /**
     * DTO para solicitar código de verificação
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SolicitarCodigoDTO {

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ser válido")
        @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
        private String email;

        @NotBlank(message = "Tipo de usuário é obrigatório")
        @Pattern(regexp = "ADMIN|USUARIO", message = "Tipo deve ser ADMIN ou USUARIO")
        private String tipoUsuario;

        /**
         * Limpa e formata o email
         */
        public void limparEFormatarDados() {
            if (email != null) {
                email = email.trim().toLowerCase();
            }
            if (tipoUsuario != null) {
                tipoUsuario = tipoUsuario.trim().toUpperCase();
            }
        }

        /**
         * Valida se email é institucional para ADMIN
         */
        public boolean isEmailValidoParaTipo() {
            if ("ADMIN".equals(tipoUsuario)) {
                return email != null && email.endsWith("@tjba.jus.br");
            }
            // Para usuário comum, qualquer email é válido
            return email != null && email.contains("@");
        }
    }

    /**
     * DTO para verificar código
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VerificarCodigoDTO {

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ser válido")
        private String email;

        @NotBlank(message = "Código é obrigatório")
        @Pattern(regexp = "\\d{6}", message = "Código deve ter 6 dígitos")
        private String codigo;

        /**
         * Limpa e formata os dados
         */
        public void limparEFormatarDados() {
            if (email != null) {
                email = email.trim().toLowerCase();
            }
            if (codigo != null) {
                codigo = codigo.trim();
            }
        }
    }

    /**
     * DTO de resposta para solicitação de código
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SolicitarCodigoResponseDTO {
        private String status;
        private String message;
        private String email;
        private Integer validadePorMinutos;
        private Integer tentativasPermitidas;
        private String proximoEnvioEm;
        private String codigoId; // Hash do ID para rastreamento sem expor dados
    }

    /**
     * DTO de resposta para verificação de código
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VerificarCodigoResponseDTO {
        private String status;
        private String message;
        private String email;
        private Boolean verificado;
        private String tokenVerificacao; // Token temporário para criar usuário
        private Integer tentativasRestantes;
        private String validoAte;
    }

    /**
     * DTO para status de verificação
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatusVerificacaoDTO {
        private String email;
        private Boolean possuiCodigoAtivo;
        private Boolean verificado;
        private Integer tentativasRestantes;
        private Integer minutosRestantes;
        private String ultimaVerificacao;
        private Boolean podeReenviar;
    }

    /**
     * DTO para reenvio de código
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReenviarCodigoDTO {

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ser válido")
        private String email;

        /**
         * Limpa e formata o email
         */
        public void limparEFormatarDados() {
            if (email != null) {
                email = email.trim().toLowerCase();
            }
        }
    }
}
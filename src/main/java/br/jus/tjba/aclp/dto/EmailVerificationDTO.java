package br.jus.tjba.aclp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class EmailVerificationDTO {

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

        public void limparEFormatarDados() {
            if (email != null) {
                email = email.trim().toLowerCase();
            }
            if (tipoUsuario != null) {
                tipoUsuario = tipoUsuario.trim().toUpperCase();
            }
        }

        public boolean isEmailValidoParaTipo() {
            if ("ADMIN".equals(tipoUsuario)) {
                return email != null && email.endsWith("@tjba.jus.br");
            }
            // Para usuário comum, qualquer email é válido
            return email != null && email.contains("@");
        }
    }

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

        public void limparEFormatarDados() {
            if (email != null) {
                email = email.trim().toLowerCase();
            }
            if (codigo != null) {
                codigo = codigo.trim();
            }
        }
    }

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReenviarCodigoDTO {

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email deve ser válido")
        private String email;

        public void limparEFormatarDados() {
            if (email != null) {
                email = email.trim().toLowerCase();
            }
        }
    }
}
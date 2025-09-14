package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.enums.TipoValidacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComparecimentoDTO {

    private Long custodiadoId;

    public void setCustodiadoId(Long custodiadoId) {
        this.custodiadoId = custodiadoId;
    }

    @NotNull(message = "Data do comparecimento é obrigatória")
    private LocalDate dataComparecimento;

    private LocalTime horaComparecimento;

    @NotNull(message = "Tipo de validação é obrigatório")
    private TipoValidacao tipoValidacao;

    @Size(max = 500, message = "Observações deve ter no máximo 500 caracteres")
    private String observacoes;

    @NotBlank(message = "Validado por é obrigatório")
    @Size(max = 100, message = "Validado por deve ter no máximo 100 caracteres")
    private String validadoPor;

    @Size(max = 1000, message = "Anexos deve ter no máximo 1000 caracteres")
    private String anexos;

    // === CONTROLE DE MUDANÇA DE ENDEREÇO ===

    @Builder.Default
    private Boolean mudancaEndereco = Boolean.FALSE;

    @Size(max = 500, message = "Motivo da mudança deve ter no máximo 500 caracteres")
    private String motivoMudancaEndereco;

    // === DADOS DO NOVO ENDEREÇO (apenas se mudancaEndereco = true) ===

    @Valid
    private EnderecoDTO novoEndereco;

    // === MÉTODOS UTILITÁRIOS ===

    public boolean houveMudancaEndereco() {
        return Boolean.TRUE.equals(mudancaEndereco);
    }

    public boolean isComparecimentoValido() {
        return custodiadoId != null &&
                dataComparecimento != null &&
                tipoValidacao != null &&
                validadoPor != null && !validadoPor.trim().isEmpty();
    }

    public boolean isMudancaEnderecoValida() {
        if (!houveMudancaEndereco()) {
            return true; // Se não houve mudança, não precisa validar
        }

        return novoEndereco != null && novoEndereco.isCompleto();
    }

    public void limparDadosEndereco() {
        if (!houveMudancaEndereco()) {
            this.novoEndereco = null;
            this.motivoMudancaEndereco = null;
        }
    }

    public void limparEFormatarDados() {
        if (observacoes != null) {
            observacoes = observacoes.trim();
        }
        if (validadoPor != null) {
            validadoPor = validadoPor.trim();
        }
        if (motivoMudancaEndereco != null) {
            motivoMudancaEndereco = motivoMudancaEndereco.trim();
        }
        if (anexos != null) {
            anexos = anexos.trim();
        }

        // Limpar dados de endereço se não houve mudança
        limparDadosEndereco();

        // Limpar e formatar endereço se houver
        if (novoEndereco != null) {
            novoEndereco.limparEFormatarDados();
        }
    }

    /**
     * DTO interno para dados do novo endereço
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnderecoDTO {

        @NotBlank(message = "CEP é obrigatório")
        @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP deve ter o formato 00000-000")
        private String cep;

        @NotBlank(message = "Logradouro é obrigatório")
        @Size(min = 5, max = 200, message = "Logradouro deve ter entre 5 e 200 caracteres")
        private String logradouro;

        @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
        private String numero;

        @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres")
        private String complemento;

        @NotBlank(message = "Bairro é obrigatório")
        @Size(min = 2, max = 100, message = "Bairro deve ter entre 2 e 100 caracteres")
        private String bairro;

        @NotBlank(message = "Cidade é obrigatória")
        @Size(min = 2, max = 100, message = "Cidade deve ter entre 2 e 100 caracteres")
        private String cidade;

        @NotBlank(message = "Estado é obrigatório")
        @Size(min = 2, max = 2, message = "Estado deve ter exatamente 2 caracteres")
        @Pattern(regexp = "[A-Z]{2}", message = "Estado deve ser uma sigla válida com 2 letras maiúsculas")
        private String estado;

        public boolean isCompleto() {
            return cep != null && !cep.trim().isEmpty() &&
                    logradouro != null && !logradouro.trim().isEmpty() &&
                    bairro != null && !bairro.trim().isEmpty() &&
                    cidade != null && !cidade.trim().isEmpty() &&
                    estado != null && !estado.trim().isEmpty();
        }

        public void limparEFormatarDados() {
            if (cep != null) {
                cep = cep.trim();
            }
            if (logradouro != null) {
                logradouro = logradouro.trim();
            }
            if (numero != null) {
                numero = numero.trim();
            }
            if (complemento != null) {
                complemento = complemento.trim();
            }
            if (bairro != null) {
                bairro = bairro.trim();
            }
            if (cidade != null) {
                cidade = cidade.trim();
            }
            if (estado != null) {
                estado = estado.trim().toUpperCase();
            }
        }
    }
}
package br.jus.tjba.aclp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricoEnderecoDTO {

    private Long id;

    @NotNull(message = "ID da pessoa é obrigatório")
    private Long pessoaId;

    // === DADOS DO ENDEREÇO ===

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

    // === CONTROLE TEMPORAL ===

    @NotNull(message = "Data de início é obrigatória")
    private LocalDate dataInicio;

    private LocalDate dataFim;

    @Size(max = 500, message = "Motivo da alteração deve ter no máximo 500 caracteres")
    private String motivoAlteracao;

    @Size(max = 100, message = "Validado por deve ter no máximo 100 caracteres")
    private String validadoPor;

    // === REFERÊNCIAS ===

    private Long historicoComparecimentoId;

    // === DADOS DE RESPOSTA (não enviados na requisição) ===

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private Long version;

    // Dados calculados
    private String enderecoCompleto;
    private String enderecoResumido;
    private String nomeEstado;
    private String regiaoEstado;
    private String periodoResidencia;
    private Long diasResidencia;
    private Boolean enderecoAtivo;

    // === MÉTODOS UTILITÁRIOS ===

    public boolean isCompleto() {
        return cep != null && !cep.trim().isEmpty() &&
                logradouro != null && !logradouro.trim().isEmpty() &&
                bairro != null && !bairro.trim().isEmpty() &&
                cidade != null && !cidade.trim().isEmpty() &&
                estado != null && !estado.trim().isEmpty();
    }

    public boolean isEnderecoAtivo() {
        return dataFim == null;
    }

    public boolean isEnderecoHistorico() {
        return dataFim != null;
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
        if (motivoAlteracao != null) {
            motivoAlteracao = motivoAlteracao.trim();
        }
        if (validadoPor != null) {
            validadoPor = validadoPor.trim();
        }
    }

    /**
     * DTO simplificado para listagens
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Resumo {
        private Long id;
        private String enderecoResumido;
        private LocalDate dataInicio;
        private LocalDate dataFim;
        private String periodoResidencia;
        private Boolean enderecoAtivo;
        private String motivoAlteracao;
    }

    /**
     * DTO para estatísticas
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Estatistica {
        private String cidade;
        private String estado;
        private String regiao;
        private Long totalCustodidos;
        private Long totalEnderecos;
        private Double mediaDiasResidencia;
    }
}
package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.enums.TipoValidacao;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
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

    // NOVO: referência principal ao processo
    @Schema(description = "ID do processo (preferencial)", example = "1")
    private Long processoId;

    // DEPRECATED: mantido para compatibilidade durante transição
    @Schema(description = "ID do custodiado (deprecated - use processoId)", example = "1", deprecated = true)
    private Long custodiadoId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Data do comparecimento é obrigatória")
    @Schema(description = "Data do comparecimento", example = "2025-10-27", required = true)
    private LocalDate dataComparecimento;

    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(description = "Hora do comparecimento no formato HH:mm:ss", example = "14:30:00")
    private LocalTime horaComparecimento;

    @NotNull(message = "Tipo de validação é obrigatório")
    @Schema(description = "Tipo de validação", example = "presencial",
            allowableValues = {"presencial", "online", "cadastro_inicial"})
    private TipoValidacao tipoValidacao;

    @Size(max = 500, message = "Observações deve ter no máximo 500 caracteres")
    @Schema(description = "Observações sobre o comparecimento")
    private String observacoes;

    @NotBlank(message = "Validado por é obrigatório")
    @Size(max = 100)
    @Schema(description = "Nome do responsável pela validação", example = "Maria Santos - Servidora TJBA")
    private String validadoPor;

    @Size(max = 1000)
    @Schema(description = "Lista de anexos")
    private String anexos;

    @Builder.Default
    @Schema(description = "Indica se houve mudança de endereço", example = "false")
    private Boolean mudancaEndereco = Boolean.FALSE;

    @Size(max = 500)
    @Schema(description = "Motivo da mudança de endereço")
    private String motivoMudancaEndereco;

    @Valid
    @Schema(description = "Dados do novo endereço (obrigatório se mudancaEndereco = true)")
    private EnderecoDTO novoEndereco;

    public boolean houveMudancaEndereco() {
        return Boolean.TRUE.equals(mudancaEndereco);
    }

    /**
     * Retorna o ID efetivo: processoId tem prioridade, fallback para custodiadoId
     */
    public Long getIdEfetivo() {
        return processoId != null ? processoId : custodiadoId;
    }

    public boolean isComparecimentoValido() {
        return (processoId != null || custodiadoId != null) &&
                dataComparecimento != null &&
                tipoValidacao != null &&
                validadoPor != null && !validadoPor.trim().isEmpty();
    }

    public boolean isMudancaEnderecoValida() {
        if (!houveMudancaEndereco()) return true;
        return novoEndereco != null && novoEndereco.isCompleto();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnderecoDTO {

        @NotBlank(message = "CEP é obrigatório")
        @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP deve ter o formato 00000-000")
        private String cep;

        @NotBlank(message = "Logradouro é obrigatório")
        @Size(min = 5, max = 200)
        private String logradouro;

        @Size(max = 20)
        private String numero;

        @Size(max = 100)
        private String complemento;

        @NotBlank(message = "Bairro é obrigatório")
        @Size(min = 2, max = 100)
        private String bairro;

        @NotBlank(message = "Cidade é obrigatória")
        @Size(min = 2, max = 100)
        private String cidade;

        @NotBlank(message = "Estado é obrigatório")
        @Size(min = 2, max = 2)
        @Pattern(regexp = "[A-Z]{2}", message = "Estado deve ser sigla com 2 letras maiúsculas")
        private String estado;

        public boolean isCompleto() {
            return cep != null && !cep.trim().isEmpty() &&
                    logradouro != null && !logradouro.trim().isEmpty() &&
                    bairro != null && !bairro.trim().isEmpty() &&
                    cidade != null && !cidade.trim().isEmpty() &&
                    estado != null && !estado.trim().isEmpty();
        }
    }
}

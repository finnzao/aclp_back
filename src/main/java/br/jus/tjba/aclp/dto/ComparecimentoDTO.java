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

    @Schema(description = "ID do custodiado", example = "1", required = true)
    private Long custodiadoId;

    @NotNull(message = "Data do comparecimento é obrigatória")
    @Schema(description = "Data do comparecimento", example = "2025-09-15", required = true)
    private LocalDate dataComparecimento;

    // Configurado para serializar/deserializar como string "HH:mm:ss"
    @JsonFormat(pattern = "HH:mm:ss")
    @Schema(description = "Hora do comparecimento no formato HH:mm:ss",
            example = "14:30:00",
            type = "string",
            pattern = "^([01]?[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$")
    private LocalTime horaComparecimento;

    @NotNull(message = "Tipo de validação é obrigatório")
    @Schema(description = "Tipo de validação do comparecimento",
            example = "presencial",
            allowableValues = {"presencial", "online", "cadastro_inicial"})
    private TipoValidacao tipoValidacao;

    @Size(max = 500, message = "Observações deve ter no máximo 500 caracteres")
    @Schema(description = "Observações sobre o comparecimento", example = "Comparecimento regular")
    private String observacoes;

    @NotBlank(message = "Validado por é obrigatório")
    @Size(max = 100, message = "Validado por deve ter no máximo 100 caracteres")
    @Schema(description = "Nome do responsável pela validação", example = "Maria Santos - Servidora TJBA")
    private String validadoPor;

    @Size(max = 1000, message = "Anexos deve ter no máximo 1000 caracteres")
    @Schema(description = "Lista de anexos ou documentos relacionados", example = "doc1.pdf, foto.jpg")
    private String anexos;

    // === CONTROLE DE MUDANÇA DE ENDEREÇO ===

    @Builder.Default
    @Schema(description = "Indica se houve mudança de endereço durante o comparecimento", example = "false")
    private Boolean mudancaEndereco = Boolean.FALSE;

    @Size(max = 500, message = "Motivo da mudança deve ter no máximo 500 caracteres")
    @Schema(description = "Motivo da mudança de endereço", example = "Mudança por questões familiares")
    private String motivoMudancaEndereco;

    // === DADOS DO NOVO ENDEREÇO (apenas se mudancaEndereco = true) ===

    @Valid
    @Schema(description = "Dados do novo endereço (obrigatório se mudancaEndereco = true)")
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
            return true;
        }
        return novoEndereco != null && novoEndereco.isCompleto();
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
        @Schema(description = "CEP do endereço", example = "40070-110")
        private String cep;

        @NotBlank(message = "Logradouro é obrigatório")
        @Size(min = 5, max = 200, message = "Logradouro deve ter entre 5 e 200 caracteres")
        @Schema(description = "Nome da rua/avenida", example = "Avenida Sete de Setembro")
        private String logradouro;

        @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
        @Schema(description = "Número do imóvel", example = "1234")
        private String numero;

        @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres")
        @Schema(description = "Complemento do endereço", example = "Apto 501")
        private String complemento;

        @NotBlank(message = "Bairro é obrigatório")
        @Size(min = 2, max = 100, message = "Bairro deve ter entre 2 e 100 caracteres")
        @Schema(description = "Nome do bairro", example = "Centro")
        private String bairro;

        @NotBlank(message = "Cidade é obrigatória")
        @Size(min = 2, max = 100, message = "Cidade deve ter entre 2 e 100 caracteres")
        @Schema(description = "Nome da cidade", example = "Salvador")
        private String cidade;

        @NotBlank(message = "Estado é obrigatório")
        @Size(min = 2, max = 2, message = "Estado deve ter exatamente 2 caracteres")
        @Pattern(regexp = "[A-Z]{2}", message = "Estado deve ser uma sigla válida com 2 letras maiúsculas")
        @Schema(description = "Sigla do estado", example = "BA")
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
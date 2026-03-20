package br.jus.tjba.aclp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessoDTO {

    @NotNull(message = "ID do custodiado é obrigatório")
    private Long custodiadoId;

    @NotBlank(message = "Número do processo é obrigatório")
    @Pattern(regexp = "\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}",
            message = "Processo deve ter o formato CNJ: 0000000-00.0000.0.00.0000")
    private String numeroProcesso;

    @NotBlank(message = "Vara é obrigatória")
    @Size(max = 100, message = "Vara deve ter no máximo 100 caracteres")
    private String vara;

    @NotBlank(message = "Comarca é obrigatória")
    @Size(max = 100, message = "Comarca deve ter no máximo 100 caracteres")
    private String comarca;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Data da decisão é obrigatória")
    @PastOrPresent(message = "Data da decisão não pode ser futura")
    private LocalDate dataDecisao;

    @NotNull(message = "Periodicidade é obrigatória")
    @Min(value = 1, message = "Periodicidade mínima é 1 dia")
    @Max(value = 365, message = "Periodicidade máxima é 365 dias")
    private Integer periodicidade;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Data do comparecimento inicial é obrigatória")
    private LocalDate dataComparecimentoInicial;

    @Size(max = 500, message = "Observações deve ter no máximo 500 caracteres")
    private String observacoes;
}

package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.IncidenteExecucao;
import br.jus.tjba.aclp.model.enums.ResultadoIncidente;
import br.jus.tjba.aclp.model.enums.TipoIncidente;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class IncidenteExecucaoDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegistroIncidenteRequest {

        @NotNull(message = "Tipo do incidente é obrigatório")
        private TipoIncidente tipo;

        @NotNull(message = "Data da ocorrência é obrigatória")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataOcorrencia;

        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 1000)
        private String descricao;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataAudiencia;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResultadoRequest {

        @NotNull(message = "Resultado é obrigatório")
        private ResultadoIncidente resultado;

        @NotNull(message = "Data da decisão é obrigatória")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataDecisao;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataAudiencia;
    }

    @Data
    @Builder
    public static class IncidenteResponse {
        private Long id;
        private TipoIncidente tipo;
        private String tipoDescricao;
        private LocalDate dataOcorrencia;
        private String descricao;
        private LocalDate dataAudiencia;
        private ResultadoIncidente resultado;
        private String resultadoDescricao;
        private LocalDate dataDecisao;
        private String registradoPor;
        private String decididoPor;

        public static IncidenteResponse fromEntity(IncidenteExecucao i) {
            return IncidenteResponse.builder()
                    .id(i.getId())
                    .tipo(i.getTipo())
                    .tipoDescricao(i.getTipo().getDescricao())
                    .dataOcorrencia(i.getDataOcorrencia())
                    .descricao(i.getDescricao())
                    .dataAudiencia(i.getDataAudiencia())
                    .resultado(i.getResultado())
                    .resultadoDescricao(i.getResultado().getDescricao())
                    .dataDecisao(i.getDataDecisao())
                    .registradoPor(i.getRegistradoPor())
                    .decididoPor(i.getDecididoPor())
                    .build();
        }
    }
}

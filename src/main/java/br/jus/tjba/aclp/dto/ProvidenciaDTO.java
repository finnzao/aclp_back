package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.Providencia;
import br.jus.tjba.aclp.model.enums.TipoProvidencia;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class ProvidenciaDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegistroProvidenciaRequest {

        @NotNull(message = "Tipo da providência é obrigatório")
        private TipoProvidencia tipo;

        @Size(max = 500)
        private String descricao;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate prazo;

        private boolean prioritaria;
    }

    @Data
    @Builder
    public static class ProvidenciaResponse {
        private Long id;
        private Long execucaoId;
        private String numeroExecucao;
        private String custodiadoNome;
        private TipoProvidencia tipo;
        private String tipoDescricao;
        private String descricao;
        private LocalDate prazo;
        private boolean prioritaria;
        private boolean vencida;
        private LocalDate concluidaEm;
        private String registradoPor;
        private String concluidaPor;

        public static ProvidenciaResponse fromEntity(Providencia p) {
            var e = p.getExecucaoPenal();
            return ProvidenciaResponse.builder()
                    .id(p.getId())
                    .execucaoId(e.getId())
                    .numeroExecucao(e.getNumeroExecucao())
                    .custodiadoNome(e.getProcesso().getCustodiado() != null
                            ? e.getProcesso().getCustodiado().getNome() : null)
                    .tipo(p.getTipo())
                    .tipoDescricao(p.getTipo().getDescricao())
                    .descricao(p.getDescricao())
                    .prazo(p.getPrazo())
                    .prioritaria(p.isPrioritaria())
                    .vencida(p.isVencida())
                    .concluidaEm(p.getConcluidaEm())
                    .registradoPor(p.getRegistradoPor())
                    .concluidaPor(p.getConcluidaPor())
                    .build();
        }
    }
}

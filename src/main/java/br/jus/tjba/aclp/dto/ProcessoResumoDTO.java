package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * CORREÇÃO DE PERFORMANCE: DTO resumido de processo.
 *
 * Usado na resposta do endpoint de busca em lote (POST /processos/batch),
 * contém apenas os campos necessários para evitar serialização pesada.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessoResumoDTO {

    private Long id;
    private Long custodiadoId;
    private String numeroProcesso;
    private String vara;
    private String comarca;
    private StatusComparecimento status;
    private SituacaoProcesso situacaoProcesso;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate proximoComparecimento;

    private Integer periodicidade;

    /**
     * Converte entidade Processo para DTO resumido.
     * Deve ser chamado dentro de @Transactional (sessão Hibernate ativa).
     */
    public static ProcessoResumoDTO fromEntity(Processo p) {
        if (p == null) return null;

        Long custId = null;
        if (p.getCustodiado() != null) {
            custId = p.getCustodiado().getId();
        }

        return ProcessoResumoDTO.builder()
                .id(p.getId())
                .custodiadoId(custId)
                .numeroProcesso(p.getNumeroProcesso())
                .vara(p.getVara())
                .comarca(p.getComarca())
                .status(p.getStatus())
                .situacaoProcesso(p.getSituacaoProcesso())
                .proximoComparecimento(p.getProximoComparecimento())
                .periodicidade(p.getPeriodicidade())
                .build();
    }
}

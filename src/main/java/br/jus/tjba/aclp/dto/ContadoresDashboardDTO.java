package br.jus.tjba.aclp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO para os contadores do dashboard.
 * Extraído de ProcessoService.ContadoresDashboard para uso independente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContadoresDashboardDTO {

    private long totalProcessosAtivos;
    private long emConformidade;
    private long inadimplentes;
    private long comparecimentosHoje;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataConsulta;
}

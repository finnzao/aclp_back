package br.jus.tjba.aclp.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CORREÇÃO DE PERFORMANCE: DTO para busca em lote de processos.
 *
 * Permite buscar processos de múltiplos custodiados em uma única
 * requisição HTTP, substituindo o padrão N+1 de requisições individuais.
 *
 * Limitado a 200 IDs por requisição para evitar abuso e queries muito grandes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchProcessoRequest {

    @NotNull(message = "Lista de IDs de custodiados é obrigatória")
    @Size(min = 1, max = 200, message = "Informe entre 1 e 200 IDs de custodiados")
    private List<Long> custodiadoIds;
}

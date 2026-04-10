package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.BatchProcessoRequest;
import br.jus.tjba.aclp.dto.ContadoresDashboardDTO;
import br.jus.tjba.aclp.dto.ProcessoDTO;
import br.jus.tjba.aclp.dto.ProcessoResumoDTO;
import br.jus.tjba.aclp.dto.ProcessoResponseDTO;
import br.jus.tjba.aclp.service.ProcessoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/processos")
@RequiredArgsConstructor
@Tag(name = "Processos", description = "API para gerenciamento de processos judiciais")
@Slf4j
public class ProcessoController {

    private final ProcessoService processoService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) br.jus.tjba.aclp.model.enums.StatusComparecimento status) {

        Page<ProcessoResponseDTO> resultado = processoService.listarComFiltros(termo, status, page, size);
        Map<String, Object> response = new HashMap<>();
        response.put("processos", resultado.getContent());
        response.put("paginaAtual", resultado.getNumber());
        response.put("totalPaginas", resultado.getTotalPages());
        response.put("totalItens", resultado.getTotalElements());
        response.put("itensPorPagina", resultado.getSize());
        return ResponseEntity.ok(ApiResponse.success("Processos listados", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcessoResponseDTO>> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Processo encontrado", processoService.buscarPorId(id)));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/custodiado/{custodiadoId}")
    public ResponseEntity<ApiResponse<List<ProcessoResponseDTO>>> buscarPorCustodiado(@PathVariable Long custodiadoId) {
        return ResponseEntity.ok(ApiResponse.success("Processos do custodiado", processoService.buscarPorCustodiado(custodiadoId)));
    }

    @GetMapping("/numero/{numero}")
    public ResponseEntity<ApiResponse<ProcessoResponseDTO>> buscarPorNumero(@PathVariable String numero) {
        return processoService.buscarPorNumeroProcesso(numero)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("Processo encontrado", dto)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Processo não encontrado: " + numero)));
    }

    @GetMapping("/inadimplentes")
    public ResponseEntity<ApiResponse<List<ProcessoResponseDTO>>> inadimplentes() {
        return ResponseEntity.ok(ApiResponse.success("Inadimplentes", processoService.buscarInadimplentes()));
    }

    @GetMapping("/hoje")
    public ResponseEntity<ApiResponse<List<ProcessoResponseDTO>>> hoje() {
        return ResponseEntity.ok(ApiResponse.success("Comparecimentos de hoje", processoService.buscarComparecimentosHoje()));
    }

    @GetMapping("/contadores")
    public ResponseEntity<ApiResponse<ContadoresDashboardDTO>> contadores() {
        return ResponseEntity.ok(ApiResponse.success("Contadores", processoService.contadoresParaDashboard()));
    }

    /**
     * CORREÇÃO DE PERFORMANCE: Busca em lote de processos.
     *
     * Permite buscar processos de múltiplos custodiados em uma única
     * requisição, eliminando o padrão N+1 de requisições individuais.
     *
     * POST /api/processos/batch
     * Body: { "custodiadoIds": [1, 2, 3, 45, 67] }
     */
    @PostMapping("/batch")
    @Operation(summary = "Buscar processos em lote",
            description = "Máximo 200 IDs. Retorna mapa custodiadoId -> processos.")
    public ResponseEntity<?> buscarProcessosEmLote(@Valid @RequestBody BatchProcessoRequest request) {
        log.info("Busca em lote — {} custodiados", request.getCustodiadoIds().size());
        try {
            if (request.getCustodiadoIds().size() > 200) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Máximo de 200 IDs por requisição"));
            }

            Map<Long, List<ProcessoResumoDTO>> resultado =
                    processoService.buscarProcessosPorCustodiadoIds(request.getCustodiadoIds());

            return ResponseEntity.ok(Map.of("success", true, "data", resultado, "totalCustodiados", resultado.size()));
        } catch (Exception e) {
            log.error("Erro na busca em lote", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProcessoResponseDTO>> criar(@Valid @RequestBody ProcessoDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Processo criado", processoService.criarProcesso(dto)));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcessoResponseDTO>> atualizar(@PathVariable Long id, @Valid @RequestBody ProcessoDTO dto) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Processo atualizado", processoService.atualizarProcesso(id, dto)));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<ProcessoResponseDTO>> encerrar(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Processo encerrado", processoService.encerrarProcesso(id)));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/suspender")
    public ResponseEntity<ApiResponse<ProcessoResponseDTO>> suspender(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Suspenso", processoService.suspenderProcesso(id)));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/reativar")
    public ResponseEntity<ApiResponse<ProcessoResponseDTO>> reativar(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Reativado", processoService.reativarProcesso(id)));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }
}

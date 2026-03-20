package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.ProcessoDTO;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.service.ProcessoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "Listagem paginada de processos com filtros")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) StatusComparecimento status) {

        Page<Processo> resultado = processoService.listarComFiltros(termo, status, page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("processos", resultado.getContent());
        response.put("paginaAtual", resultado.getNumber());
        response.put("totalPaginas", resultado.getTotalPages());
        response.put("totalItens", resultado.getTotalElements());
        response.put("itensPorPagina", resultado.getSize());

        return ResponseEntity.ok(ApiResponse.success("Processos listados com sucesso", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar processo por ID")
    public ResponseEntity<ApiResponse<Processo>> buscarPorId(@PathVariable Long id) {
        try {
            Processo processo = processoService.buscarPorId(id);
            return ResponseEntity.ok(ApiResponse.success("Processo encontrado", processo));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/custodiado/{custodiadoId}")
    @Operation(summary = "Processos ativos de um custodiado")
    public ResponseEntity<ApiResponse<List<Processo>>> buscarPorCustodiado(@PathVariable Long custodiadoId) {
        List<Processo> processos = processoService.buscarPorCustodiado(custodiadoId);
        return ResponseEntity.ok(ApiResponse.success("Processos do custodiado", processos));
    }

    @GetMapping("/numero/{numero}")
    @Operation(summary = "Buscar processo por número")
    public ResponseEntity<ApiResponse<Processo>> buscarPorNumero(@PathVariable String numero) {
        return processoService.buscarPorNumeroProcesso(numero)
                .map(p -> ResponseEntity.ok(ApiResponse.success("Processo encontrado", p)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Processo não encontrado: " + numero)));
    }

    @PostMapping
    @Operation(summary = "Criar novo processo vinculado a custodiado")
    public ResponseEntity<ApiResponse<Processo>> criar(@Valid @RequestBody ProcessoDTO dto) {
        try {
            Processo processo = processoService.criarProcesso(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Processo criado com sucesso", processo));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar dados processuais")
    public ResponseEntity<ApiResponse<Processo>> atualizar(@PathVariable Long id, @Valid @RequestBody ProcessoDTO dto) {
        try {
            Processo processo = processoService.atualizarProcesso(id, dto);
            return ResponseEntity.ok(ApiResponse.success("Processo atualizado com sucesso", processo));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Encerrar processo (soft delete)")
    public ResponseEntity<ApiResponse<Processo>> encerrar(@PathVariable Long id) {
        try {
            Processo processo = processoService.encerrarProcesso(id);
            return ResponseEntity.ok(ApiResponse.success("Processo encerrado com sucesso", processo));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/suspender")
    @Operation(summary = "Suspender processo")
    public ResponseEntity<ApiResponse<Processo>> suspender(@PathVariable Long id) {
        try {
            Processo processo = processoService.suspenderProcesso(id);
            return ResponseEntity.ok(ApiResponse.success("Processo suspenso", processo));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/reativar")
    @Operation(summary = "Reativar processo")
    public ResponseEntity<ApiResponse<Processo>> reativar(@PathVariable Long id) {
        try {
            Processo processo = processoService.reativarProcesso(id);
            return ResponseEntity.ok(ApiResponse.success("Processo reativado", processo));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/inadimplentes")
    @Operation(summary = "Lista processos inadimplentes")
    public ResponseEntity<ApiResponse<List<Processo>>> inadimplentes() {
        return ResponseEntity.ok(ApiResponse.success("Processos inadimplentes", processoService.buscarInadimplentes()));
    }

    @GetMapping("/hoje")
    @Operation(summary = "Comparecimentos previstos para hoje")
    public ResponseEntity<ApiResponse<List<Processo>>> hoje() {
        return ResponseEntity.ok(ApiResponse.success("Comparecimentos de hoje", processoService.buscarComparecimentosHoje()));
    }

    @GetMapping("/contadores")
    @Operation(summary = "Contadores para dashboard")
    public ResponseEntity<ApiResponse<ProcessoService.ContadoresDashboard>> contadores() {
        return ResponseEntity.ok(ApiResponse.success("Contadores do dashboard", processoService.contadoresParaDashboard()));
    }
}

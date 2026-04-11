package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
import br.jus.tjba.aclp.dto.HistoricoComparecimentoResponseDTO;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import br.jus.tjba.aclp.service.ComparecimentoService;
import br.jus.tjba.aclp.service.StatusSchedulerService;
import br.jus.tjba.aclp.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comparecimentos")
@RequiredArgsConstructor
@Tag(name = "Comparecimentos", description = "API para gerenciamento de comparecimentos")
@Slf4j
public class ComparecimentoController {

    private final ComparecimentoService comparecimentoService;
    private final StatusSchedulerService statusSchedulerService;

    @PostMapping("/registrar")
    @Operation(summary = "Registrar comparecimento")
    public ResponseEntity<Map<String, Object>> registrarComparecimento(@Valid @RequestBody ComparecimentoDTO dto) {
        try {
            HistoricoComparecimentoResponseDTO historico = comparecimentoService.registrarComparecimento(dto);
            return ApiResponseUtil.created(historico, "Comparecimento registrado com sucesso");
        } catch (Exception e) {
            log.error("Erro ao registrar comparecimento", e);
            return ApiResponseUtil.badRequest("Erro ao registrar comparecimento: " + e.getMessage());
        }
    }

    @GetMapping("/custodiado/{custodiadoId}")
    @Operation(summary = "Buscar histórico de comparecimentos por custodiado")
    public ResponseEntity<Map<String, Object>> buscarHistoricoPorCustodiado(@PathVariable Long custodiadoId) {
        try {
            List<HistoricoComparecimentoResponseDTO> historico =
                    comparecimentoService.buscarHistoricoPorCustodiado(custodiadoId);
            return ApiResponseUtil.success(historico, "Histórico encontrado");
        } catch (IllegalArgumentException e) {
            return ApiResponseUtil.badRequest(e.getMessage());
        }
    }

    @GetMapping("/periodo")
    @Operation(summary = "Buscar comparecimentos por período")
    public ResponseEntity<Map<String, Object>> buscarComparecimentosPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        try {
            List<HistoricoComparecimentoResponseDTO> comparecimentos =
                    comparecimentoService.buscarComparecimentosPorPeriodo(inicio, fim);
            return ApiResponseUtil.success(comparecimentos, "Comparecimentos encontrados");
        } catch (IllegalArgumentException e) {
            return ApiResponseUtil.badRequest(e.getMessage());
        }
    }

    @GetMapping("/hoje")
    @Operation(summary = "Comparecimentos de hoje")
    public ResponseEntity<Map<String, Object>> buscarComparecimentosHoje() {
        List<HistoricoComparecimentoResponseDTO> comparecimentos =
                comparecimentoService.buscarComparecimentosHoje();
        return ApiResponseUtil.success(comparecimentos, "Comparecimentos de hoje");
    }

    /**
     * CORREÇÃO DE PERFORMANCE: Endpoint /todos com limites e filtros server-side.
     *
     * - size > 100 é reduzido a 100 automaticamente (com log de aviso)
     * - Filtros são aplicados na query SQL, não no frontend
     * - Campo numeroProcesso incluído na resposta para eliminar N+1
     */
    @GetMapping("/todos")
    @Operation(summary = "Listar todos os comparecimentos com paginação e filtros")
    public ResponseEntity<Map<String, Object>> listarTodosComparecimentos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(required = false) String tipoValidacao,
            @RequestParam(required = false) String custodiadoNome,
            @RequestParam(required = false) String numeroProcesso) {

        try {
            // CORREÇÃO: Limitar tamanho máximo da página
            if (size > 100) {
                log.warn("[PERFORMANCE] Requisição com size={} reduzida para 100.", size);
                size = 100;
            }

            LocalDate inicio = (dataInicio != null && !dataInicio.isBlank()) ? LocalDate.parse(dataInicio) : null;
            LocalDate fim = (dataFim != null && !dataFim.isBlank()) ? LocalDate.parse(dataFim) : null;

            TipoValidacao tipo = null;
            if (tipoValidacao != null && !tipoValidacao.isBlank()) {
                try { tipo = TipoValidacao.valueOf(tipoValidacao.toUpperCase()); }
                catch (IllegalArgumentException e) { log.warn("Tipo de validação inválido: {}", tipoValidacao); }
            }

            Page<HistoricoComparecimentoResponseDTO> comparecimentosPage =
                    comparecimentoService.listarPaginadoComFiltros(
                            page, size, inicio, fim, tipo, custodiadoNome, numeroProcesso);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Comparecimentos listados com sucesso");
            response.put("comparecimentos", comparecimentosPage.getContent());
            response.put("paginaAtual", comparecimentosPage.getNumber());
            response.put("totalPaginas", comparecimentosPage.getTotalPages());
            response.put("totalItens", comparecimentosPage.getTotalElements());
            response.put("itensPorPagina", comparecimentosPage.getSize());
            response.put("temProxima", comparecimentosPage.hasNext());
            response.put("temAnterior", comparecimentosPage.hasPrevious());

            return ApiResponseUtil.success(response, "Comparecimentos listados com sucesso");
        } catch (Exception e) {
            log.error("Erro ao listar comparecimentos", e);
            return ApiResponseUtil.internalServerError("Erro ao listar comparecimentos: " + e.getMessage());
        }
    }

    @GetMapping("/filtrar")
    public ResponseEntity<Map<String, Object>> filtrarComparecimentos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String tipoValidacao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            if (size > 100) size = 100;
            Page<HistoricoComparecimentoResponseDTO> comparecimentosPage =
                    comparecimentoService.buscarComparecimentosComFiltros(dataInicio, dataFim, tipoValidacao, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("comparecimentos", comparecimentosPage.getContent());
            response.put("paginaAtual", comparecimentosPage.getNumber());
            response.put("totalPaginas", comparecimentosPage.getTotalPages());
            response.put("totalItens", comparecimentosPage.getTotalElements());
            response.put("itensPorPagina", comparecimentosPage.getSize());
            return ApiResponseUtil.success(response, "Filtrados com sucesso");
        } catch (Exception e) {
            return ApiResponseUtil.internalServerError("Erro: " + e.getMessage());
        }
    }

    @GetMapping("/custodiado/{custodiadoId}/mudancas-endereco")
    public ResponseEntity<Map<String, Object>> buscarComparecimentosComMudancaEndereco(@PathVariable Long custodiadoId) {
        try {
            return ApiResponseUtil.success(
                    comparecimentoService.buscarComparecimentosComMudancaEndereco(custodiadoId),
                    "Mudanças de endereço encontradas");
        } catch (IllegalArgumentException e) { return ApiResponseUtil.badRequest(e.getMessage()); }
    }

    @PutMapping("/{historicoId}/observacoes")
    public ResponseEntity<Map<String, Object>> atualizarObservacoes(@PathVariable Long historicoId, @RequestBody String observacoes) {
        try {
            return ApiResponseUtil.success(comparecimentoService.atualizarObservacoes(historicoId, observacoes), "Observações atualizadas");
        } catch (Exception e) { return ApiResponseUtil.badRequest(e.getMessage()); }
    }

    @PostMapping("/verificar-inadimplentes")
    public ResponseEntity<Map<String, Object>> verificarStatusInadimplentes() {
        try {
            long custodiadosMarcados = statusSchedulerService.verificarStatusManual();
            Map<String, Object> dados = new HashMap<>();
            dados.put("custodiadosMarcados", custodiadosMarcados);
            dados.put("executadoEm", LocalDateTime.now().toString());
            return ApiResponseUtil.success(dados, custodiadosMarcados == 0
                    ? "Nenhum custodiado marcado" : custodiadosMarcados + " marcados");
        } catch (Exception e) { return ApiResponseUtil.internalServerError("Erro: " + e.getMessage()); }
    }

    @GetMapping("/estatisticas")
    public ResponseEntity<Map<String, Object>> buscarEstatisticas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        try {
            return ApiResponseUtil.success(comparecimentoService.buscarEstatisticas(inicio, fim), "Estatísticas calculadas");
        } catch (IllegalArgumentException e) { return ApiResponseUtil.badRequest(e.getMessage()); }
    }

    @GetMapping("/estatisticas/geral")
    public ResponseEntity<Map<String, Object>> buscarEstatisticasGerais() {
        try {
            return ApiResponseUtil.success(comparecimentoService.buscarEstatisticasGerais(), "Estatísticas gerais");
        } catch (Exception e) { return ApiResponseUtil.internalServerError("Erro: " + e.getMessage()); }
    }

    @GetMapping("/estatisticas/detalhadas")
    public ResponseEntity<Map<String, Object>> buscarEstatisticasDetalhadas() {
        try {
            return ApiResponseUtil.success(comparecimentoService.buscarEstatisticasDetalhadas(), "Estatísticas detalhadas");
        } catch (Exception e) { return ApiResponseUtil.internalServerError("Erro: " + e.getMessage()); }
    }

    @GetMapping("/resumo/sistema")
    public ResponseEntity<Map<String, Object>> buscarResumoSistema() {
        try {
            return ApiResponseUtil.success(comparecimentoService.buscarResumoSistema(), "Resumo do sistema");
        } catch (Exception e) { return ApiResponseUtil.internalServerError("Erro: " + e.getMessage()); }
    }

    @PostMapping("/migrar/cadastros-iniciais")
    public ResponseEntity<Map<String, Object>> migrarCadastrosIniciais(
            @RequestParam(defaultValue = "Sistema SCC") String validadoPor) {
        try {
            return ApiResponseUtil.success(comparecimentoService.migrarCadastrosIniciais(validadoPor), "Migração executada");
        } catch (Exception e) { return ApiResponseUtil.badRequest("Erro: " + e.getMessage()); }
    }
}

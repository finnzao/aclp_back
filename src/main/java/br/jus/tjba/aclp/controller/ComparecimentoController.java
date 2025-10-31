package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
import br.jus.tjba.aclp.dto.HistoricoComparecimentoResponseDTO;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.service.ComparecimentoService;
import br.jus.tjba.aclp.service.StatusSchedulerService;
import br.jus.tjba.aclp.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Comparecimentos", description = "API para gerenciamento de comparecimentos e mudanças de endereço")
@Slf4j
public class ComparecimentoController {

    private final ComparecimentoService comparecimentoService;
    private final StatusSchedulerService statusSchedulerService;

    @PostMapping("/registrar")  // ← MUDANÇA APLICADA AQUI
    @Operation(summary = "Registrar comparecimento",
            description = "Registra um novo comparecimento (presencial ou online) com possível mudança de endereço")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Comparecimento registrado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Custodiado não encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflito - comparecimento já registrado na data")
    })
    public ResponseEntity<Map<String, Object>> registrarComparecimento(
            @Valid @RequestBody ComparecimentoDTO dto) {
        log.info("Registrando comparecimento - Custodiado ID: {}, Tipo: {}, Mudança endereço: {}",
                dto.getCustodiadoId(), dto.getTipoValidacao(), dto.houveMudancaEndereco());

        try {
            HistoricoComparecimento historico = comparecimentoService.registrarComparecimento(dto);
            log.info("Comparecimento registrado com sucesso - ID: {}", historico.getId());
            return ApiResponseUtil.created(historico, "Comparecimento registrado com sucesso");
        } catch (Exception e) {
            log.error("Erro ao registrar comparecimento", e);
            return ApiResponseUtil.badRequest("Erro ao registrar comparecimento: " + e.getMessage());
        }
    }

    @GetMapping("/custodiado/{custodiadoId}")
    @Operation(summary = "Buscar histórico de comparecimentos",
            description = "Retorna o histórico completo de comparecimentos de um custodiado")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Histórico retornado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID do custodiado inválido"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Custodiado não encontrado")
    })
    public ResponseEntity<Map<String, Object>> buscarHistoricoPorCustodiado(
            @Parameter(description = "ID do custodiado") @PathVariable Long custodiadoId) {
        log.info("Buscando histórico de comparecimentos - Custodiado ID: {}", custodiadoId);

        try {
            List<HistoricoComparecimento> historico = comparecimentoService.buscarHistoricoPorCustodiado(custodiadoId);
            return ApiResponseUtil.success(historico, "Histórico de comparecimentos encontrado");
        } catch (IllegalArgumentException e) {
            return ApiResponseUtil.badRequest(e.getMessage());
        }
    }

    @GetMapping("/periodo")
    @Operation(summary = "Buscar comparecimentos por período",
            description = "Retorna comparecimentos registrados em um período específico")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comparecimentos retornados com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Período inválido")
    })
    public ResponseEntity<Map<String, Object>> buscarComparecimentosPorPeriodo(
            @Parameter(description = "Data de início")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Data de fim")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        log.info("Buscando comparecimentos por período: {} a {}", inicio, fim);

        try {
            List<HistoricoComparecimento> comparecimentos =
                    comparecimentoService.buscarComparecimentosPorPeriodo(inicio, fim);
            return ApiResponseUtil.success(comparecimentos, "Comparecimentos encontrados para o período");
        } catch (IllegalArgumentException e) {
            return ApiResponseUtil.badRequest(e.getMessage());
        }
    }

    @GetMapping("/hoje")
    @Operation(summary = "Comparecimentos de hoje",
            description = "Retorna todos os comparecimentos registrados hoje")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comparecimentos de hoje retornados com sucesso")
    public ResponseEntity<Map<String, Object>> buscarComparecimentosHoje() {
        log.info("Buscando comparecimentos de hoje");

        List<HistoricoComparecimento> comparecimentos = comparecimentoService.buscarComparecimentosHoje();
        return ApiResponseUtil.success(comparecimentos, "Comparecimentos de hoje encontrados");
    }

    @GetMapping("/todos")
    @Operation(summary = "Listar todos os comparecimentos",
            description = "Retorna todos os comparecimentos registrados no sistema com paginação")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Lista de comparecimentos retornada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    description = "Erro interno do servidor")
    })
    public ResponseEntity<Map<String, Object>> listarTodosComparecimentos(
            @Parameter(description = "Número da página (inicia em 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Quantidade de itens por página")
            @RequestParam(defaultValue = "50") int size) {

        log.info("Listando todos os comparecimentos - Página: {}, Tamanho: {}", page, size);

        try {
            Page<HistoricoComparecimentoResponseDTO> comparecimentosPage =
                    comparecimentoService.buscarTodosComparecimentos(page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("comparecimentos", comparecimentosPage.getContent());
            response.put("paginaAtual", comparecimentosPage.getNumber());
            response.put("totalPaginas", comparecimentosPage.getTotalPages());
            response.put("totalItens", comparecimentosPage.getTotalElements());
            response.put("itensPorPagina", comparecimentosPage.getSize());
            response.put("temProxima", comparecimentosPage.hasNext());
            response.put("temAnterior", comparecimentosPage.hasPrevious());

            return ApiResponseUtil.success(response, "Comparecimentos listados com sucesso");
        } catch (Exception e) {
            log.error("Erro ao listar todos os comparecimentos", e);
            return ApiResponseUtil.internalServerError("Erro ao listar comparecimentos: " + e.getMessage());
        }
    }

    // NOVO ENDPOINT
    @GetMapping("/filtrar")
    public ResponseEntity<Map<String, Object>> filtrarComparecimentos(
            @Parameter(description = "Data inicial (formato: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data final (formato: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @Parameter(description = "Tipo de validação (PRESENCIAL ou ONLINE)")
            @RequestParam(required = false) String tipoValidacao,
            @Parameter(description = "Número da página")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Itens por página")
            @RequestParam(defaultValue = "50") int size) {

        log.info("Filtrando comparecimentos - Início: {}, Fim: {}, Tipo: {}",
                dataInicio, dataFim, tipoValidacao);

        try {
            Page<HistoricoComparecimentoResponseDTO> comparecimentosPage =
                    comparecimentoService.buscarComparecimentosComFiltros(
                            dataInicio, dataFim, tipoValidacao, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("comparecimentos", comparecimentosPage.getContent());
            response.put("paginaAtual", comparecimentosPage.getNumber());
            response.put("totalPaginas", comparecimentosPage.getTotalPages());
            response.put("totalItens", comparecimentosPage.getTotalElements());
            response.put("itensPorPagina", comparecimentosPage.getSize());

            return ApiResponseUtil.success(response, "Comparecimentos filtrados com sucesso");
        } catch (Exception e) {
            log.error("Erro ao filtrar comparecimentos", e);
            return ApiResponseUtil.internalServerError("Erro ao filtrar comparecimentos: " + e.getMessage());
        }
    }

    @GetMapping("/custodiado/{custodiadoId}/mudancas-endereco")
    @Operation(summary = "Comparecimentos com mudança de endereço",
            description = "Retorna comparecimentos de um custodiado que incluíram mudança de endereço")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comparecimentos retornados com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID do custodiado inválido")
    })
    public ResponseEntity<Map<String, Object>> buscarComparecimentosComMudancaEndereco(
            @Parameter(description = "ID do custodiado") @PathVariable Long custodiadoId) {
        log.info("Buscando comparecimentos com mudança de endereço - Custodiado ID: {}", custodiadoId);

        try {
            List<HistoricoComparecimento> comparecimentos =
                    comparecimentoService.buscarComparecimentosComMudancaEndereco(custodiadoId);
            return ApiResponseUtil.success(comparecimentos, "Comparecimentos com mudança de endereço encontrados");
        } catch (IllegalArgumentException e) {
            return ApiResponseUtil.badRequest(e.getMessage());
        }
    }

    @PutMapping("/{historicoId}/observacoes")
    @Operation(summary = "Atualizar observações",
            description = "Atualiza as observações de um comparecimento registrado")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Observações atualizadas com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Comparecimento não encontrado")
    })
    public ResponseEntity<Map<String, Object>> atualizarObservacoes(
            @Parameter(description = "ID do histórico de comparecimento") @PathVariable Long historicoId,
            @Parameter(description = "Novas observações") @RequestBody String observacoes) {
        log.info("Atualizando observações do comparecimento ID: {}", historicoId);

        try {
            HistoricoComparecimento historico = comparecimentoService.atualizarObservacoes(historicoId, observacoes);
            return ApiResponseUtil.success(historico, "Observações atualizadas com sucesso");
        } catch (Exception e) {
            return ApiResponseUtil.badRequest(e.getMessage());
        }
    }

    @PostMapping("/verificar-inadimplentes")
    @Operation(summary = "Verificar inadimplentes",
            description = "Executa verificação automática de custodiados inadimplentes")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verificação executada com sucesso")
    public ResponseEntity<Map<String, Object>> verificarStatusInadimplentes() {
        log.info("Executando verificação de status inadimplentes");

        try {
            long custodiadosMarcados = statusSchedulerService.verificarStatusManual();

            String mensagem = custodiadosMarcados == 0
                    ? "Nenhum custodiado foi marcado como inadimplente"
                    : String.format("%d custodiado(s) foram marcados como inadimplentes", custodiadosMarcados);

            Map<String, Object> dados = new HashMap<>();
            dados.put("custodiadosMarcados", custodiadosMarcados);
            dados.put("executadoEm", LocalDateTime.now().toString());

            return ApiResponseUtil.success(dados, mensagem);
        } catch (Exception e) {
            log.error("Erro na verificação de inadimplentes", e);
            return ApiResponseUtil.internalServerError("Erro ao verificar inadimplentes: " + e.getMessage());
        }
    }

    @GetMapping("/estatisticas")
    @Operation(summary = "Estatísticas de comparecimentos",
            description = "Retorna estatísticas de comparecimentos por período")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Estatísticas retornadas com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Período inválido")
    })
    public ResponseEntity<Map<String, Object>> buscarEstatisticas(
            @Parameter(description = "Data de início")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Data de fim")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        log.info("Buscando estatísticas de comparecimentos: {} a {}", inicio, fim);

        try {
            ComparecimentoService.EstatisticasComparecimento estatisticas =
                    comparecimentoService.buscarEstatisticas(inicio, fim);
            return ApiResponseUtil.success(estatisticas, "Estatísticas calculadas com sucesso");
        } catch (IllegalArgumentException e) {
            return ApiResponseUtil.badRequest(e.getMessage());
        }
    }

    @GetMapping("/estatisticas/geral")
    @Operation(summary = "Estatísticas gerais de comparecimentos",
            description = "Retorna estatísticas de todos os comparecimentos registrados no sistema")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Estatísticas gerais retornadas com sucesso")
    public ResponseEntity<Map<String, Object>> buscarEstatisticasGerais() {
        log.info("Buscando estatísticas gerais de comparecimentos");

        try {
            ComparecimentoService.EstatisticasGerais estatisticas =
                    comparecimentoService.buscarEstatisticasGerais();
            return ApiResponseUtil.success(estatisticas, "Estatísticas gerais obtidas com sucesso");
        } catch (Exception e) {
            log.error("Erro ao buscar estatísticas gerais", e);
            return ApiResponseUtil.internalServerError("Erro interno ao buscar estatísticas: " + e.getMessage());
        }
    }



    @GetMapping("/estatisticas/detalhadas")
    @Operation(summary = "Estatísticas detalhadas",
            description = "Retorna estatísticas completas sobre comparecimentos")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Estatísticas retornadas com sucesso")
    public ResponseEntity<Map<String, Object>> buscarEstatisticasDetalhadas() {
        log.info("Buscando estatísticas detalhadas de comparecimentos");

        try {
            Map<String, Object> estatisticas = comparecimentoService.buscarEstatisticasDetalhadas();
            return ApiResponseUtil.success(estatisticas, "Estatísticas obtidas com sucesso");
        } catch (Exception e) {
            log.error("Erro ao buscar estatísticas detalhadas", e);
            return ApiResponseUtil.internalServerError("Erro ao buscar estatísticas: " + e.getMessage());
        }
    }

    @GetMapping("/resumo/sistema")
    @Operation(summary = "Resumo completo do sistema",
            description = "Retorna resumo com custodiados cadastrados, comparecimentos e estatísticas gerais")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resumo do sistema retornado com sucesso")
    public ResponseEntity<Map<String, Object>> buscarResumoSistema() {
        log.info("Buscando resumo completo do sistema");

        try {
            ComparecimentoService.ResumoSistema resumo =
                    comparecimentoService.buscarResumoSistema();
            return ApiResponseUtil.success(resumo, "Resumo do sistema obtido com sucesso");
        } catch (Exception e) {
            log.error("Erro ao buscar resumo do sistema", e);
            return ApiResponseUtil.internalServerError("Erro interno ao buscar resumo: " + e.getMessage());
        }
    }

    @PostMapping("/migrar/cadastros-iniciais")
    @Operation(summary = "Migrar cadastros iniciais",
            description = "Cria comparecimentos iniciais para custodiados que não possuem histórico")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Migração executada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Erro na migração")
    })
    public ResponseEntity<Map<String, Object>> migrarCadastrosIniciais(
            @Parameter(description = "Nome do responsável pela migração")
            @RequestParam(defaultValue = "Sistema ACLP") String validadoPor) {

        log.info("Executando migração de cadastros iniciais - Validado por: {}", validadoPor);

        try {
            Map<String, Object> resultado = comparecimentoService.migrarCadastrosIniciais(validadoPor);
            return ApiResponseUtil.success(resultado, "Migração de cadastros iniciais executada com sucesso");
        } catch (Exception e) {
            log.error("Erro na migração de cadastros iniciais", e);
            return ApiResponseUtil.badRequest("Erro na migração: " + e.getMessage());
        }
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
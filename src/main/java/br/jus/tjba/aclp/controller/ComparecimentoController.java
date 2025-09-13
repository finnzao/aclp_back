package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
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
@CrossOrigin(origins = "*", maxAge = 3600,
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Comparecimentos", description = "API para gerenciamento de comparecimentos e mudanças de endereço")
@Slf4j
public class ComparecimentoController {

    private final ComparecimentoService comparecimentoService;
    private final StatusSchedulerService statusSchedulerService;

    @PostMapping
    @Operation(summary = "Registrar comparecimento",
            description = "Registra um novo comparecimento (presencial ou online) com possível mudança de endereço")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Comparecimento registrado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pessoa não encontrada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflito - comparecimento já registrado na data")
    })
    public ResponseEntity<Map<String, Object>> registrarComparecimento(
            @Valid @RequestBody ComparecimentoDTO dto) {
        log.info("Registrando comparecimento - Pessoa ID: {}, Tipo: {}, Mudança endereço: {}",
                dto.getPessoaId(), dto.getTipoValidacao(), dto.houveMudancaEndereco());

        try {
            HistoricoComparecimento historico = comparecimentoService.registrarComparecimento(dto);
            log.info("Comparecimento registrado com sucesso - ID: {}", historico.getId());
            return ApiResponseUtil.created(historico, "Comparecimento registrado com sucesso");
        } catch (Exception e) {
            log.error("Erro ao registrar comparecimento", e);
            return ApiResponseUtil.badRequest("Erro ao registrar comparecimento: " + e.getMessage());
        }
    }

    @GetMapping("/pessoa/{pessoaId}")
    @Operation(summary = "Buscar histórico de comparecimentos",
            description = "Retorna o histórico completo de comparecimentos de uma pessoa")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Histórico retornado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID da pessoa inválido"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<Map<String, Object>> buscarHistoricoPorPessoa(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId) {
        log.info("Buscando histórico de comparecimentos - Pessoa ID: {}", pessoaId);

        try {
            List<HistoricoComparecimento> historico = comparecimentoService.buscarHistoricoPorPessoa(pessoaId);
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

    @GetMapping("/pessoa/{pessoaId}/mudancas-endereco")
    @Operation(summary = "Comparecimentos com mudança de endereço",
            description = "Retorna comparecimentos de uma pessoa que incluíram mudança de endereço")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comparecimentos retornados com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID da pessoa inválido")
    })
    public ResponseEntity<Map<String, Object>> buscarComparecimentosComMudancaEndereco(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId) {
        log.info("Buscando comparecimentos com mudança de endereço - Pessoa ID: {}", pessoaId);

        try {
            List<HistoricoComparecimento> comparecimentos =
                    comparecimentoService.buscarComparecimentosComMudancaEndereco(pessoaId);
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
            description = "Executa verificação automática de pessoas inadimplentes")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verificação executada com sucesso")
    public ResponseEntity<Map<String, Object>> verificarStatusInadimplentes() {
        log.info("Executando verificação de status inadimplentes");

        try {
            long pessoasMarcadas = statusSchedulerService.verificarStatusManual();

            String mensagem = pessoasMarcadas == 0
                    ? "Nenhuma pessoa foi marcada como inadimplente"
                    : String.format("%d pessoa(s) foram marcadas como inadimplentes", pessoasMarcadas);

            Map<String, Object> dados = new HashMap<>();
            dados.put("pessoasMarcadas", pessoasMarcadas);
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

    @GetMapping("/resumo/sistema")
    @Operation(summary = "Resumo completo do sistema",
            description = "Retorna resumo com pessoas cadastradas, comparecimentos e estatísticas gerais")
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
            description = "Cria comparecimentos iniciais para pessoas que não possuem histórico")
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
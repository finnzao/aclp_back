package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.service.ComparecimentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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

    @PostMapping
    @Operation(summary = "Registrar comparecimento",
            description = "Registra um novo comparecimento (presencial ou online) com possível mudança de endereço")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Comparecimento registrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada"),
            @ApiResponse(responseCode = "409", description = "Conflito - comparecimento já registrado na data")
    })
    public ResponseEntity<HistoricoComparecimento> registrarComparecimento(
            @Valid @RequestBody ComparecimentoDTO dto) {
        log.info("Registrando comparecimento - Pessoa ID: {}, Tipo: {}, Mudança endereço: {}",
                dto.getPessoaId(), dto.getTipoValidacao(), dto.houveMudancaEndereco());

        HistoricoComparecimento historico = comparecimentoService.registrarComparecimento(dto);

        log.info("Comparecimento registrado com sucesso - ID: {}", historico.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(historico);
    }

    @GetMapping("/pessoa/{pessoaId}")
    @Operation(summary = "Buscar histórico de comparecimentos",
            description = "Retorna o histórico completo de comparecimentos de uma pessoa")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Histórico retornado com sucesso"),
            @ApiResponse(responseCode = "400", description = "ID da pessoa inválido"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<List<HistoricoComparecimento>> buscarHistoricoPorPessoa(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId) {
        log.info("Buscando histórico de comparecimentos - Pessoa ID: {}", pessoaId);

        List<HistoricoComparecimento> historico = comparecimentoService.buscarHistoricoPorPessoa(pessoaId);
        return ResponseEntity.ok(historico);
    }

    @GetMapping("/periodo")
    @Operation(summary = "Buscar comparecimentos por período",
            description = "Retorna comparecimentos registrados em um período específico")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comparecimentos retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Período inválido")
    })
    public ResponseEntity<List<HistoricoComparecimento>> buscarComparecimentosPorPeriodo(
            @Parameter(description = "Data de início")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Data de fim")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        log.info("Buscando comparecimentos por período: {} a {}", inicio, fim);

        List<HistoricoComparecimento> comparecimentos =
                comparecimentoService.buscarComparecimentosPorPeriodo(inicio, fim);
        return ResponseEntity.ok(comparecimentos);
    }

    @GetMapping("/hoje")
    @Operation(summary = "Comparecimentos de hoje",
            description = "Retorna todos os comparecimentos registrados hoje")
    @ApiResponse(responseCode = "200", description = "Comparecimentos de hoje retornados com sucesso")
    public ResponseEntity<List<HistoricoComparecimento>> buscarComparecimentosHoje() {
        log.info("Buscando comparecimentos de hoje");

        List<HistoricoComparecimento> comparecimentos = comparecimentoService.buscarComparecimentosHoje();
        return ResponseEntity.ok(comparecimentos);
    }

    @GetMapping("/pessoa/{pessoaId}/mudancas-endereco")
    @Operation(summary = "Comparecimentos com mudança de endereço",
            description = "Retorna comparecimentos de uma pessoa que incluíram mudança de endereço")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Comparecimentos retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "ID da pessoa inválido")
    })
    public ResponseEntity<List<HistoricoComparecimento>> buscarComparecimentosComMudancaEndereco(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId) {
        log.info("Buscando comparecimentos com mudança de endereço - Pessoa ID: {}", pessoaId);

        List<HistoricoComparecimento> comparecimentos =
                comparecimentoService.buscarComparecimentosComMudancaEndereco(pessoaId);
        return ResponseEntity.ok(comparecimentos);
    }

    @PutMapping("/{historicoId}/observacoes")
    @Operation(summary = "Atualizar observações",
            description = "Atualiza as observações de um comparecimento registrado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Observações atualizadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Comparecimento não encontrado")
    })
    public ResponseEntity<HistoricoComparecimento> atualizarObservacoes(
            @Parameter(description = "ID do histórico de comparecimento") @PathVariable Long historicoId,
            @Parameter(description = "Novas observações") @RequestBody String observacoes) {
        log.info("Atualizando observações do comparecimento ID: {}", historicoId);

        HistoricoComparecimento historico = comparecimentoService.atualizarObservacoes(historicoId, observacoes);
        return ResponseEntity.ok(historico);
    }

    @PostMapping("/verificar-inadimplentes")
    @Operation(summary = "Verificar inadimplentes",
            description = "Executa verificação automática de pessoas inadimplentes")
    @ApiResponse(responseCode = "200", description = "Verificação executada com sucesso")
    public ResponseEntity<String> verificarStatusInadimplentes() {
        log.info("Executando verificação de status inadimplentes");

        comparecimentoService.verificarStatusInadimplentes();
        return ResponseEntity.ok("Verificação de status inadimplentes executada com sucesso");
    }

    @GetMapping("/estatisticas")
    @Operation(summary = "Estatísticas de comparecimentos",
            description = "Retorna estatísticas de comparecimentos por período")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estatísticas retornadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Período inválido")
    })
    public ResponseEntity<ComparecimentoService.EstatisticasComparecimento> buscarEstatisticas(
            @Parameter(description = "Data de início")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Data de fim")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        log.info("Buscando estatísticas de comparecimentos: {} a {}", inicio, fim);

        ComparecimentoService.EstatisticasComparecimento estatisticas =
                comparecimentoService.buscarEstatisticas(inicio, fim);
        return ResponseEntity.ok(estatisticas);
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
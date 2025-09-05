package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.HistoricoEnderecoDTO;
import br.jus.tjba.aclp.model.Pessoa;
import br.jus.tjba.aclp.service.HistoricoEnderecoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/historico-enderecos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600,
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Histórico de Endereços", description = "API para consulta do histórico de endereços das pessoas")
@Slf4j
public class HistoricoEnderecoController {

    private final HistoricoEnderecoService historicoEnderecoService;

    @GetMapping("/pessoa/{pessoaId}")
    @Operation(summary = "Histórico completo de endereços",
            description = "Retorna o histórico completo de endereços de uma pessoa ordenado por data de início")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Histórico retornado com sucesso"),
            @ApiResponse(responseCode = "400", description = "ID da pessoa inválido"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<List<HistoricoEnderecoDTO>> buscarHistoricoPorPessoa(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId) {
        log.info("Buscando histórico de endereços - Pessoa ID: {}", pessoaId);

        List<HistoricoEnderecoDTO> historico = historicoEnderecoService.buscarHistoricoPorPessoa(pessoaId);
        return ResponseEntity.ok(historico);
    }

    @GetMapping("/pessoa/{pessoaId}/ativo")
    @Operation(summary = "Endereço atual",
            description = "Retorna o endereço ativo atual de uma pessoa")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Endereço ativo encontrado"),
            @ApiResponse(responseCode = "204", description = "Pessoa não possui endereço ativo"),
            @ApiResponse(responseCode = "400", description = "ID da pessoa inválido")
    })
    public ResponseEntity<HistoricoEnderecoDTO> buscarEnderecoAtivo(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId) {
        log.info("Buscando endereço ativo - Pessoa ID: {}", pessoaId);

        Optional<HistoricoEnderecoDTO> endereco = historicoEnderecoService.buscarEnderecoAtivo(pessoaId);
        return endereco.map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/pessoa/{pessoaId}/historicos")
    @Operation(summary = "Endereços históricos",
            description = "Retorna apenas os endereços históricos (finalizados) de uma pessoa")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Endereços históricos retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "ID da pessoa inválido")
    })
    public ResponseEntity<List<HistoricoEnderecoDTO>> buscarEnderecosHistoricos(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId) {
        log.info("Buscando endereços históricos - Pessoa ID: {}", pessoaId);

        List<HistoricoEnderecoDTO> enderecos = historicoEnderecoService.buscarEnderecosHistoricos(pessoaId);
        return ResponseEntity.ok(enderecos);
    }

    @GetMapping("/pessoa/{pessoaId}/periodo")
    @Operation(summary = "Endereços por período",
            description = "Retorna endereços de uma pessoa que estavam ativos em um período específico")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Endereços retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos")
    })
    public ResponseEntity<List<HistoricoEnderecoDTO>> buscarEnderecosPorPeriodo(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId,
            @Parameter(description = "Data de início do período")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Data de fim do período")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        log.info("Buscando endereços por período - Pessoa ID: {}, Período: {} a {}", pessoaId, inicio, fim);

        List<HistoricoEnderecoDTO> enderecos = historicoEnderecoService.buscarEnderecosPorPeriodo(pessoaId, inicio, fim);
        return ResponseEntity.ok(enderecos);
    }

    @GetMapping("/cidade/{cidade}/pessoas")
    @Operation(summary = "Pessoas por cidade",
            description = "Retorna pessoas que moram ou já moraram em uma cidade específica")
    @ApiResponse(responseCode = "200", description = "Pessoas retornadas com sucesso")
    public ResponseEntity<List<Pessoa>> buscarPessoasPorCidade(
            @Parameter(description = "Nome da cidade") @PathVariable String cidade) {
        log.info("Buscando pessoas por cidade: {}", cidade);

        List<Pessoa> pessoas = historicoEnderecoService.buscarPessoasPorCidade(cidade);
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/estado/{estado}/pessoas")
    @Operation(summary = "Pessoas por estado",
            description = "Retorna pessoas que moram ou já moraram em um estado específico")
    @ApiResponse(responseCode = "200", description = "Pessoas retornadas com sucesso")
    public ResponseEntity<List<Pessoa>> buscarPessoasPorEstado(
            @Parameter(description = "Sigla do estado (ex: BA, SP)") @PathVariable String estado) {
        log.info("Buscando pessoas por estado: {}", estado);

        List<Pessoa> pessoas = historicoEnderecoService.buscarPessoasPorEstado(estado);
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/mudancas/periodo")
    @Operation(summary = "Mudanças por período",
            description = "Retorna todas as mudanças de endereço registradas em um período")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Mudanças retornadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Período inválido")
    })
    public ResponseEntity<List<HistoricoEnderecoDTO>> buscarMudancasPorPeriodo(
            @Parameter(description = "Data de início")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @Parameter(description = "Data de fim")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        log.info("Buscando mudanças de endereço por período: {} a {}", inicio, fim);

        List<HistoricoEnderecoDTO> mudancas = historicoEnderecoService.buscarMudancasPorPeriodo(inicio, fim);
        return ResponseEntity.ok(mudancas);
    }

    @GetMapping("/pessoa/{pessoaId}/total")
    @Operation(summary = "Total de endereços",
            description = "Retorna o número total de endereços que uma pessoa já teve")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Total retornado com sucesso"),
            @ApiResponse(responseCode = "400", description = "ID da pessoa inválido")
    })
    public ResponseEntity<Long> contarEnderecosPorPessoa(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId) {
        log.info("Contando endereços - Pessoa ID: {}", pessoaId);

        long total = historicoEnderecoService.contarEnderecosPorPessoa(pessoaId);
        return ResponseEntity.ok(total);
    }

    @GetMapping("/data/{data}/ativos")
    @Operation(summary = "Endereços ativos em data específica",
            description = "Retorna todos os endereços que estavam ativos em uma data específica")
    @ApiResponse(responseCode = "200", description = "Endereços retornados com sucesso")
    public ResponseEntity<List<HistoricoEnderecoDTO>> buscarEnderecosAtivosPorData(
            @Parameter(description = "Data de referência")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        log.info("Buscando endereços ativos em: {}", data);

        List<HistoricoEnderecoDTO> enderecos = historicoEnderecoService.buscarEnderecosAtivosPorData(data);
        return ResponseEntity.ok(enderecos);
    }

    @GetMapping("/motivo")
    @Operation(summary = "Endereços por motivo",
            description = "Busca endereços por motivo de alteração")
    @ApiResponse(responseCode = "200", description = "Endereços retornados com sucesso")
    public ResponseEntity<List<HistoricoEnderecoDTO>> buscarPorMotivoAlteracao(
            @Parameter(description = "Termo de busca no motivo") @RequestParam String motivo) {
        log.info("Buscando endereços por motivo: {}", motivo);

        List<HistoricoEnderecoDTO> enderecos = historicoEnderecoService.buscarPorMotivoAlteracao(motivo);
        return ResponseEntity.ok(enderecos);
    }

    @GetMapping("/pessoa/{pessoaId}/anterior")
    @Operation(summary = "Último endereço anterior",
            description = "Busca o último endereço de uma pessoa anterior a uma data específica")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Endereço encontrado"),
            @ApiResponse(responseCode = "204", description = "Nenhum endereço anterior encontrado"),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos")
    })
    public ResponseEntity<HistoricoEnderecoDTO> buscarUltimoEnderecoAnterior(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId,
            @Parameter(description = "Data de referência")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        log.info("Buscando último endereço anterior - Pessoa ID: {}, Data: {}", pessoaId, data);

        Optional<HistoricoEnderecoDTO> endereco = historicoEnderecoService.buscarUltimoEnderecoAnterior(pessoaId, data);
        return endereco.map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/estatisticas")
    @Operation(summary = "Estatísticas de endereços",
            description = "Retorna estatísticas gerais sobre endereços e mudanças")
    @ApiResponse(responseCode = "200", description = "Estatísticas retornadas com sucesso")
    public ResponseEntity<List<HistoricoEnderecoService.EstatisticasEndereco>> buscarEstatisticas() {
        log.info("Buscando estatísticas de endereços");

        List<HistoricoEnderecoService.EstatisticasEndereco> estatisticas =
                historicoEnderecoService.buscarEstatisticasPorCidade();
        return ResponseEntity.ok(estatisticas);
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
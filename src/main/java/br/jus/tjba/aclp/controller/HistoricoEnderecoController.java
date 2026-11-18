package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.HistoricoEnderecoDTO;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.service.HistoricoEnderecoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/historico-enderecos")
@RequiredArgsConstructor
@Tag(name = "Histórico de Endereços", description = "API para consulta do histórico de endereços das pessoas")
@Slf4j
public class HistoricoEnderecoController {

    private final HistoricoEnderecoService historicoEnderecoService;

    @PostMapping("/pessoa/{pessoaId}")
    @Operation(summary = "Registrar endereço atual", description = "Desativa o anterior e abre um novo ativo na trilha.")
    public ResponseEntity<ApiResponse<HistoricoEnderecoDTO>> registrar(
            @PathVariable Long pessoaId,
            @RequestBody HistoricoEnderecoDTO dto,
            org.springframework.security.core.Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Endereço registrado",
                historicoEnderecoService.registrarEndereco(pessoaId, dto, auth.getName())));
    }

    @GetMapping("/pessoa/{pessoaId}")
    @Operation(summary = "Histórico completo de endereços",
            description = "Retorna o histórico completo de endereços de uma pessoa ordenado por data de início")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Histórico retornado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID da pessoa inválido"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pessoa monitorada não encontrada")
    })
    public ResponseEntity<ApiResponse<List<HistoricoEnderecoDTO>>> buscarHistoricoPorCustodiado(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId) {
        log.info("Buscando histórico de endereços - Pessoa monitorada ID: {}", pessoaId);

        List<HistoricoEnderecoDTO> historico = historicoEnderecoService.buscarHistoricoPorCustodiado(pessoaId);
        return ResponseEntity.ok(
                ApiResponse.success("Histórico de endereços encontrado", historico)
        );
    }

    @GetMapping("/pessoa/{pessoaId}/ativo")
    @Operation(summary = "Endereço atual",
            description = "Retorna o endereço ativo atual de uma pessoa")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Endereço ativo encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Pessoa monitorada não possui endereço ativo"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID da pessoa inválido")
    })
    public ResponseEntity<ApiResponse<HistoricoEnderecoDTO>> buscarEnderecoAtivo(
            @Parameter(description = "ID da pessoa") @PathVariable Long pessoaId) {
        log.info("Buscando endereço ativo - Pessoa monitorada ID: {}", pessoaId);

        Optional<HistoricoEnderecoDTO> endereco = historicoEnderecoService.buscarEnderecoAtivo(pessoaId);
        return endereco.map(dto -> ResponseEntity.ok(
                ApiResponse.success("Endereço ativo encontrado", dto)
        )).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse.error("Pessoa monitorada não possui endereço ativo")
        ));
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.CustodiadoDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.service.CustodiadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/custodiados")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600,
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Custodiados", description = "API para gerenciamento de custodiados em liberdade provisória")
@Slf4j
public class CustodiadoController {

    private final CustodiadoService custodiadoService;

    @GetMapping
    @Operation(summary = "Listar todos os custodiados", description = "Retorna uma lista com todos os custodiados cadastrados")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de custodiados retornada com sucesso")
    })
    public ResponseEntity<ApiResponse<List<Custodiado>>> findAll() {
        log.info("Listando todos os custodiados");
        List<Custodiado> custodiados = custodiadoService.findAll();
        return ResponseEntity.ok(
                ApiResponse.success("Custodiados listados com sucesso", custodiados)
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar custodiado por ID", description = "Retorna um custodiado específico pelo seu ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custodiado encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Custodiado não encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID inválido")
    })
    public ResponseEntity<ApiResponse<Custodiado>> findById(@Parameter(description = "ID do custodiado") @PathVariable Long id) {
        log.info("Buscando custodiado por ID: {}", id);

        return custodiadoService.findById(id)
                .map(custodiado -> ResponseEntity.ok(
                        ApiResponse.success("Custodiado encontrado com sucesso", custodiado)
                ))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ApiResponse.error("Custodiado não encontrado com ID: " + id)
                ));
    }

    @GetMapping("/processo/{processo}")
    @Operation(summary = "Buscar custodiados por processo",
            description = "Retorna todos os custodiados de um processo (pode haver múltiplos réus no mesmo processo)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custodiados encontrados"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Nenhum custodiado encontrado para este processo"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Número do processo inválido")
    })
    public ResponseEntity<ApiResponse<List<Custodiado>>> findByProcesso(@Parameter(description = "Número do processo") @PathVariable String processo) {
        log.info("Buscando custodiados por processo: {}", processo);

        List<Custodiado> custodiados = custodiadoService.findByProcesso(processo);

        if (custodiados.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Nenhum custodiado encontrado com processo: " + processo)
            );
        }

        return ResponseEntity.ok(
                ApiResponse.success("Custodiados encontrados com sucesso", custodiados)
        );
    }

    @PostMapping
    @Operation(summary = "Cadastrar novo custodiado", description = "Cadastra um novo custodiado no sistema")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Custodiado cadastrado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflito - CPF ou RG já cadastrado")
    })
    public ResponseEntity<ApiResponse<Custodiado>> save(@Valid @RequestBody CustodiadoDTO dto) {
        log.info("Cadastrando novo custodiado - Processo: {}, Nome: {}", dto.getProcesso(), dto.getNome());

        try {
            Custodiado custodiado = custodiadoService.save(dto);
            log.info("Custodiado cadastrado com sucesso. ID: {}", custodiado.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.success("Custodiado cadastrado com sucesso", custodiado)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar custodiado", description = "Atualiza os dados de um custodiado existente")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custodiado atualizado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Custodiado não encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflito - CPF ou RG já cadastrado")
    })
    public ResponseEntity<ApiResponse<Custodiado>> update(@PathVariable Long id, @Valid @RequestBody CustodiadoDTO dto) {
        log.info("Atualizando custodiado ID: {}", id);

        try {
            Custodiado custodiado = custodiadoService.update(id, dto);
            log.info("Custodiado atualizado com sucesso. ID: {}", custodiado.getId());
            return ResponseEntity.ok(
                    ApiResponse.success("Custodiado atualizado com sucesso", custodiado)
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error(e.getMessage())
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir custodiado", description = "Remove um custodiado do sistema")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custodiado excluído com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID inválido ou operação não permitida"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Custodiado não encontrado")
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        log.info("Excluindo custodiado ID: {}", id);

        try {
            custodiadoService.delete(id);
            log.info("Custodiado excluído com sucesso. ID: {}", id);
            return ResponseEntity.ok(
                    ApiResponse.success("Custodiado excluído com sucesso")
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error(e.getMessage())
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Buscar custodiados por status", description = "Retorna custodiados filtrados por status de comparecimento")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de custodiados retornada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Status inválido")
    })
    public ResponseEntity<ApiResponse<List<Custodiado>>> findByStatus(@Parameter(description = "Status do comparecimento") @PathVariable StatusComparecimento status) {
        log.info("Buscando custodiados por status: {}", status);

        try {
            List<Custodiado> custodiados = custodiadoService.findByStatus(status);
            return ResponseEntity.ok(
                    ApiResponse.success("Custodiados encontrados com sucesso", custodiados)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @GetMapping("/comparecimentos/hoje")
    @Operation(summary = "Comparecimentos de hoje", description = "Retorna custodiados que devem comparecer hoje")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de custodiados retornada com sucesso")
    public ResponseEntity<ApiResponse<List<Custodiado>>> findComparecimentosHoje() {
        log.info("Buscando custodiados com comparecimento hoje");
        List<Custodiado> custodiados = custodiadoService.findComparecimentosHoje();
        return ResponseEntity.ok(
                ApiResponse.success("Custodiados com comparecimento hoje listados com sucesso", custodiados)
        );
    }

    @GetMapping("/inadimplentes")
    @Operation(summary = "Custodiados inadimplentes",
            description = "Retorna custodiados que estão inadimplentes (atrasados) com o comparecimento")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de custodiados retornada com sucesso")
    public ResponseEntity<ApiResponse<List<Custodiado>>> findInadimplentes() {
        log.info("Buscando custodiados inadimplentes");
        List<Custodiado> custodiados = custodiadoService.findInadimplentes();
        return ResponseEntity.ok(
                ApiResponse.success("Custodiados inadimplentes listados com sucesso", custodiados)
        );
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar custodiados", description = "Busca custodiados por nome ou número do processo")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de custodiados retornada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Termo de busca inválido")
    })
    public ResponseEntity<ApiResponse<List<Custodiado>>> buscar(@Parameter(description = "Termo de busca") @RequestParam String termo) {
        log.info("Buscando custodiados por termo: {}", termo);

        try {
            List<Custodiado> custodiados = custodiadoService.buscarPorNomeOuProcesso(termo);
            return ResponseEntity.ok(
                    ApiResponse.success("Busca realizada com sucesso", custodiados)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @GetMapping("/processo/{processo}/count")
    @Operation(summary = "Contar custodiados por processo",
            description = "Retorna a quantidade de custodiados em um processo específico")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Contagem retornada com sucesso")
    })
    public ResponseEntity<ApiResponse<Long>> countByProcesso(@Parameter(description = "Número do processo") @PathVariable String processo) {
        log.info("Contando custodiados no processo: {}", processo);

        List<Custodiado> custodiados = custodiadoService.findByProcesso(processo);
        long count = custodiados.size();

        return ResponseEntity.ok(
                ApiResponse.success(String.format("Processo %s tem %d custodiado(s)", processo, count), count)
        );
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
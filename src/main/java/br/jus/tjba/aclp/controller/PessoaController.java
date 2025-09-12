package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.PessoaDTO;
import br.jus.tjba.aclp.model.Pessoa;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.service.PessoaService;
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
@RequestMapping("/api/pessoas")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600,
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Pessoas", description = "API para gerenciamento de pessoas em liberdade provisória")
@Slf4j
public class PessoaController {

    private final PessoaService pessoaService;

    @GetMapping
    @Operation(summary = "Listar todas as pessoas", description = "Retorna uma lista com todas as pessoas cadastradas")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso")
    })
    public ResponseEntity<ApiResponse<List<Pessoa>>> findAll() {
        log.info("Listando todas as pessoas");
        List<Pessoa> pessoas = pessoaService.findAll();
        return ResponseEntity.ok(
                ApiResponse.success("Pessoas listadas com sucesso", pessoas)
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pessoa por ID", description = "Retorna uma pessoa específica pelo seu ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pessoa encontrada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pessoa não encontrada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID inválido")
    })
    public ResponseEntity<ApiResponse<Pessoa>> findById(@Parameter(description = "ID da pessoa") @PathVariable Long id) {
        log.info("Buscando pessoa por ID: {}", id);

        return pessoaService.findById(id)
                .map(pessoa -> ResponseEntity.ok(
                        ApiResponse.success("Pessoa encontrada com sucesso", pessoa)
                ))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ApiResponse.error("Pessoa não encontrada com ID: " + id)
                ));
    }

    @GetMapping("/processo/{processo}")
    @Operation(summary = "Buscar pessoa por processo", description = "Retorna uma pessoa pelo número do processo")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pessoa encontrada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pessoa não encontrada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Número do processo inválido")
    })
    public ResponseEntity<ApiResponse<Pessoa>> findByProcesso(@Parameter(description = "Número do processo") @PathVariable String processo) {
        log.info("Buscando pessoa por processo: {}", processo);

        return pessoaService.findByProcesso(processo)
                .map(pessoa -> ResponseEntity.ok(
                        ApiResponse.success("Pessoa encontrada com sucesso", pessoa)
                ))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ApiResponse.error("Pessoa não encontrada com processo: " + processo)
                ));
    }

    @PostMapping
    @Operation(summary = "Cadastrar nova pessoa", description = "Cadastra uma nova pessoa no sistema")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Pessoa cadastrada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflito - CPF ou RG já cadastrado")
    })
    public ResponseEntity<ApiResponse<Pessoa>> save(@Valid @RequestBody PessoaDTO dto) {
        log.info("Cadastrando nova pessoa - Processo: {}", dto.getProcesso());

        try {
            Pessoa pessoa = pessoaService.save(dto);
            log.info("Pessoa cadastrada com sucesso. ID: {}", pessoa.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.success("Pessoa cadastrada com sucesso", pessoa)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar pessoa", description = "Atualiza os dados de uma pessoa existente")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pessoa atualizada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pessoa não encontrada"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflito - CPF ou RG já cadastrado")
    })
    public ResponseEntity<ApiResponse<Pessoa>> update(@PathVariable Long id, @Valid @RequestBody PessoaDTO dto) {
        log.info("Atualizando pessoa ID: {}", id);

        try {
            Pessoa pessoa = pessoaService.update(id, dto);
            log.info("Pessoa atualizada com sucesso. ID: {}", pessoa.getId());
            return ResponseEntity.ok(
                    ApiResponse.success("Pessoa atualizada com sucesso", pessoa)
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
    @Operation(summary = "Excluir pessoa", description = "Remove uma pessoa do sistema")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pessoa excluída com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID inválido ou operação não permitida"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        log.info("Excluindo pessoa ID: {}", id);

        try {
            pessoaService.delete(id);
            log.info("Pessoa excluída com sucesso. ID: {}", id);
            return ResponseEntity.ok(
                    ApiResponse.success("Pessoa excluída com sucesso")
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
    @Operation(summary = "Buscar pessoas por status", description = "Retorna pessoas filtradas por status de comparecimento")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Status inválido")
    })
    public ResponseEntity<ApiResponse<List<Pessoa>>> findByStatus(@Parameter(description = "Status do comparecimento") @PathVariable StatusComparecimento status) {
        log.info("Buscando pessoas por status: {}", status);

        try {
            List<Pessoa> pessoas = pessoaService.findByStatus(status);
            return ResponseEntity.ok(
                    ApiResponse.success("Pessoas encontradas com sucesso", pessoas)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @GetMapping("/comparecimentos/hoje")
    @Operation(summary = "Comparecimentos de hoje", description = "Retorna pessoas que devem comparecer hoje")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso")
    public ResponseEntity<ApiResponse<List<Pessoa>>> findComparecimentosHoje() {
        log.info("Buscando pessoas com comparecimento hoje");
        List<Pessoa> pessoas = pessoaService.findComparecimentosHoje();
        return ResponseEntity.ok(
                ApiResponse.success("Pessoas com comparecimento hoje listadas com sucesso", pessoas)
        );
    }

    @GetMapping("/atrasados")
    @Operation(summary = "Pessoas em atraso", description = "Retorna pessoas que estão em atraso com o comparecimento")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso")
    public ResponseEntity<ApiResponse<List<Pessoa>>> findAtrasados() {
        log.info("Buscando pessoas em atraso");
        List<Pessoa> pessoas = pessoaService.findAtrasados();
        return ResponseEntity.ok(
                ApiResponse.success("Pessoas em atraso listadas com sucesso", pessoas)
        );
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar pessoas", description = "Busca pessoas por nome ou número do processo")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Termo de busca inválido")
    })
    public ResponseEntity<ApiResponse<List<Pessoa>>> buscar(@Parameter(description = "Termo de busca") @RequestParam String termo) {
        log.info("Buscando pessoas por termo: {}", termo);

        try {
            List<Pessoa> pessoas = pessoaService.buscarPorNomeOuProcesso(termo);
            return ResponseEntity.ok(
                    ApiResponse.success("Busca realizada com sucesso", pessoas)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
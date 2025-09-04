package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.PessoaDTO;
import br.jus.tjba.aclp.model.Pessoa;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.service.PessoaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
            @ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso")
    })
    public ResponseEntity<List<Pessoa>> findAll() {
        log.info("Listando todas as pessoas");
        List<Pessoa> pessoas = pessoaService.findAll();
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pessoa por ID", description = "Retorna uma pessoa específica pelo seu ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pessoa encontrada"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada"),
            @ApiResponse(responseCode = "400", description = "ID inválido")
    })
    public ResponseEntity<Pessoa> findById(@Parameter(description = "ID da pessoa") @PathVariable Long id) {
        log.info("Buscando pessoa por ID: {}", id);

        // O GlobalExceptionHandler cuidará dos erros de IllegalArgumentException
        return pessoaService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/processo/{processo}")
    @Operation(summary = "Buscar pessoa por processo", description = "Retorna uma pessoa pelo número do processo")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pessoa encontrada"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada"),
            @ApiResponse(responseCode = "400", description = "Número do processo inválido")
    })
    public ResponseEntity<Pessoa> findByProcesso(@Parameter(description = "Número do processo") @PathVariable String processo) {
        log.info("Buscando pessoa por processo: {}", processo);

        // O GlobalExceptionHandler cuidará dos erros de IllegalArgumentException
        return pessoaService.findByProcesso(processo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Cadastrar nova pessoa", description = "Cadastra uma nova pessoa no sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Pessoa cadastrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "409", description = "Conflito - CPF ou RG já cadastrado")
    })
    public ResponseEntity<Pessoa> save(@Valid @RequestBody PessoaDTO dto) {
        log.info("Cadastrando nova pessoa - Processo: {}", dto.getProcesso());

        // O GlobalExceptionHandler cuidará de todos os erros:
        // - MethodArgumentNotValidException (validações do @Valid)
        // - IllegalArgumentException (validações de negócio)
        // - DataIntegrityViolationException (duplicidades)
        Pessoa pessoa = pessoaService.save(dto);
        log.info("Pessoa cadastrada com sucesso. ID: {}", pessoa.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(pessoa);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar pessoa", description = "Atualiza os dados de uma pessoa existente")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pessoa atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada"),
            @ApiResponse(responseCode = "409", description = "Conflito - CPF ou RG já cadastrado")
    })
    public ResponseEntity<Pessoa> update(@PathVariable Long id, @Valid @RequestBody PessoaDTO dto) {
        log.info("Atualizando pessoa ID: {}", id);

        // O GlobalExceptionHandler cuidará dos erros:
        // - EntityNotFoundException (pessoa não encontrada)
        // - IllegalArgumentException (validações de negócio)
        // - MethodArgumentNotValidException (validações do @Valid)
        // - DataIntegrityViolationException (duplicidades)
        Pessoa pessoa = pessoaService.update(id, dto);
        log.info("Pessoa atualizada com sucesso. ID: {}", pessoa.getId());
        return ResponseEntity.ok(pessoa);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir pessoa", description = "Remove uma pessoa do sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Pessoa excluída com sucesso"),
            @ApiResponse(responseCode = "400", description = "ID inválido ou operação não permitida"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Excluindo pessoa ID: {}", id);

        // O GlobalExceptionHandler cuidará dos erros:
        // - EntityNotFoundException (pessoa não encontrada)
        // - IllegalArgumentException (operação não permitida)
        pessoaService.delete(id);
        log.info("Pessoa excluída com sucesso. ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Buscar pessoas por status", description = "Retorna pessoas filtradas por status de comparecimento")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Status inválido")
    })
    public ResponseEntity<List<Pessoa>> findByStatus(@Parameter(description = "Status do comparecimento") @PathVariable StatusComparecimento status) {
        log.info("Buscando pessoas por status: {}", status);

        // O GlobalExceptionHandler cuidará do IllegalArgumentException se status for null
        List<Pessoa> pessoas = pessoaService.findByStatus(status);
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/comparecimentos/hoje")
    @Operation(summary = "Comparecimentos de hoje", description = "Retorna pessoas que devem comparecer hoje")
    @ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso")
    public ResponseEntity<List<Pessoa>> findComparecimentosHoje() {
        log.info("Buscando pessoas com comparecimento hoje");
        List<Pessoa> pessoas = pessoaService.findComparecimentosHoje();
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/atrasados")
    @Operation(summary = "Pessoas em atraso", description = "Retorna pessoas que estão em atraso com o comparecimento")
    @ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso")
    public ResponseEntity<List<Pessoa>> findAtrasados() {
        log.info("Buscando pessoas em atraso");
        List<Pessoa> pessoas = pessoaService.findAtrasados();
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar pessoas", description = "Busca pessoas por nome ou número do processo")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Termo de busca inválido")
    })
    public ResponseEntity<List<Pessoa>> buscar(@Parameter(description = "Termo de busca") @RequestParam String termo) {
        log.info("Buscando pessoas por termo: {}", termo);

        // O GlobalExceptionHandler cuidará do IllegalArgumentException
        List<Pessoa> pessoas = pessoaService.buscarPorNomeOuProcesso(termo);
        return ResponseEntity.ok(pessoas);
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
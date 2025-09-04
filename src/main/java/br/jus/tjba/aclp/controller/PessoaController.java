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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pessoas")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Pessoas", description = "API para gerenciamento de pessoas em liberdade provisória")
public class PessoaController {

    private final PessoaService pessoaService;

    @GetMapping
    @Operation(summary = "Listar todas as pessoas", description = "Retorna uma lista com todas as pessoas cadastradas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de pessoas retornada com sucesso")
    })
    public ResponseEntity<List<Pessoa>> findAll() {
        List<Pessoa> pessoas = pessoaService.findAll();
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pessoa por ID", description = "Retorna uma pessoa específica pelo seu ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pessoa encontrada"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<Pessoa> findById(@Parameter(description = "ID da pessoa") @PathVariable Long id) {
        return pessoaService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/processo/{processo}")
    @Operation(summary = "Buscar pessoa por processo", description = "Retorna uma pessoa pelo número do processo")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pessoa encontrada"),
            @ApiResponse(responseCode = "404", description = "Pessoa não encontrada")
    })
    public ResponseEntity<Pessoa> findByProcesso(@Parameter(description = "Número do processo") @PathVariable String processo) {
        return pessoaService.findByProcesso(processo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Cadastrar nova pessoa", description = "Cadastra uma nova pessoa no sistema")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pessoa cadastrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<Pessoa> save(@Valid @RequestBody PessoaDTO dto) {
        try {
            Pessoa pessoa = pessoaService.save(dto);
            return ResponseEntity.ok(pessoa);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Buscar pessoas por status", description = "Retorna pessoas filtradas por status de comparecimento")
    public ResponseEntity<List<Pessoa>> findByStatus(@Parameter(description = "Status do comparecimento") @PathVariable StatusComparecimento status) {
        List<Pessoa> pessoas = pessoaService.findByStatus(status);
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/comparecimentos/hoje")
    @Operation(summary = "Comparecimentos de hoje", description = "Retorna pessoas que devem comparecer hoje")
    public ResponseEntity<List<Pessoa>> findComparecimentosHoje() {
        List<Pessoa> pessoas = pessoaService.findComparecimentosHoje();
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/atrasados")
    @Operation(summary = "Pessoas em atraso", description = "Retorna pessoas que estão em atraso com o comparecimento")
    public ResponseEntity<List<Pessoa>> findAtrasados() {
        List<Pessoa> pessoas = pessoaService.findAtrasados();
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar pessoas", description = "Busca pessoas por nome ou número do processo")
    public ResponseEntity<List<Pessoa>> buscar(@Parameter(description = "Termo de busca") @RequestParam String termo) {
        List<Pessoa> pessoas = pessoaService.buscarPorNomeOuProcesso(termo);
        return ResponseEntity.ok(pessoas);
    }
}
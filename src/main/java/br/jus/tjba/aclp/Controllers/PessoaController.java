package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.PessoaDTO;
import br.jus.tjba.aclp.model.Pessoa;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.service.PessoaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pessoas")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PessoaController {

    private final PessoaService pessoaService;

    @GetMapping
    public ResponseEntity<List<Pessoa>> findAll() {
        List<Pessoa> pessoas = pessoaService.findAll();
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pessoa> findById(@PathVariable Long id) {
        return pessoaService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/processo/{processo}")
    public ResponseEntity<Pessoa> findByProcesso(@PathVariable String processo) {
        return pessoaService.findByProcesso(processo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Pessoa> save(@Valid @RequestBody PessoaDTO dto) {
        try {
            Pessoa pessoa = pessoaService.save(dto);
            return ResponseEntity.ok(pessoa);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Pessoa>> findByStatus(@PathVariable StatusComparecimento status) {
        List<Pessoa> pessoas = pessoaService.findByStatus(status);
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/comparecimentos/hoje")
    public ResponseEntity<List<Pessoa>> findComparecimentosHoje() {
        List<Pessoa> pessoas = pessoaService.findComparecimentosHoje();
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/atrasados")
    public ResponseEntity<List<Pessoa>> findAtrasados() {
        List<Pessoa> pessoas = pessoaService.findAtrasados();
        return ResponseEntity.ok(pessoas);
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<Pessoa>> buscar(@RequestParam String termo) {
        List<Pessoa> pessoas = pessoaService.buscarPorNomeOuProcesso(termo);
        return ResponseEntity.ok(pessoas);
    }
}
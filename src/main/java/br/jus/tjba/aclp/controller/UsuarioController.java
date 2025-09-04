package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.UsuarioDTO;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600,
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Slf4j
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<Usuario>> findAll() {
        log.info("Buscando todos os usuários");
        List<Usuario> usuarios = usuarioService.findAll();
        return ResponseEntity.ok(usuarios);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> findById(@PathVariable Long id) {
        log.info("Buscando usuário por ID: {}", id);
        return usuarioService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Usuario> save(@Valid @RequestBody UsuarioDTO dto) {
        log.info("Cadastrando novo usuário: {}", dto.getEmail());

        // O GlobalExceptionHandler cuidará dos erros de validação
        Usuario usuario = usuarioService.save(dto);
        log.info("Usuário cadastrado com sucesso. ID: {}", usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Usuario> update(@PathVariable Long id, @Valid @RequestBody UsuarioDTO dto) {
        log.info("Atualizando usuário ID: {}", id);

        // O GlobalExceptionHandler cuidará dos erros de validação
        Usuario usuario = usuarioService.update(id, dto);
        log.info("Usuário atualizado com sucesso. ID: {}", usuario.getId());
        return ResponseEntity.ok(usuario);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Desativando usuário ID: {}", id);

        // O GlobalExceptionHandler cuidará dos erros
        usuarioService.delete(id);
        log.info("Usuário desativado com sucesso. ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tipo/{tipo}")
    public ResponseEntity<List<Usuario>> findByTipo(@PathVariable TipoUsuario tipo) {
        log.info("Buscando usuários por tipo: {}", tipo);
        List<Usuario> usuarios = usuarioService.findByTipo(tipo);
        return ResponseEntity.ok(usuarios);
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.UsuarioDTO;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<Usuario>> findAll() {
        List<Usuario> usuarios = usuarioService.findAll();
        return ResponseEntity.ok(usuarios);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> findById(@PathVariable Long id) {
        return usuarioService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Usuario> save(@Valid @RequestBody UsuarioDTO dto) {
        try {
            Usuario usuario = usuarioService.save(dto);
            return ResponseEntity.ok(usuario);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Usuario> update(@PathVariable Long id, @Valid @RequestBody UsuarioDTO dto) {
        try {
            Usuario usuario = usuarioService.update(id, dto);
            return ResponseEntity.ok(usuario);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            usuarioService.delete(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/tipo/{tipo}")
    public ResponseEntity<List<Usuario>> findByTipo(@PathVariable TipoUsuario tipo) {
        List<Usuario> usuarios = usuarioService.findByTipo(tipo);
        return ResponseEntity.ok(usuarios);
    }
}
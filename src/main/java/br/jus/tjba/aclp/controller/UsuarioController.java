package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
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
@Slf4j
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Usuario>>> findAll() {
        log.info("Buscando todos os usuários");
        List<Usuario> usuarios = usuarioService.findAll();
        return ResponseEntity.ok(
                ApiResponse.success("Usuários listados com sucesso", usuarios)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Usuario>> findById(@PathVariable Long id) {
        log.info("Buscando usuário por ID: {}", id);
        return usuarioService.findById(id)
                .map(usuario -> ResponseEntity.ok(
                        ApiResponse.success("Usuário encontrado com sucesso", usuario)
                ))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ApiResponse.error("Usuário não encontrado com ID: " + id)
                ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Usuario>> save(@Valid @RequestBody UsuarioDTO dto) {
        log.info("Cadastrando novo usuário: {}", dto.getEmail());

        try {
            Usuario usuario = usuarioService.save(dto);
            log.info("Usuário cadastrado com sucesso. ID: {}", usuario.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.success("Usuário cadastrado com sucesso", usuario)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Usuario>> update(@PathVariable Long id, @Valid @RequestBody UsuarioDTO dto) {
        log.info("Atualizando usuário ID: {}", id);

        try {
            Usuario usuario = usuarioService.update(id, dto);
            log.info("Usuário atualizado com sucesso. ID: {}", usuario.getId());
            return ResponseEntity.ok(
                    ApiResponse.success("Usuário atualizado com sucesso", usuario)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        log.info("Desativando usuário ID: {}", id);

        try {
            usuarioService.delete(id);
            log.info("Usuário desativado com sucesso. ID: {}", id);
            return ResponseEntity.ok(
                    ApiResponse.success("Usuário desativado com sucesso")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @GetMapping("/tipo/{tipo}")
    public ResponseEntity<ApiResponse<List<Usuario>>> findByTipo(@PathVariable TipoUsuario tipo) {
        log.info("Buscando usuários por tipo: {}", tipo);
        try {
            List<Usuario> usuarios = usuarioService.findByTipo(tipo);
            return ResponseEntity.ok(
                    ApiResponse.success("Usuários encontrados com sucesso", usuarios)
            );
        } catch (Exception e) {
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
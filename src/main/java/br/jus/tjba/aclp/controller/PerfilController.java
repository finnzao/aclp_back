package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.AlterarSenhaDTO;
import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.AtualizarPerfilDTO;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controller para gerenciamento do perfil do usuário autenticado
 */
@RestController
@RequestMapping("/api/auth/perfil")
@RequiredArgsConstructor
@Slf4j
public class PerfilController {

    private final UsuarioService usuarioService;

    /**
     * Retorna dados do perfil do usuário autenticado
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Usuario>> getPerfil(
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Buscando perfil do usuário: {}", userDetails.getUsername());

        return usuarioService.findByEmail(userDetails.getUsername())
                .map(usuario -> ResponseEntity.ok(
                        ApiResponse.success("Perfil carregado com sucesso", usuario)
                ))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Atualiza dados do perfil do usuário autenticado
     */
    @PutMapping
    public ResponseEntity<ApiResponse<Usuario>> atualizarPerfil(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AtualizarPerfilDTO dto) {

        log.info("Atualizando perfil do usuário: {}", userDetails.getUsername());

        try {
            Usuario usuario = usuarioService.atualizarPerfil(userDetails.getUsername(), dto);
            return ResponseEntity.ok(
                    ApiResponse.success("Perfil atualizado com sucesso", usuario)
            );
        } catch (IllegalArgumentException e) {
            log.error("Erro ao atualizar perfil: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    /**
     * Altera senha do usuário autenticado
     */
    @PutMapping("/senha")
    public ResponseEntity<ApiResponse<Void>> alterarSenha(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AlterarSenhaDTO dto) {

        log.info("Alterando senha do usuário: {}", userDetails.getUsername());

        try {
            usuarioService.alterarSenha(userDetails.getUsername(), dto);
            return ResponseEntity.ok(
                    ApiResponse.success("Senha alterada com sucesso")
            );
        } catch (IllegalArgumentException e) {
            log.error("Erro ao alterar senha: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    /**
     * Desativa conta do próprio usuário
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> desativarConta(
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Desativando conta do usuário: {}", userDetails.getUsername());

        try {
            usuarioService.desativarPropriaConta(userDetails.getUsername());
            return ResponseEntity.ok(
                    ApiResponse.success("Conta desativada com sucesso")
            );
        } catch (IllegalArgumentException e) {
            log.error("Erro ao desativar conta: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }
}
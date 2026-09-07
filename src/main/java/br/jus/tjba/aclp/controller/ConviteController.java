package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.ConviteDTO.*;
import br.jus.tjba.aclp.service.ConviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios/convites")
@RequiredArgsConstructor
@Tag(name = "Convites", description = "Gerenciamento de convites de usuários")
@Slf4j
public class ConviteController {

    private final ConviteService conviteService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Criar convite",
            description = "Cria novo convite para usuário (apenas ADMIN)")
    public ResponseEntity<ApiResponse<ConviteResponse>> criarConvite(
            @Valid @RequestBody CriarConviteRequest request,
            HttpServletRequest httpRequest) {

        log.info("Request para criar convite - Email: {}", request.getEmail());

        ConviteResponse convite = conviteService.criarConvite(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Convite criado com sucesso", convite));
    }

    @GetMapping("/validar/{token}")
    @Operation(summary = "Validar convite",
            description = "Valida se convite é válido (público)")
    public ResponseEntity<ApiResponse<ValidarConviteResponse>> validarConvite(
            @PathVariable String token) {

        log.info("Validando convite - Token: {}", token);

        ValidarConviteResponse response = conviteService.validarConvite(token);

        if (response.isValido()) {
            return ResponseEntity.ok(ApiResponse.success("Convite válido", response));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(response.getMensagem()));
        }
    }

    @PostMapping("/ativar")
    @Operation(summary = "Ativar convite",
            description = "Ativa convite criando usuário (público)")
    public ResponseEntity<ApiResponse<AtivarConviteResponse>> ativarConvite(
            @Valid @RequestBody AtivarConviteRequest request,
            HttpServletRequest httpRequest) {

        log.info("Ativando convite - Token: {}", request.getToken());

        AtivarConviteResponse response = conviteService.ativarConvite(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Listar convites",
            description = "Lista convites criados pelo usuário autenticado (apenas ADMIN)")
    public ResponseEntity<ApiResponse<List<ConviteListItem>>> listarConvites() {
        log.info("Listando convites do usuário autenticado");

        List<ConviteListItem> convites = conviteService.listarConvitesDoUsuarioAtual();
        return ResponseEntity.ok(
                ApiResponse.success("Convites listados com sucesso", convites));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Buscar convite",
            description = "Busca convite por ID (apenas ADMIN)")
    public ResponseEntity<ApiResponse<ConviteResponse>> buscarConvite(
            @PathVariable Long id) {

        log.info("Buscando convite - ID: {}", id);

        ConviteResponse convite = conviteService.buscarPorId(id);
        return ResponseEntity.ok(
                ApiResponse.success("Convite encontrado", convite));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Cancelar convite",
            description = "Cancela um convite (apenas ADMIN)")
    public ResponseEntity<ApiResponse<Void>> cancelarConvite(
            @PathVariable Long id) {

        log.info("Cancelando convite - ID: {}", id);

        conviteService.cancelarConvite(id);
        return ResponseEntity.ok(
                ApiResponse.success("Convite cancelado com sucesso"));
    }

    @PostMapping("/{id}/reenviar")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Reenviar convite",
            description = "Reenvia email de convite (apenas ADMIN)")
    public ResponseEntity<ApiResponse<Void>> reenviarConvite(
            @PathVariable Long id) {

        log.info("Reenviando convite - ID: {}", id);

        conviteService.reenviarConvite(id);
        return ResponseEntity.ok(
                ApiResponse.success("Convite reenviado com sucesso"));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Estatísticas",
            description = "Retorna estatísticas de convites (apenas ADMIN)")
    public ResponseEntity<ApiResponse<ConviteStats>> getEstatisticas() {
        log.info("Buscando estatísticas de convites");

        ConviteStats stats = conviteService.getEstatisticas();
        return ResponseEntity.ok(
                ApiResponse.success("Estatísticas obtidas", stats));
    }
}
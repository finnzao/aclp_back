package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.UserInviteDTO.*;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.service.UserInviteService;
import br.jus.tjba.aclp.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import java.util.Map;

/**
 * Controller para gerenciar convites de usuários
 * Implementa o fluxo de convite com link de primeiro acesso
 */
@RestController
@RequestMapping("/api/usuarios/convites")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Convites de Usuários", description = "Endpoints para gerenciar convites e primeiro acesso")
@Slf4j
public class UserInviteController {

    private final UserInviteService inviteService;
    private final AuthService authService; // Para obter usuário atual

    // ===== ENDPOINTS PROTEGIDOS (REQUER ADMIN) =====

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Criar novo convite",
            description = "Cria e envia convite por email para novo usuário (apenas ADMIN)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Convite criado e enviado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "403", description = "Sem permissão de admin"),
            @ApiResponse(responseCode = "409", description = "Email já cadastrado")
    })
    public ResponseEntity<?> criarConvite(
            @Valid @RequestBody CriarConviteDTO dto,
            HttpServletRequest request) {

        String clientIp = getClientIpAddress(request);
        Usuario adminAtual = authService.getUsuarioAtual(); // Implementar método

        log.info("Admin {} criando convite para {}", adminAtual.getEmail(), dto.getEmail());

        try {
            ConviteResponseDTO response = inviteService.criarConvite(dto, adminAtual, clientIp);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "success", true,
                            "message", "Convite enviado com sucesso",
                            "data", response
                    ));

        } catch (IllegalArgumentException e) {
            log.warn("Erro ao criar convite: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Listar convites",
            description = "Lista todos os convites enviados (apenas ADMIN)")
    @ApiResponse(responseCode = "200", description = "Lista de convites")
    public ResponseEntity<?> listarConvites(
            @Parameter(description = "Filtrar por status: PENDING, ACCEPTED, EXPIRED, CANCELLED")
            @RequestParam(required = false) String status) {

        log.info("Listando convites - Status: {}", status);

        List<ConviteListDTO> convites = inviteService.listarConvites(status);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", convites,
                "total", convites.size()
        ));
    }

    @PostMapping("/{id}/reenviar")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Reenviar convite",
            description = "Reenvia convite com novo token (apenas ADMIN)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Convite reenviado"),
            @ApiResponse(responseCode = "404", description = "Convite não encontrado"),
            @ApiResponse(responseCode = "400", description = "Convite já aceito")
    })
    public ResponseEntity<?> reenviarConvite(
            @PathVariable Long id,
            @Valid @RequestBody ReenviarConviteDTO dto,
            HttpServletRequest request) {

        Usuario adminAtual = authService.getUsuarioAtual();
        dto.setConviteId(id);

        log.info("Admin {} reenviando convite ID: {}", adminAtual.getEmail(), id);

        try {
            ConviteResponseDTO response = inviteService.reenviarConvite(dto, adminAtual);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Convite reenviado com sucesso",
                    "data", response
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Cancelar convite",
            description = "Cancela convite pendente (apenas ADMIN)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Convite cancelado"),
            @ApiResponse(responseCode = "404", description = "Convite não encontrado"),
            @ApiResponse(responseCode = "400", description = "Convite já aceito")
    })
    public ResponseEntity<?> cancelarConvite(
            @PathVariable Long id,
            @RequestBody(required = false) CancelarConviteDTO dto) {

        Usuario adminAtual = authService.getUsuarioAtual();

        if (dto == null) {
            dto = new CancelarConviteDTO();
        }
        dto.setConviteId(id);

        log.info("Admin {} cancelando convite ID: {}", adminAtual.getEmail(), id);

        try {
            inviteService.cancelarConvite(dto, adminAtual);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Convite cancelado com sucesso"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }

    // ===== ENDPOINTS PÚBLICOS (PRIMEIRO ACESSO) =====

    @GetMapping("/validar/{token}")
    @Operation(summary = "Validar token de convite",
            description = "Valida token e retorna informações do convite (público)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token validado"),
            @ApiResponse(responseCode = "400", description = "Token inválido ou expirado")
    })
    public ResponseEntity<?> validarToken(@PathVariable String token) {
        log.info("Validando token de convite");

        TokenInfoDTO info = inviteService.validarToken(token);

        if (info.isValido()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", info
            ));
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", info.getMessage(),
                            "status", info.getStatus()
                    ));
        }
    }

    @PostMapping("/ativar")
    @Operation(summary = "Ativar conta com convite",
            description = "Define senha e ativa conta usando token de convite (público)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Conta ativada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Token inválido ou dados incorretos"),
            @ApiResponse(responseCode = "409", description = "Email já cadastrado")
    })
    public ResponseEntity<?> ativarConvite(
            @Valid @RequestBody AtivarConviteDTO dto,
            HttpServletRequest request) {

        String clientIp = getClientIpAddress(request);
        log.info("Ativando convite - IP: {}", clientIp);

        try {
            Usuario usuario = inviteService.ativarConvite(dto, clientIp);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "success", true,
                            "message", "Conta ativada com sucesso! Você já pode fazer login.",
                            "data", Map.of(
                                    "id", usuario.getId(),
                                    "email", usuario.getEmail(),
                                    "nome", usuario.getNome(),
                                    "tipo", usuario.getTipo()
                            )
                    ));

        } catch (IllegalArgumentException e) {
            log.warn("Erro ao ativar convite: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }

    // ===== MÉTODOS AUXILIARES =====

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
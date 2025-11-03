package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.SetupAdminDTO;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.service.SetupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
@Tag(name = "Setup", description = "Endpoints para configuração inicial do sistema")
@Slf4j
public class SetupController {

    private final SetupService setupService;

    @GetMapping("/status")
    @Operation(summary = "Verificar status do setup",
            description = "Retorna informações sobre o status da configuração inicial")
    @ApiResponse(responseCode = "200", description = "Status retornado com sucesso")
    public ResponseEntity<Map<String, Object>> getSetupStatus() {
        log.debug("Verificando status do setup");

        Map<String, Object> status = setupService.getSetupStatus();
        return ResponseEntity.ok(status);
    }

    @PostMapping("/admin")
    @Operation(summary = "Criar primeiro administrador",
            description = "Cria o primeiro administrador do sistema e finaliza o setup inicial")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Administrador criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "409", description = "Setup já foi concluído"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<Map<String, Object>> createFirstAdmin(
            @Valid @RequestBody SetupAdminDTO dto,
            HttpServletRequest request) {

        String clientIp = getClientIpAddress(request);
        log.info("Solicitação de criação do primeiro admin - Email: {}, IP: {}", dto.getEmail(), clientIp);

        try {
            if (!setupService.isSetupRequired()) {
                log.warn("Tentativa de executar setup já concluído - IP: {}", clientIp);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(
                                "status", "error",
                                "message", "Setup já foi concluído",
                                "code", "SETUP_ALREADY_COMPLETED"
                        ));
            }

            Usuario admin = setupService.createFirstAdmin(dto, clientIp);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "status", "success",
                            "message", "Administrador criado com sucesso",
                            "adminId", admin.getId(),
                            "adminEmail", admin.getEmail(),
                            "adminNome", admin.getNome(),
                            "setupCompleted", true,
                            "nextStep", "/login"
                    ));

        } catch (IllegalStateException e) {
            log.warn("Setup já concluído - IP: {}, Erro: {}", clientIp, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "status", "error",
                            "message", e.getMessage(),
                            "code", "SETUP_ALREADY_COMPLETED"
                    ));

        } catch (IllegalArgumentException e) {
            log.warn("Dados inválidos no setup - IP: {}, Erro: {}", clientIp, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "status", "error",
                            "message", e.getMessage(),
                            "code", "INVALID_DATA"
                    ));

        } catch (Exception e) {
            log.error("Erro interno ao criar primeiro admin - IP: " + clientIp, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "Erro interno do servidor",
                            "code", "INTERNAL_ERROR"
                    ));
        }
    }

    @GetMapping("/audit")
    @Operation(summary = "Informações de auditoria do setup",
            description = "Retorna informações de auditoria sobre a configuração inicial")
    @ApiResponse(responseCode = "200", description = "Informações de auditoria retornadas")
    public ResponseEntity<Map<String, Object>> getSetupAuditInfo() {
        log.debug("Consultando informações de auditoria do setup");

        Map<String, Object> auditInfo = setupService.getSetupAuditInfo();
        return ResponseEntity.ok(auditInfo);
    }

    @GetMapping("/health")
    @Operation(summary = "Health check do setup", description = "Verifica se o módulo de setup está funcionando")
    @ApiResponse(responseCode = "200", description = "Setup module funcionando")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "module", "setup",
                "timestamp", System.currentTimeMillis(),
                "setupRequired", setupService.isSetupRequired()
        ));
    }

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(IllegalArgumentException e) {
        log.warn("Erro de validação no setup: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "status", "error",
                        "message", e.getMessage(),
                        "code", "VALIDATION_ERROR"
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleStateError(IllegalStateException e) {
        log.warn("Erro de estado no setup: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "status", "error",
                        "message", e.getMessage(),
                        "code", "INVALID_STATE"
                ));
    }

    /**
     * Endpoint de reset isolado - só existe com profile "dev".
     */
    @Profile("dev")
    @RestController
    @RequestMapping("/api/setup")
    @RequiredArgsConstructor
    @Slf4j
    static class SetupResetController {

        private final SetupService setupService;

        @Value("${aclp.setup.reset-token:#{T(java.util.UUID).randomUUID().toString()}}")
        private String resetToken;

        @PostMapping("/reset")
        @Operation(summary = "Resetar setup (desenvolvimento)",
                description = "Reseta o setup para estado inicial - APENAS PARA DESENVOLVIMENTO")
        @ApiResponse(responseCode = "200", description = "Setup resetado com sucesso")
        public ResponseEntity<Map<String, String>> resetSetup(
                @RequestParam(required = false) String confirmToken,
                HttpServletRequest request) {

            String clientIp = request.getRemoteAddr();

            if (!resetToken.equals(confirmToken)) {
                log.warn("Tentativa de reset com token inválido - IP: {}", clientIp);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "status", "error",
                                "message", "Token de confirmação inválido"
                        ));
            }

            log.warn("RESET DO SETUP EXECUTADO - IP: {}", clientIp);
            setupService.resetSetup();

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Setup resetado com sucesso",
                    "warning", "Sistema retornou ao estado inicial"
            ));
        }
    }
}
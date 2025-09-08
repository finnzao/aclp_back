package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.EmailVerificationDTO.*;
import br.jus.tjba.aclp.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller responsável pelos endpoints de verificação por email
 * Gerencia todo o fluxo de verificação antes da criação de usuários
 */
@RestController
@RequestMapping("/api/verificacao")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Verificação de Email", description = "Endpoints para verificação de email antes do cadastro de usuários")
@Slf4j
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/solicitar-codigo")
    @Operation(summary = "Solicitar código de verificação",
            description = "Envia código de 6 dígitos para o email informado. Para ADMIN, email deve ser @tjba.jus.br")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Código enviado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou email já cadastrado"),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas - rate limiting ativo"),
            @ApiResponse(responseCode = "500", description = "Erro ao enviar email")
    })
    public ResponseEntity<?> solicitarCodigo(
            @Valid @RequestBody SolicitarCodigoDTO dto,
            HttpServletRequest request) {

        String clientIp = getClientIpAddress(request);
        log.info("Solicitação de código de verificação - Email: {}, Tipo: {}, IP: {}",
                dto.getEmail(), dto.getTipoUsuario(), clientIp);

        try {
            SolicitarCodigoResponseDTO response = emailVerificationService.solicitarCodigo(dto, clientIp);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Erro de validação ao solicitar código - Email: {}, Erro: {}, IP: {}",
                    dto.getEmail(), e.getMessage(), clientIp);

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "status", "error",
                            "message", e.getMessage(),
                            "code", determinarCodigoErro(e.getMessage())
                    ));

        } catch (Exception e) {
            log.error("Erro interno ao solicitar código - Email: " + dto.getEmail() + ", IP: " + clientIp, e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "Erro interno do servidor. Tente novamente.",
                            "code", "INTERNAL_ERROR"
                    ));
        }
    }

    @PostMapping("/verificar-codigo")
    @Operation(summary = "Verificar código de segurança",
            description = "Valida o código de 6 dígitos recebido por email e retorna token para criação do usuário")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Código verificado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Código inválido ou expirado"),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas incorretas")
    })
    public ResponseEntity<?> verificarCodigo(
            @Valid @RequestBody VerificarCodigoDTO dto,
            HttpServletRequest request) {

        String clientIp = getClientIpAddress(request);
        log.info("Verificação de código - Email: {}, IP: {}", dto.getEmail(), clientIp);

        try {
            VerificarCodigoResponseDTO response = emailVerificationService.verificarCodigo(dto, clientIp);

            if (response.getVerificado()) {
                log.info("Código verificado com sucesso - Email: {}, IP: {}", dto.getEmail(), clientIp);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Erro interno ao verificar código - Email: " + dto.getEmail() + ", IP: " + clientIp, e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "Erro interno do servidor",
                            "code", "INTERNAL_ERROR"
                    ));
        }
    }

    @GetMapping("/status")
    @Operation(summary = "Consultar status de verificação",
            description = "Consulta o status atual de verificação de um email sem fazer nova verificação")
    @ApiResponse(responseCode = "200", description = "Status retornado com sucesso")
    public ResponseEntity<StatusVerificacaoDTO> consultarStatus(
            @Parameter(description = "Email para consultar status")
            @RequestParam String email) {

        log.debug("Consultando status de verificação - Email: {}", email);

        try {
            StatusVerificacaoDTO status = emailVerificationService.consultarStatus(email);
            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Erro ao consultar status - Email: " + email, e);

            StatusVerificacaoDTO errorStatus = StatusVerificacaoDTO.builder()
                    .email(email)
                    .possuiCodigoAtivo(false)
                    .verificado(false)
                    .podeReenviar(true)
                    .build();

            return ResponseEntity.ok(errorStatus);
        }
    }

    @PostMapping("/reenviar-codigo")
    @Operation(summary = "Reenviar código de verificação",
            description = "Invalida código anterior e envia novo código para o email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Novo código enviado"),
            @ApiResponse(responseCode = "400", description = "Email inválido"),
            @ApiResponse(responseCode = "429", description = "Rate limiting ativo")
    })
    public ResponseEntity<?> reenviarCodigo(
            @Valid @RequestBody ReenviarCodigoDTO dto,
            HttpServletRequest request) {

        String clientIp = getClientIpAddress(request);
        log.info("Reenvio de código solicitado - Email: {}, IP: {}", dto.getEmail(), clientIp);

        try {
            SolicitarCodigoResponseDTO response = emailVerificationService.reenviarCodigo(dto, clientIp);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Erro ao reenviar código - Email: {}, Erro: {}, IP: {}",
                    dto.getEmail(), e.getMessage(), clientIp);

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "status", "error",
                            "message", e.getMessage(),
                            "code", determinarCodigoErro(e.getMessage())
                    ));

        } catch (Exception e) {
            log.error("Erro interno ao reenviar código - Email: " + dto.getEmail() + ", IP: " + clientIp, e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", "Erro interno do servidor",
                            "code", "INTERNAL_ERROR"
                    ));
        }
    }

    @PostMapping("/validar-token")
    @Operation(summary = "Validar token de verificação",
            description = "Valida token gerado após verificação do código - usado internamente pelo sistema")
    @ApiResponse(responseCode = "200", description = "Token validado")
    public ResponseEntity<Map<String, Object>> validarToken(
            @RequestParam String email,
            @RequestParam String token,
            HttpServletRequest request) {

        String clientIp = getClientIpAddress(request);
        log.debug("Validação de token - Email: {}, IP: {}", email, clientIp);

        try {
            boolean tokenValido = emailVerificationService.validarTokenVerificacao(email, token);

            return ResponseEntity.ok(Map.of(
                    "status", tokenValido ? "success" : "error",
                    "valid", tokenValido,
                    "message", tokenValido ? "Token válido" : "Token inválido ou expirado"
            ));

        } catch (Exception e) {
            log.error("Erro ao validar token - Email: " + email + ", IP: " + clientIp, e);

            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "valid", false,
                    "message", "Erro ao validar token"
            ));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Health check do módulo de verificação",
            description = "Verifica se o módulo de verificação está funcionando")
    @ApiResponse(responseCode = "200", description = "Módulo funcionando")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "module", "email-verification",
                "timestamp", System.currentTimeMillis(),
                "features", Map.of(
                        "codeGeneration", true,
                        "emailValidation", true,
                        "rateLimiting", true,
                        "tokenGeneration", true
                )
        ));
    }

    // ========== MÉTODOS UTILITÁRIOS ==========

    /**
     * Extrai endereço IP do cliente considerando proxies
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Pegar o primeiro IP da lista (cliente original)
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        String xOriginalForwardedFor = request.getHeader("X-Original-Forwarded-For");
        if (xOriginalForwardedFor != null && !xOriginalForwardedFor.isEmpty()) {
            return xOriginalForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * Determina código de erro baseado na mensagem
     */
    private String determinarCodigoErro(String mensagem) {
        if (mensagem.contains("institucional") || mensagem.contains("@tjba.jus.br")) {
            return "EMAIL_NAO_INSTITUCIONAL";
        }
        if (mensagem.contains("já está cadastrado")) {
            return "EMAIL_JA_CADASTRADO";
        }
        if (mensagem.contains("Muitas solicitações")) {
            return "RATE_LIMIT_EXCEEDED";
        }
        if (mensagem.contains("inválido")) {
            return "INVALID_DATA";
        }
        return "VALIDATION_ERROR";
    }

    /**
     * Handler global para erros de validação
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(IllegalArgumentException e, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        log.warn("Erro de validação na verificação de email - Erro: {}, IP: {}", e.getMessage(), clientIp);

        return ResponseEntity.badRequest()
                .body(Map.of(
                        "status", "error",
                        "message", e.getMessage(),
                        "code", determinarCodigoErro(e.getMessage())
                ));
    }

    /**
     * Handler para erros gerais
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralError(Exception e, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        log.error("Erro geral na verificação de email - IP: " + clientIp, e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "status", "error",
                        "message", "Erro interno do servidor",
                        "code", "INTERNAL_ERROR"
                ));
    }
}
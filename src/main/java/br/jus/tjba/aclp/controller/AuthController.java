package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.AuthDTO.*;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller responsável pela autenticação e autorização
 * Gerencia login, logout, refresh token, recuperação de senha, etc.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Autenticação", description = "Endpoints de autenticação e autorização")
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Realiza o login do usuário
     */
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Realiza autenticação do usuário e retorna tokens JWT")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login realizado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas"),
            @ApiResponse(responseCode = "403", description = "Conta bloqueada ou desativada"),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas - tente novamente mais tarde")
    })
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletRequest httpRequest) {

        log.info("Tentativa de login - Email: {}", request.getEmail());

        try {
            LoginResponseDTO response = authService.login(request, httpRequest);

            if (response.isRequiresMfa()) {
                // Retornar parcial se MFA necessário
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "requiresMfa", true,
                        "message", "Código de autenticação necessário"
                ));
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro no login - Email: {}, Erro: {}", request.getEmail(), e.getMessage());

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Realiza logout do usuário
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalida tokens e encerra sessão do usuário")
    @SecurityRequirement(name = "bearer-auth")
    @ApiResponse(responseCode = "200", description = "Logout realizado com sucesso")
    public ResponseEntity<?> logout(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {

        try {
            String token = extractTokenFromHeader(authHeader);
            authService.logout(token, request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Logout realizado com sucesso"
            ));

        } catch (Exception e) {
            log.error("Erro no logout", e);
            // Logout sempre retorna sucesso para o cliente
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Logout realizado"
            ));
        }
    }

    /**
     * Renova o token de acesso
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh Token",
            description = "Renova o token de acesso usando refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token renovado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido ou expirado")
    })
    public ResponseEntity<?> refresh(
            @Valid @RequestBody RefreshTokenRequestDTO request,
            HttpServletRequest httpRequest) {

        log.debug("Renovação de token solicitada");

        try {
            RefreshTokenResponseDTO response = authService.refreshToken(request, httpRequest);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao renovar token: {}", e.getMessage());

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Valida token JWT
     */
    @GetMapping("/validate")
    @Operation(summary = "Validar Token", description = "Valida se token JWT está válido")
    @SecurityRequirement(name = "bearer-auth")
    @ApiResponse(responseCode = "200", description = "Informações do token")
    public ResponseEntity<?> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        try {
            String token = extractTokenFromHeader(authHeader);
            TokenValidationResponseDTO response = authService.validateToken(token);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.ok(
                    TokenValidationResponseDTO.builder()
                            .valid(false)
                            .message("Token inválido")
                            .build()
            );
        }
    }

    /**
     * Solicita recuperação de senha
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Esqueci a senha",
            description = "Envia email com link para recuperação de senha")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email enviado (sempre retorna sucesso)"),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas")
    })
    public ResponseEntity<?> forgotPassword(
            @Valid @RequestBody PasswordResetRequestDTO request) {

        log.info("Recuperação de senha solicitada - Email: {}", request.getEmail());

        try {
            authService.requestPasswordReset(request);

            // Sempre retornar sucesso para não revelar se email existe
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Se o email estiver cadastrado, você receberá instruções para recuperação"
            ));

        } catch (Exception e) {
            log.error("Erro na recuperação de senha", e);

            // Mesmo em erro, retornar mensagem genérica
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Se o email estiver cadastrado, você receberá instruções para recuperação"
            ));
        }
    }

    /**
     * Reseta a senha usando token
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Resetar senha",
            description = "Define nova senha usando token de recuperação")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Senha alterada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Token inválido ou expirado")
    })
    public ResponseEntity<?> resetPassword(
            @Valid @RequestBody PasswordResetConfirmDTO request) {

        log.info("Reset de senha com token");

        try {
            // Validar se senhas coincidem
            if (!request.senhasCoincidentes()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "success", false,
                                "message", "As senhas não coincidem"
                        ));
            }

            authService.resetPassword(request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Senha alterada com sucesso! Faça login com a nova senha."
            ));

        } catch (Exception e) {
            log.error("Erro ao resetar senha: {}", e.getMessage());

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Altera senha do usuário autenticado
     */
    @PostMapping("/change-password")
    @Operation(summary = "Alterar senha",
            description = "Altera senha do usuario autenticado (requer senha atual)")
    @SecurityRequirement(name = "bearer-auth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Senha alterada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Senha atual incorreta ou nova senha invalida"),
            @ApiResponse(responseCode = "401", description = "Nao autenticado")
    })
    public ResponseEntity<?> changePassword(
            @Valid @RequestBody ChangePasswordDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "success", false,
                            "message", "Usuario nao autenticado"
                    ));
        }

        String userEmail = userDetails.getUsername();
        log.info("Alteracao de senha solicitada - Usuario: {}", userEmail);

        try {
            if (!request.senhasCoincidentes()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "success", false,
                                "message", "As senhas nao coincidem"
                        ));
            }

            authService.changePassword(request, userEmail);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Senha alterada com sucesso!"
            ));

        } catch (Exception e) {
            log.error("Erro ao alterar senha: {}", e.getMessage());

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Retorna informacoes do usuario autenticado
     */
    @GetMapping("/me")
    @Operation(summary = "Dados do usuario",
            description = "Retorna informacoes do usuario autenticado")
    @SecurityRequirement(name = "bearer-auth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dados do usuario"),
            @ApiResponse(responseCode = "401", description = "Nao autenticado")
    })
    public ResponseEntity<?> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            log.warn("Tentativa de acesso ao /me sem autenticacao");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "success", false,
                            "message", "Usuario nao autenticado"
                    ));
        }

        try {
            String email = userDetails.getUsername();
            var usuario = authService.getUsuarioAtual();

            if (usuario == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "success", false,
                                "message", "Usuario nao encontrado"
                        ));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of(
                            "id", usuario.getId(),
                            "nome", usuario.getNome(),
                            "email", usuario.getEmail(),
                            "tipo", usuario.getTipo(),
                            "departamento", usuario.getDepartamento() != null ? usuario.getDepartamento() : "",
                            "telefone", usuario.getTelefone() != null ? usuario.getTelefone() : "",
                            "ultimoLogin", usuario.getUltimoLogin() != null ? usuario.getUltimoLogin() : "",
                            "isAdmin", usuario.isAdmin()
                    )
            ));

        } catch (Exception e) {
            log.error("Erro ao obter usuario atual", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Erro ao obter dados do usuario"
                    ));
        }
    }
    /**
     * Retorna informações da sessão atual
     */
    @GetMapping("/session")
    @Operation(summary = "Informações da sessão",
            description = "Retorna detalhes da sessão atual")
    @SecurityRequirement(name = "bearer-auth")
    @ApiResponse(responseCode = "200", description = "Informações da sessão")
    public ResponseEntity<?> getSessionInfo(
            @RequestHeader("Authorization") String authHeader) {

        try {
            String token = extractTokenFromHeader(authHeader);
            SessionInfoDTO session = authService.getCurrentSessionInfo(token);

            if (session == null) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Sessão não encontrada"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", session
            ));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Erro ao obter informações da sessão"
            ));
        }
    }

    /**
     * Lista todas as sessões do usuário
     */
    @GetMapping("/sessions")
    @Operation(summary = "Listar sessoes",
            description = "Lista todas as sessoes ativas do usuario")
    @SecurityRequirement(name = "bearer-auth")
    @ApiResponse(responseCode = "200", description = "Lista de sessoes")
    public ResponseEntity<?> getUserSessions(
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "success", false,
                            "message", "Usuario nao autenticado"
                    ));
        }

        try {
            String email = userDetails.getUsername();
            List<SessionInfoDTO> sessions = authService.getUserSessions(email);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", sessions,
                    "total", sessions.size()
            ));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Erro ao listar sessoes"
            ));
        }
    }

    /**
     * Invalida uma sessão específica
     */
    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Invalidar sessao",
            description = "Invalida uma sessao especifica")
    @SecurityRequirement(name = "bearer-auth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sessao invalidada"),
            @ApiResponse(responseCode = "403", description = "Sem permissao para invalidar esta sessao")
    })
    public ResponseEntity<?> invalidateSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "success", false,
                            "message", "Usuario nao autenticado"
                    ));
        }

        try {
            String email = userDetails.getUsername();
            authService.invalidateSession(sessionId, email);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Sessao invalidada com sucesso"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "success", false,
                            "message", e.getMessage()
                    ));
        }
    }


    /**
     * Health check do módulo de autenticação
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Verifica se o módulo está funcionando")
    @ApiResponse(responseCode = "200", description = "Módulo funcionando")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "module", "authentication",
                "timestamp", System.currentTimeMillis(),
                "features", Map.of(
                        "login", true,
                        "logout", true,
                        "refreshToken", true,
                        "passwordReset", true,
                        "mfa", false,
                        "sessions", true
                )
        ));
    }

    /**
     * Verifica se sistema requer setup inicial
     */
    @GetMapping("/check-setup")
    @Operation(summary = "Verificar setup",
            description = "Verifica se o sistema precisa de configuração inicial")
    @ApiResponse(responseCode = "200", description = "Status do setup")
    public ResponseEntity<?> checkSetup() {
        // Este endpoint sempre é público
        // O SetupInterceptor irá redirecionar se necessário
        return ResponseEntity.ok(Map.of(
                "setupRequired", false,
                "message", "Sistema configurado"
        ));
    }

    // ========== MÉTODOS AUXILIARES ==========

    /**
     * Extrai token do header Authorization
     */
    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Token não encontrado no header");
    }

    /**
     * Handler para erros de validação
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleValidationError(IllegalArgumentException e) {
        log.warn("Erro de validação: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "success", false,
                        "message", e.getMessage()
                ));
    }
}
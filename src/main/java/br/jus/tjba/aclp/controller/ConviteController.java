package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.ConviteDTO.*;
import br.jus.tjba.aclp.service.ConviteService;
import br.jus.tjba.aclp.service.EmailVerificationService;
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

/**
 * Controller para gerenciamento de convites
 */
@RestController
@RequestMapping("/api/usuarios/convites")
@RequiredArgsConstructor
@Tag(name = "Convites", description = "Gerenciamento de convites de usuários")
@Slf4j
public class ConviteController {

    private final ConviteService conviteService;

    /**
     * ADMIN: Criar novo convite
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Criar convite",
            description = "Cria novo convite para usuário (apenas ADMIN)")
    public ResponseEntity<ApiResponse<ConviteResponse>> criarConvite(
            @Valid @RequestBody CriarConviteRequest request,
            HttpServletRequest httpRequest) {

        log.info("Request para criar convite - Email: {}", request.getEmail());

        try {
            ConviteResponse convite = conviteService.criarConvite(request, httpRequest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Convite criado com sucesso", convite));
        } catch (IllegalArgumentException e) {
            log.warn("Erro ao criar convite: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro inesperado ao criar convite", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao criar convite. Tente novamente."));
        }
    }

    /**
     * PÚBLICO: Validar convite pelo token
     */
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

    /**
     * PÚBLICO: Ativar convite (criar usuário)
     */
    @PostMapping("/ativar")
    @Operation(summary = "Ativar convite",
            description = "Ativa convite criando usuário (público)")
    public ResponseEntity<ApiResponse<AtivarConviteResponse>> ativarConvite(
            @Valid @RequestBody AtivarConviteRequest request,
            HttpServletRequest httpRequest) {

        log.info("Ativando convite - Token: {}", request.getToken());

        try {
            AtivarConviteResponse response = conviteService.ativarConvite(request, httpRequest);
            return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
        } catch (IllegalArgumentException e) {
            log.warn("Erro ao ativar convite: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro inesperado ao ativar convite", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao ativar convite. Tente novamente."));
        }
    }

    /**
     * ADMIN: Listar convites do usuário atual
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Listar convites",
            description = "Lista convites criados pelo usuário autenticado (apenas ADMIN)")
    public ResponseEntity<ApiResponse<List<ConviteListItem>>> listarConvites() {
        log.info("Listando convites do usuário autenticado");

        try {
            List<ConviteListItem> convites = conviteService.listarConvitesDoUsuarioAtual();
            return ResponseEntity.ok(
                    ApiResponse.success("Convites listados com sucesso", convites));
        } catch (Exception e) {
            log.error("Erro ao listar convites", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao listar convites"));
        }
    }

    /**
     * ADMIN: Buscar convite por ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Buscar convite",
            description = "Busca convite por ID (apenas ADMIN)")
    public ResponseEntity<ApiResponse<ConviteResponse>> buscarConvite(
            @PathVariable Long id) {

        log.info("Buscando convite - ID: {}", id);

        try {
            ConviteResponse convite = conviteService.buscarPorId(id);
            return ResponseEntity.ok(
                    ApiResponse.success("Convite encontrado", convite));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * ADMIN: Cancelar convite
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Cancelar convite",
            description = "Cancela um convite (apenas ADMIN)")
    public ResponseEntity<ApiResponse<Void>> cancelarConvite(
            @PathVariable Long id) {

        log.info("Cancelando convite - ID: {}", id);

        try {
            conviteService.cancelarConvite(id);
            return ResponseEntity.ok(
                    ApiResponse.success("Convite cancelado com sucesso"));
        } catch (IllegalArgumentException e) {
            log.warn("Erro ao cancelar convite: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * ADMIN: Reenviar convite
     */
    @PostMapping("/{id}/reenviar")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Reenviar convite",
            description = "Reenvia email de convite (apenas ADMIN)")
    public ResponseEntity<ApiResponse<Void>> reenviarConvite(
            @PathVariable Long id) {

        log.info("Reenviando convite - ID: {}", id);

        try {
            conviteService.reenviarConvite(id);
            return ResponseEntity.ok(
                    ApiResponse.success("Convite reenviado com sucesso"));
        } catch (IllegalArgumentException e) {
            log.warn("Erro ao reenviar convite: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao reenviar convite", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao enviar email"));
        }
    }

    /**
     * ADMIN: Estatísticas de convites
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Estatísticas",
            description = "Retorna estatísticas de convites (apenas ADMIN)")
    public ResponseEntity<ApiResponse<ConviteStats>> getEstatisticas() {
        log.info("Buscando estatísticas de convites");

        try {
            ConviteStats stats = conviteService.getEstatisticas();
            return ResponseEntity.ok(
                    ApiResponse.success("Estatísticas obtidas", stats));
        } catch (Exception e) {
            log.error("Erro ao buscar estatísticas", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao buscar estatísticas"));
        }
    }

    private final EmailVerificationService emailVerificationService;

    /**
     * ADMIN: Gerar link de convite (sem email específico)
     */
    @PostMapping("/gerar-link")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-auth")
    @Operation(summary = "Gerar link de convite",
            description = "Gera link de convite reutilizável (apenas ADMIN)")
    public ResponseEntity<ApiResponse<LinkConviteResponse>> gerarLinkConvite(
            @Valid @RequestBody GerarLinkConviteRequest request,
            HttpServletRequest httpRequest) {

        log.info("Gerando link de convite - Tipo: {}", request.getTipoUsuario());

        try {
            // Este método precisaria ser implementado no ConviteService
            LinkConviteResponse response = conviteService.gerarLinkConvite(request, httpRequest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Link de convite gerado com sucesso", response));
        } catch (Exception e) {
            log.error("Erro ao gerar link de convite", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao gerar link"));
        }
    }

    /**
     * PÚBLICO: Criar pré-cadastro (primeira etapa)
     */
    @PostMapping("/pre-cadastro")
    @Operation(summary = "Criar pré-cadastro",
            description = "Cria pré-cadastro e envia email de verificação (público)")
    public ResponseEntity<ApiResponse<PreCadastroResponse>> criarPreCadastro(
            @Valid @RequestBody PreCadastroRequest request,
            HttpServletRequest httpRequest) {

        log.info("Criando pré-cadastro - Email: {}", request.getEmail());

        try {
            PreCadastroResponse response = emailVerificationService.criarPreCadastro(request, httpRequest);
            return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
        } catch (IllegalArgumentException e) {
            log.warn("Erro ao criar pré-cadastro: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro inesperado ao criar pré-cadastro", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao processar cadastro"));
        }
    }

    /**
     * PÚBLICO: Verificar email (segunda etapa)
     */
    @PostMapping("/verificar-email")
    @Operation(summary = "Verificar email",
            description = "Verifica email e ativa conta (público)")
    public ResponseEntity<ApiResponse<VerificarEmailResponse>> verificarEmail(
            @Valid @RequestBody VerificarEmailRequest request,
            HttpServletRequest httpRequest) {

        log.info("Verificando email - Token: {}", request.getToken());

        try {
            VerificarEmailResponse response = emailVerificationService.verificarEmail(
                    request.getToken(), httpRequest);
            return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
        } catch (IllegalArgumentException e) {
            log.warn("Erro ao verificar email: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro inesperado ao verificar email", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao verificar email"));
        }
    }

    /**
     * PÚBLICO: Verificar email via GET (para links de email)
     */
    @GetMapping("/verificar-email/{token}")
    @Operation(summary = "Verificar email via link",
            description = "Verifica email através do link enviado (público)")
    public ResponseEntity<ApiResponse<VerificarEmailResponse>> verificarEmailViaLink(
            @PathVariable String token,
            HttpServletRequest httpRequest) {

        log.info("Verificando email via link - Token: {}", token);

        try {
            VerificarEmailResponse response = emailVerificationService.verificarEmail(token, httpRequest);
            return ResponseEntity.ok(ApiResponse.success(response.getMessage(), response));
        } catch (IllegalArgumentException e) {
            log.warn("Erro ao verificar email: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * PÚBLICO: Reenviar email de verificação
     */
    @PostMapping("/reenviar-verificacao")
    @Operation(summary = "Reenviar email de verificação",
            description = "Reenvia email de verificação para pré-cadastro (público)")
    public ResponseEntity<ApiResponse<Void>> reenviarVerificacao(
            @RequestParam String email) {

        log.info("Reenviando verificação - Email: {}", email);

        try {
            emailVerificationService.reenviarEmailVerificacao(email);
            return ResponseEntity.ok(
                    ApiResponse.success("Email de verificação reenviado"));
        } catch (IllegalArgumentException e) {
            log.warn("Erro ao reenviar verificação: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
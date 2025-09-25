package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.UserInviteDTO.*;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.service.AuthService;
import br.jus.tjba.aclp.service.UserInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller de demonstração para testar o fluxo de convites
 * APENAS PARA DESENVOLVIMENTO - REMOVER EM PRODUÇÃO
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Demo", description = "Endpoints de demonstração (desenvolvimento)")
@Slf4j
public class DemoController {

    private final UserInviteService inviteService;
    private final AuthService authService;

    @PostMapping("/criar-convite-teste")
    @Operation(summary = "Criar convite de teste",
            description = "Cria um convite de teste usando admin mock (desenvolvimento)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Convite criado"),
            @ApiResponse(responseCode = "400", description = "Erro ao criar convite")
    })
    public ResponseEntity<?> criarConviteTeste(HttpServletRequest request) {
        log.warn("🧪 ENDPOINT DE TESTE - Criando convite de demonstração");

        try {
            // Usar admin mock para teste
            Usuario adminMock = authService.getMockAdminUser();

            // Criar DTO de convite de teste
            CriarConviteDTO dto = CriarConviteDTO.builder()
                    .nome("Usuário Teste")
                    .email("teste@example.com")
                    .tipoUsuario(TipoUsuario.USUARIO)
                    .departamento("Desenvolvimento")
                    .telefone("(71) 99999-9999")
                    .escopo("Teste")
                    .validadeHoras(72)
                    .mensagemPersonalizada("Este é um convite de teste do sistema")
                    .build();

            // Criar convite
            ConviteResponseDTO response = inviteService.criarConvite(dto, adminMock, getClientIp(request));

            // Adicionar informações extras para teste
            Map<String, Object> result = Map.of(
                    "success", true,
                    "message", "Convite de teste criado com sucesso",
                    "data", response,
                    "info", Map.of(
                            "modo", "DESENVOLVIMENTO",
                            "adminUsado", adminMock.getEmail(),
                            "linkAtivacao", response.getLinkAtivacao(),
                            "token", response.getToken(),
                            "nota", "Em produção, o email seria enviado automaticamente"
                    )
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Erro ao criar convite de teste", e);
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", "Erro ao criar convite de teste: " + e.getMessage()
                    ));
        }
    }

    @GetMapping("/info-sistema")
    @Operation(summary = "Informações do sistema de convites",
            description = "Retorna informações sobre o sistema de convites")
    @ApiResponse(responseCode = "200", description = "Informações retornadas")
    public ResponseEntity<?> infoSistema() {
        return ResponseEntity.ok(Map.of(
                "sistema", "ACLP - Sistema de Convites",
                "versao", "2.0.0",
                "modo", "DESENVOLVIMENTO",
                "fluxo", "Convite com link de primeiro acesso",
                "etapas", Map.of(
                        "1", "Admin cria convite via API",
                        "2", "Sistema envia email com link único",
                        "3", "Usuário acessa link e valida token",
                        "4", "Usuário define sua senha",
                        "5", "Conta é ativada e token invalidado"
                ),
                "endpoints", Map.of(
                        "criarConvite", "POST /api/usuarios/convites",
                        "validarToken", "GET /api/usuarios/convites/validar/{token}",
                        "ativarConta", "POST /api/usuarios/convites/ativar",
                        "listarConvites", "GET /api/usuarios/convites",
                        "reenviarConvite", "POST /api/usuarios/convites/{id}/reenviar",
                        "cancelarConvite", "DELETE /api/usuarios/convites/{id}"
                ),
                "testeRapido", Map.of(
                        "criarConviteTeste", "POST /api/demo/criar-convite-teste",
                        "simularAtivacao", "POST /api/demo/simular-ativacao/{token}"
                )
        ));
    }

    @PostMapping("/simular-ativacao/{token}")
    @Operation(summary = "Simular ativação de conta",
            description = "Simula o processo de ativação de conta (desenvolvimento)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Simulação executada"),
            @ApiResponse(responseCode = "400", description = "Token inválido")
    })
    public ResponseEntity<?> simularAtivacao(
            @PathVariable String token,
            @RequestParam(defaultValue = "Senha@123") String senha,
            HttpServletRequest request) {

        log.warn("🧪 ENDPOINT DE TESTE - Simulando ativação de conta");

        try {
            // Primeiro validar o token
            TokenInfoDTO tokenInfo = inviteService.validarToken(token);

            if (!tokenInfo.isValido()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "success", false,
                                "message", "Token inválido: " + tokenInfo.getMessage(),
                                "tokenInfo", tokenInfo
                        ));
            }

            // Criar DTO de ativação
            AtivarConviteDTO dto = AtivarConviteDTO.builder()
                    .token(token)
                    .senha(senha)
                    .confirmaSenha(senha)
                    .habilitarMFA(false)
                    .build();

            // Ativar conta
            Usuario usuarioCriado = inviteService.ativarConvite(dto, getClientIp(request));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Conta ativada com sucesso (simulação)",
                    "usuario", Map.of(
                            "id", usuarioCriado.getId(),
                            "nome", usuarioCriado.getNome(),
                            "email", usuarioCriado.getEmail(),
                            "tipo", usuarioCriado.getTipo(),
                            "status", usuarioCriado.getStatusUsuario()
                    ),
                    "proximoPasso", "Fazer login com email e senha definida"
            ));

        } catch (Exception e) {
            log.error("Erro ao simular ativação", e);
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "success", false,
                            "message", "Erro ao simular ativação: " + e.getMessage()
                    ));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
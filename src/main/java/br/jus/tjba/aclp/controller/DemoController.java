package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ConviteDTO.*;
import br.jus.tjba.aclp.service.ConviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller de demonstração para testar o fluxo de convites.
 * Ativo apenas com profile "demo" e restrito a ADMIN autenticado.
 * Para ativar: spring.profiles.active=demo (não usar em produção).
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Profile("demo")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Demo", description = "Endpoints de demonstração (desenvolvimento)")
@Slf4j
public class DemoController {

    private final ConviteService conviteService;

    @PostMapping("/criar-convite-teste")
    @Operation(summary = "Criar convite de teste",
            description = "Cria um convite de teste (desenvolvimento)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Convite criado"),
            @ApiResponse(responseCode = "400", description = "Erro ao criar convite")
    })
    public ResponseEntity<?> criarConviteTeste(HttpServletRequest request) {
        log.warn("ENDPOINT DE TESTE - Criando convite de demonstração");

        try {
            CriarConviteRequest dto = CriarConviteRequest.builder()
                    .email("teste@example.com")
                    .tipoUsuario(br.jus.tjba.aclp.model.enums.TipoUsuario.USUARIO)
                    .build();

            ConviteResponse response = conviteService.criarConvite(dto, request);

            Map<String, Object> info = new HashMap<>();
            info.put("modo", "DESENVOLVIMENTO");
            info.put("token", response.getToken());
            info.put("linkConvite", response.getLinkConvite());
            info.put("expiraEm", response.getExpiraEm());
            info.put("nota", "Em produção, o email seria enviado automaticamente");

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Convite de teste criado com sucesso");
            result.put("data", response);
            result.put("info", info);

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
        Map<String, Object> etapas = new HashMap<>();
        etapas.put("1", "Admin cria convite via API");
        etapas.put("2", "Sistema envia email com link único");
        etapas.put("3", "Usuário acessa link e valida token");
        etapas.put("4", "Usuário define sua senha");
        etapas.put("5", "Conta é ativada e token invalidado");

        Map<String, Object> endpoints = new HashMap<>();
        endpoints.put("criarConvite", "POST /api/usuarios/convites");
        endpoints.put("validarToken", "GET /api/usuarios/convites/validar/{token}");
        endpoints.put("ativarConta", "POST /api/usuarios/convites/ativar");
        endpoints.put("listarConvites", "GET /api/usuarios/convites");
        endpoints.put("reenviarConvite", "POST /api/usuarios/convites/{id}/reenviar");
        endpoints.put("cancelarConvite", "DELETE /api/usuarios/convites/{id}");

        Map<String, Object> testeRapido = new HashMap<>();
        testeRapido.put("criarConviteTeste", "POST /api/demo/criar-convite-teste");
        testeRapido.put("simularAtivacao", "POST /api/demo/simular-ativacao/{token}");

        Map<String, Object> response = new HashMap<>();
        response.put("sistema", "ACLP - Sistema de Convites");
        response.put("versao", "2.0.0");
        response.put("modo", "DESENVOLVIMENTO");
        response.put("fluxo", "Convite com link de primeiro acesso");
        response.put("etapas", etapas);
        response.put("endpoints", endpoints);
        response.put("testeRapido", testeRapido);

        return ResponseEntity.ok(response);
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

        log.warn("ENDPOINT DE TESTE - Simulando ativação de conta");

        try {
            ValidarConviteResponse tokenInfo = conviteService.validarConvite(token);

            if (!tokenInfo.isValido()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "success", false,
                                "message", "Token inválido: " + tokenInfo.getMensagem(),
                                "tokenInfo", tokenInfo
                        ));
            }

            AtivarConviteRequest dto = AtivarConviteRequest.builder()
                    .token(token)
                    .nome("Usuário Teste")
                    .senha(senha)
                    .confirmaSenha(senha)
                    .build();

            AtivarConviteResponse response = conviteService.ativarConvite(dto, request);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", response.getMessage(),
                    "usuario", response.getUsuario(),
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
}
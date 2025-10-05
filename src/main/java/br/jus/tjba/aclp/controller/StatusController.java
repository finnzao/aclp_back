package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.service.StatusSchedulerService;
import br.jus.tjba.aclp.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
@Tag(name = "Status", description = "Gerenciamento automático do status das pessoas")
@Slf4j
public class StatusController {

    private final StatusSchedulerService statusSchedulerService;

    @PostMapping("/verificar-inadimplentes")
    @Operation(summary = "Verificar inadimplentes manualmente",
            description = "Executa verificação manual do status de todas as pessoas")
    @ApiResponse(responseCode = "200", description = "Verificação executada com sucesso")
    public ResponseEntity<Map<String, Object>> verificarInadimplentes() {
        log.info("Executando verificação manual de inadimplentes");

        try {
            long pessoasMarcadas = statusSchedulerService.verificarStatusManual();

            String mensagem = pessoasMarcadas == 0
                    ? "Nenhuma pessoa foi marcada como inadimplente"
                    : String.format("%d pessoa(s) foram marcadas como inadimplentes", pessoasMarcadas);

            Map<String, Object> dados = new HashMap<>();
            dados.put("pessoasMarcadas", pessoasMarcadas);
            dados.put("executadoEm", LocalDateTime.now().toString());
            dados.put("tipo", "verificacao_manual");

            return ApiResponseUtil.success(dados, mensagem);

        } catch (Exception e) {
            log.error("Erro na verificação manual de inadimplentes", e);
            return ApiResponseUtil.internalServerError("Erro ao verificar inadimplentes: " + e.getMessage());
        }
    }

    @PostMapping("/reprocessar-todos")
    @Operation(summary = "Reprocessar todos os status",
            description = "Recalcula o status de TODAS as pessoas baseado na data atual")
    @ApiResponse(responseCode = "200", description = "Reprocessamento executado com sucesso")
    public ResponseEntity<Map<String, Object>> reprocessarTodos() {
        log.info("Executando reprocessamento completo de status");

        try {
            long inadimplentes = statusSchedulerService.reprocessarTodosStatus();

            Map<String, Object> dados = new HashMap<>();
            dados.put("inadimplentesEncontrados", inadimplentes);
            dados.put("executadoEm", LocalDateTime.now().toString());
            dados.put("tipo", "reprocessamento_completo");

            return ApiResponseUtil.success(dados, "Reprocessamento completo executado com sucesso");

        } catch (Exception e) {
            log.error("Erro no reprocessamento de status", e);
            return ApiResponseUtil.internalServerError("Erro ao reprocessar status: " + e.getMessage());
        }
    }

    @GetMapping("/estatisticas")
    @Operation(summary = "Estatísticas de status das pessoas",
            description = "Retorna informações sobre quantas pessoas estão em conformidade ou inadimplentes")
    @ApiResponse(responseCode = "200", description = "Estatísticas retornadas com sucesso")
    public ResponseEntity<Map<String, Object>> obterEstatisticas() {
        try {
            StatusSchedulerService.StatusInfo statusInfo = statusSchedulerService.obterStatusInfo();
            return ApiResponseUtil.success(statusInfo, "Estatísticas de status obtidas com sucesso");
        } catch (Exception e) {
            log.error("Erro ao obter estatísticas de status", e);
            return ApiResponseUtil.internalServerError("Erro ao obter estatísticas: " + e.getMessage());
        }
    }

    @GetMapping("/info")
    @Operation(summary = "Informações sobre o sistema de status",
            description = "Retorna informações sobre como funciona a atualização automática")
    @ApiResponse(responseCode = "200", description = "Informações retornadas")
    public ResponseEntity<Map<String, Object>> obterInfo() {
        Map<String, Object> agendamentos = new HashMap<>();
        agendamentos.put("verificacao_diaria", "01:00 todos os dias");
        agendamentos.put("verificacao_periodica", "A cada 6 horas (00:00, 06:00, 12:00, 18:00)");

        Map<String, Object> criterios = new HashMap<>();
        criterios.put("EM_CONFORMIDADE", "próximo_comparecimento >= data_atual");
        criterios.put("INADIMPLENTE", "próximo_comparecimento < data_atual");

        Map<String, Object> endpoints = new HashMap<>();
        endpoints.put("verificar", "POST /api/status/verificar-inadimplentes");
        endpoints.put("reprocessar", "POST /api/status/reprocessar-todos");
        endpoints.put("estatisticas", "GET /api/status/estatisticas");

        Map<String, Object> info = new HashMap<>();
        info.put("descricao", "Sistema de atualização automática de status");
        info.put("agendamentos", agendamentos);
        info.put("criterios", criterios);
        info.put("endpoints_manuais", endpoints);

        return ApiResponseUtil.success(info, "Sistema de status funcionando normalmente");
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
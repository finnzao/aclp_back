package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.ExecucaoPenalResponse;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.RegistroPenaRequest;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.RemicaoRequest;
import br.jus.tjba.aclp.dto.IncidenteExecucaoDTO.IncidenteResponse;
import br.jus.tjba.aclp.dto.IncidenteExecucaoDTO.RegistroIncidenteRequest;
import br.jus.tjba.aclp.dto.IncidenteExecucaoDTO.ResultadoRequest;
import br.jus.tjba.aclp.dto.ProvidenciaDTO.ProvidenciaResponse;
import br.jus.tjba.aclp.dto.ProvidenciaDTO.RegistroProvidenciaRequest;
import br.jus.tjba.aclp.service.ExecucaoPenalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/execucoes")
@RequiredArgsConstructor
@Tag(name = "Execução Penal", description = "Registro e consulta de execuções de pena")
@Slf4j
public class ExecucaoPenalController {

    private final ExecucaoPenalService execucaoPenalService;

    @PostMapping
    @Operation(summary = "Registrar pena",
            description = "Cria a execução penal de um processo EXECUCAO_REGIME_ABERTO. "
                    + "origem SENTENCA: envia dataTermino. origem DOSIMETRIA_CALCULADA: envia anos/meses/dias.")
    public ResponseEntity<ApiResponse<ExecucaoPenalResponse>> registrar(
            @Valid @RequestBody RegistroPenaRequest request) {
        ExecucaoPenalResponse response = execucaoPenalService.registrarPena(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Execução penal registrada", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExecucaoPenalResponse>> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Execução encontrada",
                execucaoPenalService.buscarPorId(id)));
    }

    @GetMapping("/processo/{processoId}")
    public ResponseEntity<ApiResponse<ExecucaoPenalResponse>> buscarPorProcesso(@PathVariable Long processoId) {
        return ResponseEntity.ok(ApiResponse.success("Execução encontrada",
                execucaoPenalService.buscarPorProcesso(processoId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExecucaoPenalResponse>>> listar() {
        return ResponseEntity.ok(ApiResponse.success("Execuções listadas",
                execucaoPenalService.listarTodas()));
    }

    @GetMapping("/proximas-extincao")
    public ResponseEntity<ApiResponse<List<ExecucaoPenalResponse>>> proximasExtincao(
            @RequestParam(defaultValue = "60") int dias) {
        return ResponseEntity.ok(ApiResponse.success("Painel D-" + dias,
                execucaoPenalService.proximasExtincao(dias)));
    }

    @GetMapping("/inativas")
    public ResponseEntity<ApiResponse<List<ExecucaoPenalResponse>>> inativas(
            @RequestParam(defaultValue = "90") int dias) {
        return ResponseEntity.ok(ApiResponse.success("Sem movimentação há " + dias + " dias",
                execucaoPenalService.inativas(dias)));
    }

    @GetMapping("/providencias/pendentes")
    public ResponseEntity<ApiResponse<List<ProvidenciaResponse>>> providenciasPendentes() {
        return ResponseEntity.ok(ApiResponse.success("Providências pendentes",
                execucaoPenalService.listarPendentes()));
    }

    @GetMapping("/{id}/providencias")
    public ResponseEntity<ApiResponse<List<ProvidenciaResponse>>> listarProvidencias(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Providências listadas",
                execucaoPenalService.listarProvidencias(id)));
    }

    @PostMapping("/{id}/providencias")
    public ResponseEntity<ApiResponse<ProvidenciaResponse>> registrarProvidencia(
            @PathVariable Long id,
            @Valid @RequestBody RegistroProvidenciaRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Providência registrada",
                execucaoPenalService.registrarProvidencia(id, request, auth.getName())));
    }

    @PatchMapping("/providencias/{providenciaId}/concluir")
    public ResponseEntity<ApiResponse<ProvidenciaResponse>> concluirProvidencia(
            @PathVariable Long providenciaId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Providência concluída",
                execucaoPenalService.concluirProvidencia(providenciaId, auth.getName())));
    }

    @PostMapping("/{id}/incidentes")
    @Operation(summary = "Registrar incidente",
            description = "Registra ocorrência (descumprimento, novo crime etc.). "
                    + "Tipos que exigem justificação suspendem a execução até a decisão.")
    public ResponseEntity<ApiResponse<IncidenteResponse>> registrarIncidente(
            @PathVariable Long id,
            @Valid @RequestBody RegistroIncidenteRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Incidente registrado",
                execucaoPenalService.registrarIncidente(id, request, auth.getName())));
    }

    @GetMapping("/{id}/incidentes")
    public ResponseEntity<ApiResponse<List<IncidenteResponse>>> listarIncidentes(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Incidentes listados",
                execucaoPenalService.listarIncidentes(id)));
    }

    @PatchMapping("/{id}/remicao")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lançar remição",
            description = "Soma dias remidos e recalcula o término. Aceita dias direto "
                    + "ou diasTrabalhados/horasEstudo (conversão LEP 126). Registro de "
                    + "decisão judicial — o sistema converte, não decide.")
    public ResponseEntity<ApiResponse<ExecucaoPenalResponse>> lancarRemicao(
            @PathVariable Long id,
            @Valid @RequestBody RemicaoRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Remição lançada",
                execucaoPenalService.lancarRemicao(id, request, auth.getName())));
    }

    @PatchMapping("/incidentes/{incidenteId}/resultado")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar decisão do incidente",
            description = "Registra o desfecho decidido pelo juízo. Única porta para "
                    + "REGREDIDA/EXTINTA — o sistema registra, não decide (LEP art. 118, §2º).")
    public ResponseEntity<ApiResponse<IncidenteResponse>> registrarResultado(
            @PathVariable Long incidenteId,
            @Valid @RequestBody ResultadoRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Decisão registrada",
                execucaoPenalService.registrarResultado(incidenteId, request, auth.getName())));
    }
}

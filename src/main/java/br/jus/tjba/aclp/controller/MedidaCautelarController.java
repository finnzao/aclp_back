package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.model.MedidaCautelar;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.TipoMedidaCautelar;
import br.jus.tjba.aclp.repository.MedidaCautelarRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** O sistema registra as medidas impostas; fiscaliza so o comparecimento. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MedidaCautelarController {

    private final MedidaCautelarRepository medidaRepository;
    private final ProcessoRepository processoRepository;

    public record RegistroMedidaRequest(
            @NotNull TipoMedidaCautelar tipoMedida,
            @NotNull LocalDate dataImposicao,
            String detalhe) {}

    public record RevogacaoRequest(@NotNull LocalDate dataRevogacao) {}

    @Value
    @Builder
    public static class MedidaResponse {
        Long id;
        TipoMedidaCautelar tipoMedida;
        String descricao;
        String baseLegal;
        LocalDate dataImposicao;
        LocalDate dataRevogacao;
        String detalhe;
        String registradoPor;
        boolean vigente;

        static MedidaResponse de(MedidaCautelar m) {
            return MedidaResponse.builder()
                    .id(m.getId()).tipoMedida(m.getTipoMedida())
                    .descricao(m.getTipoMedida().getDescricao())
                    .baseLegal(m.getTipoMedida().getBaseLegal())
                    .dataImposicao(m.getDataImposicao()).dataRevogacao(m.getDataRevogacao())
                    .detalhe(m.getDetalhe()).registradoPor(m.getRegistradoPor())
                    .vigente(m.isVigente()).build();
        }
    }

    @GetMapping("/processos/{processoId}/medidas")
    public ResponseEntity<ApiResponse<List<MedidaResponse>>> listar(@PathVariable Long processoId) {
        List<MedidaResponse> medidas = medidaRepository
                .findByProcessoIdOrderByDataImposicaoDesc(processoId)
                .stream().map(MedidaResponse::de).toList();
        return ResponseEntity.ok(ApiResponse.success("Medidas listadas", medidas));
    }

    @PostMapping("/processos/{processoId}/medidas")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<MedidaResponse>> registrar(
            @PathVariable Long processoId,
            @jakarta.validation.Valid @RequestBody RegistroMedidaRequest req,
            Authentication auth) {
        try {
            Processo processo = processoRepository.findById(processoId)
                    .orElseThrow(() -> new EntityNotFoundException("Processo não encontrado: " + processoId));
            MedidaCautelar medida = MedidaCautelar.builder()
                    .processo(processo)
                    .tipoMedida(req.tipoMedida())
                    .dataImposicao(req.dataImposicao())
                    .detalhe(req.detalhe() != null && !req.detalhe().isBlank() ? req.detalhe().trim() : null)
                    .registradoPor(auth.getName())
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                    "Medida registrada", MedidaResponse.de(medidaRepository.save(medida))));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/medidas-cautelares/{id}/revogar")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ApiResponse<MedidaResponse>> revogar(
            @PathVariable Long id, @jakarta.validation.Valid @RequestBody RevogacaoRequest req) {
        try {
            MedidaCautelar medida = medidaRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Medida não encontrada: " + id));
            medida.revogar(req.dataRevogacao());
            return ResponseEntity.ok(ApiResponse.success(
                    "Medida revogada", MedidaResponse.de(medidaRepository.save(medida))));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}

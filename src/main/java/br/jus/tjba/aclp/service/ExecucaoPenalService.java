package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.ExecucaoPenalResponse;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.RegistroPenaRequest;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.RemicaoRequest;
import br.jus.tjba.aclp.dto.IncidenteExecucaoDTO.IncidenteResponse;
import br.jus.tjba.aclp.dto.IncidenteExecucaoDTO.RegistroIncidenteRequest;
import br.jus.tjba.aclp.dto.IncidenteExecucaoDTO.ResultadoRequest;
import br.jus.tjba.aclp.model.ExecucaoPenal;
import br.jus.tjba.aclp.model.IncidenteExecucao;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.OrigemCalculo;
import br.jus.tjba.aclp.repository.ExecucaoPenalRepository;
import br.jus.tjba.aclp.repository.IncidenteExecucaoRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Registro e consulta de execucao penal. SENTENCA recebe a data final e deriva
 * os dias; DOSIMETRIA recebe o tempo e deriva a data. Calculo mora na entidade.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecucaoPenalService {

    private final ExecucaoPenalRepository execucaoPenalRepository;
    private final IncidenteExecucaoRepository incidenteExecucaoRepository;
    private final ProcessoRepository processoRepository;

    @Transactional
    public ExecucaoPenalResponse registrarPena(RegistroPenaRequest req) {
        if (req.getProcessoId() == null)
            throw new IllegalArgumentException("Processo é obrigatório");
        Processo processo = processoRepository.findById(req.getProcessoId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Processo não encontrado: " + req.getProcessoId()));

        if (!processo.isExecucao())
            throw new IllegalArgumentException(
                    "Registro de pena exige processo de natureza EXECUCAO_REGIME_ABERTO. "
                    + "Este processo é " + processo.getNaturezaVinculo() + ".");

        if (execucaoPenalRepository.existsByProcessoId(processo.getId()))
            throw new IllegalArgumentException(
                    "Este processo já possui execução penal registrada (relação é 1:1).");

        if (execucaoPenalRepository.existsByNumeroExecucao(req.getNumeroExecucao()))
            throw new IllegalArgumentException(
                    "Já existe execução com o PEC " + req.getNumeroExecucao());

        LocalDate inicio = req.getDataInicio();
        LocalDate terminoOriginal;
        int penaTotalDias;

        if (req.getOrigemCalculo() == OrigemCalculo.SENTENCA) {
            if (req.getDataTermino() == null)
                throw new IllegalArgumentException(
                        "Origem SENTENCA exige a data de término informada na sentença.");
            terminoOriginal = req.getDataTermino();
            penaTotalDias = ExecucaoPenal.calcularPenaDias(inicio, terminoOriginal);
        } else {
            int anos  = req.getAnos()  != null ? req.getAnos()  : 0;
            int meses = req.getMeses() != null ? req.getMeses() : 0;
            int dias  = req.getDias()  != null ? req.getDias()  : 0;
            terminoOriginal = ExecucaoPenal.calcularTerminoPorTempo(inicio, anos, meses, dias);
            penaTotalDias = ExecucaoPenal.calcularPenaDias(inicio, terminoOriginal);
        }

        int detraidos = req.getDiasDetraidos() != null ? req.getDiasDetraidos() : 0;
        if (detraidos >= penaTotalDias)
            throw new IllegalArgumentException(
                    "Detração (" + detraidos + " dias) não pode ser igual ou maior que a pena ("
                    + penaTotalDias + " dias).");

        ExecucaoPenal execucao = ExecucaoPenal.builder()
                .processo(processo)
                .numeroExecucao(req.getNumeroExecucao().trim())
                .guiaRecolhimento(req.getGuiaRecolhimento())
                .numeroSeeu(req.getNumeroSeeu())
                .dataInicioCumprimento(inicio)
                .penaTotalDias(penaTotalDias)
                .diasDetraidos(detraidos)
                .dataTerminoOriginal(terminoOriginal)
                // prevista: @PrePersist calcula original - detracao
                .origemCalculo(req.getOrigemCalculo())
                .observacoes(req.getObservacoes())
                .build();

        ExecucaoPenal salva = execucaoPenalRepository.save(execucao);
        log.info("Execução penal registrada: PEC {}, processo {}, {} dias, término {} (origem {})",
                salva.getNumeroExecucao(), processo.getNumeroProcesso(),
                penaTotalDias, terminoOriginal, req.getOrigemCalculo());

        return ExecucaoPenalResponse.fromEntity(salva);
    }

    @Transactional(readOnly = true)
    public ExecucaoPenalResponse buscarPorId(Long id) {
        return execucaoPenalRepository.findById(id)
                .map(ExecucaoPenalResponse::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException("Execução não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public ExecucaoPenalResponse buscarPorProcesso(Long processoId) {
        return execucaoPenalRepository.findByProcessoId(processoId)
                .map(ExecucaoPenalResponse::fromEntity)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Processo " + processoId + " não possui execução penal registrada"));
    }

    @Transactional(readOnly = true)
    public List<ExecucaoPenalResponse> listarTodas() {
        return execucaoPenalRepository.findAllComProcesso().stream()
                .map(ExecucaoPenalResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<ExecucaoPenalResponse> proximasExtincao(int dias) {
        return execucaoPenalRepository.findProximasExtincao(LocalDate.now().plusDays(dias)).stream()
                .map(ExecucaoPenalResponse::fromEntity).toList();
    }

    @Transactional
    public ExecucaoPenalResponse lancarRemicao(Long execucaoId, RemicaoRequest req, String lancadoPor) {
        ExecucaoPenal execucao = execucaoPenalRepository.findById(execucaoId)
                .orElseThrow(() -> new EntityNotFoundException("Execução não encontrada: " + execucaoId));

        int dias;
        if (req.getDias() != null) {
            dias = req.getDias();
        } else {
            int trabalhados = req.getDiasTrabalhados() != null ? req.getDiasTrabalhados() : 0;
            int estudo = req.getHorasEstudo() != null ? req.getHorasEstudo() : 0;
            dias = ExecucaoPenal.calcularRemicao(trabalhados, estudo);
            if (dias == 0)
                throw new IllegalArgumentException(
                        "Os valores informados não somam nenhum dia de remição "
                        + "(3 dias trabalhados ou 12h de estudo = 1 dia).");
        }

        execucao.adicionarRemicao(dias);
        log.info("Remição de {} dia(s) lançada na execução {} por {} — término previsto: {}",
                dias, execucaoId, lancadoPor, execucao.getDataTerminoPrevista());
        return ExecucaoPenalResponse.fromEntity(execucao);
    }

    // ------------------- Incidentes (fase 5) -------------------

    @Transactional
    public IncidenteResponse registrarIncidente(Long execucaoId, RegistroIncidenteRequest req,
                                                String registradoPor) {
        ExecucaoPenal execucao = execucaoPenalRepository.findById(execucaoId)
                .orElseThrow(() -> new EntityNotFoundException("Execução não encontrada: " + execucaoId));

        IncidenteExecucao incidente = IncidenteExecucao.builder()
                .execucaoPenal(execucao)
                .tipo(req.getTipo())
                .dataOcorrencia(req.getDataOcorrencia())
                .descricao(req.getDescricao().trim())
                .dataAudiencia(req.getDataAudiencia())
                .registradoPor(registradoPor)
                .build();

        execucao.aoRegistrarIncidente(req.getTipo());
        IncidenteExecucao salvo = incidenteExecucaoRepository.save(incidente);
        log.info("Incidente {} registrado na execução {} por {}", req.getTipo(), execucaoId, registradoPor);
        return IncidenteResponse.fromEntity(salvo);
    }

    @Transactional
    public IncidenteResponse registrarResultado(Long incidenteId, ResultadoRequest req,
                                                String decididoPor) {
        IncidenteExecucao incidente = incidenteExecucaoRepository.findById(incidenteId)
                .orElseThrow(() -> new EntityNotFoundException("Incidente não encontrado: " + incidenteId));

        if (req.getResultado() == br.jus.tjba.aclp.model.enums.ResultadoIncidente.AGUARDANDO)
            throw new IllegalArgumentException("AGUARDANDO não é um desfecho — informe a decisão.");

        incidente.setResultado(req.getResultado());
        incidente.setDataDecisao(req.getDataDecisao());
        if (req.getDataAudiencia() != null) incidente.setDataAudiencia(req.getDataAudiencia());
        incidente.setDecididoPor(decididoPor);

        incidente.getExecucaoPenal().aoDecidirIncidente(req.getResultado());
        log.info("Incidente {} decidido: {} por {}", incidenteId, req.getResultado(), decididoPor);
        return IncidenteResponse.fromEntity(incidente);
    }

    /** Evasão declarada (custodiado foragido) — entra no rito de justificação. */
    @Transactional
    public void registrarIncidenteEvasao(Long processoId, String motivo, String registradoPor) {
        execucaoPenalRepository.findByProcessoId(processoId).ifPresent(execucao -> {
            IncidenteExecucao incidente = IncidenteExecucao.builder()
                    .execucaoPenal(execucao)
                    .tipo(br.jus.tjba.aclp.model.enums.TipoIncidente.EVASAO)
                    .dataOcorrencia(LocalDate.now())
                    .descricao("Declarado foragido: " + motivo)
                    .registradoPor(registradoPor)
                    .build();
            execucao.aoRegistrarIncidente(incidente.getTipo());
            incidenteExecucaoRepository.save(incidente);
        });
    }

    @Transactional(readOnly = true)
    public List<IncidenteResponse> listarIncidentes(Long execucaoId) {
        if (!execucaoPenalRepository.existsById(execucaoId))
            throw new EntityNotFoundException("Execução não encontrada: " + execucaoId);
        return incidenteExecucaoRepository.findByExecucaoPenalIdOrderByDataOcorrenciaDesc(execucaoId)
                .stream().map(IncidenteResponse::fromEntity).toList();
    }
}

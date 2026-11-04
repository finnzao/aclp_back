package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.ExecucaoPenalResponse;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.RegistroPenaRequest;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.RemicaoRequest;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.RetificarTerminoRequest;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.MotivoRequest;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.ExtincaoRequest;
import br.jus.tjba.aclp.dto.ExecucaoPenalDTO.EdicaoExecucaoRequest;
import br.jus.tjba.aclp.model.enums.TipoIncidente;
import br.jus.tjba.aclp.dto.IncidenteExecucaoDTO.IncidenteResponse;
import br.jus.tjba.aclp.dto.IncidenteExecucaoDTO.RegistroIncidenteRequest;
import br.jus.tjba.aclp.dto.IncidenteExecucaoDTO.ResultadoRequest;
import br.jus.tjba.aclp.dto.ProvidenciaDTO.ProvidenciaResponse;
import br.jus.tjba.aclp.dto.ProvidenciaDTO.RegistroProvidenciaRequest;
import br.jus.tjba.aclp.model.ExecucaoPenal;
import br.jus.tjba.aclp.model.IncidenteExecucao;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.Providencia;
import br.jus.tjba.aclp.model.enums.ModalidadeExecucao;
import br.jus.tjba.aclp.model.enums.OrigemCalculo;
import br.jus.tjba.aclp.model.enums.ResultadoIncidente;
import br.jus.tjba.aclp.model.enums.SituacaoExecucao;
import br.jus.tjba.aclp.model.enums.TipoProvidencia;
import br.jus.tjba.aclp.repository.ExecucaoPenalRepository;
import br.jus.tjba.aclp.repository.IncidenteExecucaoRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;
import br.jus.tjba.aclp.repository.ProvidenciaRepository;
import br.jus.tjba.aclp.util.TempoAtual;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecucaoPenalService {

    static final String SISTEMA = "sistema";
    static final int PRAZO_ADMONITORIA_DIAS = 30;
    static final int PRAZO_LAUDO_DIAS = 180;
    static final int PRAZO_INTIMACAO_ANTES_AUDIENCIA_DIAS = 5;
    static final int PRAZO_EXTINCAO_DIAS = 60;

    private final ExecucaoPenalRepository execucaoPenalRepository;
    private final IncidenteExecucaoRepository incidenteExecucaoRepository;
    private final ProvidenciaRepository providenciaRepository;
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
                    "Registro de pena exige processo de natureza EXECUCAO_PENAL. "
                    + "Este processo é " + processo.getNaturezaVinculo() + ".");

        if (execucaoPenalRepository.existsByProcessoId(processo.getId()))
            throw new IllegalArgumentException(
                    "Este processo já possui execução penal registrada (relação é 1:1).");

        if (execucaoPenalRepository.existsByNumeroExecucao(req.getNumeroExecucao()))
            throw new IllegalArgumentException(
                    "Já existe execução com o PEC " + req.getNumeroExecucao());

        ModalidadeExecucao modalidade = req.getModalidade();
        LocalDate inicio = req.getDataInicio();
        LocalDate terminoOriginal = null;
        Integer penaTotalDias = null;
        int detraidos = req.getDiasDetraidos() != null ? req.getDiasDetraidos() : 0;

        if (modalidade.isTerminoCalculavel()) {
            if (req.getOrigemCalculo() == null)
                throw new IllegalArgumentException("Origem do cálculo é obrigatória nesta modalidade.");
            if (req.getOrigemCalculo() == OrigemCalculo.SENTENCA) {
                if (req.getDataTermino() == null)
                    throw new IllegalArgumentException(
                            "Origem SENTENCA exige a data de término informada na sentença.");
                terminoOriginal = req.getDataTermino();
            } else {
                int anos  = req.getAnos()  != null ? req.getAnos()  : 0;
                int meses = req.getMeses() != null ? req.getMeses() : 0;
                int dias  = req.getDias()  != null ? req.getDias()  : 0;
                terminoOriginal = ExecucaoPenal.calcularTerminoPorTempo(inicio, anos, meses, dias);
            }
            penaTotalDias = ExecucaoPenal.calcularPenaDias(inicio, terminoOriginal);
            if (detraidos >= penaTotalDias)
                throw new IllegalArgumentException(
                        "Detração (" + detraidos + " dias) não pode ser igual ou maior que a pena ("
                        + penaTotalDias + " dias).");
        }

        ExecucaoPenal execucao = ExecucaoPenal.builder()
                .processo(processo)
                .modalidade(modalidade)
                .dataDistribuicao(req.getDataDistribuicao())
                .numeroExecucao(req.getNumeroExecucao().trim())
                .guiaRecolhimento(req.getGuiaRecolhimento())
                .numeroSeeu(req.getNumeroSeeu())
                .dataInicioCumprimento(inicio)
                .penaTotalDias(penaTotalDias)
                .diasDetraidos(detraidos)
                .dataTerminoOriginal(terminoOriginal)
                .origemCalculo(modalidade.isTerminoCalculavel() ? req.getOrigemCalculo() : null)
                .observacoes(req.getObservacoes())
                .build();

        if (!modalidade.isComparecimentoPeriodico()) {
            processo.setProximoComparecimento(null);
        }

        ExecucaoPenal salva = execucaoPenalRepository.save(execucao);
        criarProvidenciasIniciais(salva);
        log.info("Execução penal registrada: PEC {}, {}, processo {}, término {}",
                salva.getNumeroExecucao(), modalidade, processo.getNumeroProcesso(), terminoOriginal);

        return toResponse(salva);
    }

    private void criarProvidenciasIniciais(ExecucaoPenal e) {
        switch (e.getModalidade()) {
            case ABERTO, LIVRAMENTO_CONDICIONAL, PRD ->
                criarAutomatica(e, TipoProvidencia.AUDIENCIA_ADMONITORIA, null, PRAZO_ADMONITORIA_DIAS);
            case TRATAMENTO_AMBULATORIAL ->
                criarAutomatica(e, TipoProvidencia.OFICIAR, "Solicitar laudo atualizado ao CAPS", PRAZO_LAUDO_DIAS);
            default -> { }
        }
    }

    private void criarAutomatica(ExecucaoPenal e, TipoProvidencia tipo, String descricao, int prazoDias) {
        if (providenciaRepository.existsByExecucaoPenalIdAndTipoAndConcluidaEmIsNull(e.getId(), tipo)) return;
        providenciaRepository.save(Providencia.builder()
                .execucaoPenal(e)
                .tipo(tipo)
                .descricao(descricao)
                .prazo(TempoAtual.hoje().plusDays(prazoDias))
                .registradoPor(SISTEMA)
                .build());
    }

    @Transactional(readOnly = true)
    public ExecucaoPenalResponse buscarPorId(Long id) {
        return execucaoPenalRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Execução não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public ExecucaoPenalResponse buscarPorProcesso(Long processoId) {
        return execucaoPenalRepository.findByProcessoId(processoId)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Processo " + processoId + " não possui execução penal registrada"));
    }

    @Transactional(readOnly = true)
    public List<ExecucaoPenalResponse> listarTodas() {
        return toResponses(execucaoPenalRepository.findAllComProcesso());
    }

    @Transactional(readOnly = true)
    public List<ExecucaoPenalResponse> proximasExtincao(int dias) {
        return toResponses(execucaoPenalRepository.findProximasExtincao(TempoAtual.hoje().plusDays(dias)));
    }

    private ExecucaoPenalResponse toResponse(ExecucaoPenal e) {
        return toResponses(List.of(e)).get(0);
    }

    // ultima movimentacao = max(providencia, incidente, comparecimento, alteracao da execucao)
    private List<ExecucaoPenalResponse> toResponses(List<ExecucaoPenal> execucoes) {
        if (execucoes.isEmpty()) return List.of();
        List<Long> ids = execucoes.stream().map(ExecucaoPenal::getId).toList();
        Map<Long, LocalDate> ultProv = agrupar(providenciaRepository.ultimaMovimentacaoPorExecucao(ids));
        Map<Long, LocalDate> ultInc = agrupar(incidenteExecucaoRepository.ultimaMovimentacaoPorExecucao(ids));
        Map<Long, long[]> pendentes = new HashMap<>();
        for (Providencia p : providenciaRepository.findPendentes()) {
            long[] c = pendentes.computeIfAbsent(p.getExecucaoPenal().getId(), k -> new long[2]);
            c[0]++;
            if (p.isVencida()) c[1]++;
        }

        return execucoes.stream().map(e -> {
            ExecucaoPenalResponse r = ExecucaoPenalResponse.fromEntity(e);
            LocalDate ult = maxData(
                    ultProv.get(e.getId()),
                    ultInc.get(e.getId()),
                    e.getProcesso().getUltimoComparecimento(),
                    (e.getAtualizadoEm() != null ? e.getAtualizadoEm() : e.getCriadoEm()).toLocalDate());
            r.setUltimaMovimentacao(ult);
            long[] c = pendentes.getOrDefault(e.getId(), new long[2]);
            r.setProvidenciasPendentes(c[0]);
            r.setProvidenciasVencidas(c[1]);
            return r;
        }).toList();
    }

    private static Map<Long, LocalDate> agrupar(List<Object[]> linhas) {
        Map<Long, LocalDate> m = new HashMap<>();
        for (Object[] l : linhas) {
            Object d = l[1];
            if (d instanceof LocalDate ld) m.put((Long) l[0], ld);
            else if (d instanceof java.sql.Date sd) m.put((Long) l[0], sd.toLocalDate());
        }
        return m;
    }

    static LocalDate maxData(LocalDate... datas) {
        LocalDate max = null;
        for (LocalDate d : datas) if (d != null && (max == null || d.isAfter(max))) max = d;
        return max;
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
        return toResponse(execucao);
    }

    @Transactional
    public ExecucaoPenalResponse retificarTermino(Long execucaoId, RetificarTerminoRequest req, String por) {
        ExecucaoPenal e = execucaoPenalRepository.findById(execucaoId)
                .orElseThrow(() -> new EntityNotFoundException("Execução não encontrada: " + execucaoId));
        if (!e.temPena())
            throw new IllegalArgumentException("Esta modalidade não tem término calculável");
        if (e.getSituacaoExecucao() == SituacaoExecucao.EXTINTA)
            throw new IllegalArgumentException("Execução extinta não pode ter o término alterado");
        LocalDate anterior = e.getDataTerminoPrevista();
        e.retificarTermino(req.getNovoTermino());
        providenciaRepository.save(Providencia.builder()
                .execucaoPenal(e)
                .tipo(TipoProvidencia.CERTIFICAR)
                .descricao("Término retificado de " + anterior + " para " + e.getDataTerminoPrevista() + ": " + req.getMotivo().trim())
                .concluidaEm(TempoAtual.hoje())
                .concluidaPor(por)
                .registradoPor(por)
                .build());
        log.info("Término da execução {} retificado para {} por {}", execucaoId, e.getDataTerminoPrevista(), por);
        return toResponse(e);
    }

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
        criarIntimacaoParaAudiencia(execucao, req.getDataAudiencia());
        log.info("Incidente {} registrado na execução {} por {}", req.getTipo(), execucaoId, registradoPor);
        return IncidenteResponse.fromEntity(salvo);
    }

    private void criarIntimacaoParaAudiencia(ExecucaoPenal e, LocalDate dataAudiencia) {
        if (dataAudiencia == null) return;
        if (providenciaRepository.existsByExecucaoPenalIdAndTipoAndConcluidaEmIsNull(e.getId(), TipoProvidencia.INTIMAR)) return;
        providenciaRepository.save(Providencia.builder()
                .execucaoPenal(e)
                .tipo(TipoProvidencia.INTIMAR)
                .descricao("Intimar para audiência de " + dataAudiencia)
                .prazo(dataAudiencia.minusDays(PRAZO_INTIMACAO_ANTES_AUDIENCIA_DIAS))
                .prioritaria(true)
                .registradoPor(SISTEMA)
                .build());
    }

    @Transactional
    public IncidenteResponse registrarResultado(Long incidenteId, ResultadoRequest req,
                                                String decididoPor) {
        IncidenteExecucao incidente = incidenteExecucaoRepository.findById(incidenteId)
                .orElseThrow(() -> new EntityNotFoundException("Incidente não encontrado: " + incidenteId));

        if (req.getResultado() == ResultadoIncidente.AGUARDANDO)
            throw new IllegalArgumentException("AGUARDANDO não é um desfecho — informe a decisão.");

        incidente.setResultado(req.getResultado());
        incidente.setDataDecisao(req.getDataDecisao());
        if (req.getDataAudiencia() != null) {
            incidente.setDataAudiencia(req.getDataAudiencia());
            criarIntimacaoParaAudiencia(incidente.getExecucaoPenal(), req.getDataAudiencia());
        }
        incidente.setDecididoPor(decididoPor);

        ExecucaoPenal execucao = incidente.getExecucaoPenal();
        execucao.aoDecidirIncidente(req.getResultado());
        abrirProvidenciasDeExtincao(execucao);
        log.info("Incidente {} decidido: {} por {}", incidenteId, req.getResultado(), decididoPor);
        return IncidenteResponse.fromEntity(incidente);
    }

    private void abrirProvidenciasDeExtincao(ExecucaoPenal execucao) {
        if (execucao.getSituacaoExecucao() != SituacaoExecucao.EXTINTA) return;
        criarAutomatica(execucao, TipoProvidencia.COMUNICAR_TRE, "Comunicar extinção ao TRE", 15);
        criarAutomatica(execucao, TipoProvidencia.ARQUIVAR, "Arquivar a execução", 30);
    }

    // sentenca de extincao por cumprimento: incidente ja decidido, mesmo rito, um passo
    @Transactional
    public ExecucaoPenalResponse extinguir(Long execucaoId, ExtincaoRequest req, String por) {
        ExecucaoPenal e = buscar(execucaoId);
        if (e.getSituacaoExecucao() == SituacaoExecucao.EXTINTA)
            throw new IllegalArgumentException("Execução já está extinta");
        incidenteExecucaoRepository.save(IncidenteExecucao.builder()
                .execucaoPenal(e)
                .tipo(TipoIncidente.EXTINCAO_PUNIBILIDADE)
                .dataOcorrencia(req.getDataSentenca())
                .descricao(req.getDescricao().trim())
                .resultado(ResultadoIncidente.EXTINCAO_DECLARADA)
                .dataDecisao(req.getDataSentenca())
                .registradoPor(por)
                .decididoPor(por)
                .build());
        e.aoDecidirIncidente(ResultadoIncidente.EXTINCAO_DECLARADA);
        abrirProvidenciasDeExtincao(e);
        log.info("Execução {} extinta por sentença de {} ({})", execucaoId, req.getDataSentenca(), por);
        return toResponse(e);
    }

    @Transactional
    public ExecucaoPenalResponse suspender(Long execucaoId, MotivoRequest req, String por) {
        ExecucaoPenal e = buscar(execucaoId);
        e.suspender();
        registrarNoHistorico(e, "Execução suspensa: " + req.getMotivo().trim(), por);
        return toResponse(e);
    }

    @Transactional
    public ExecucaoPenalResponse retomar(Long execucaoId, MotivoRequest req, String por) {
        ExecucaoPenal e = buscar(execucaoId);
        e.retomar();
        registrarNoHistorico(e, "Execução retomada: " + req.getMotivo().trim(), por);
        return toResponse(e);
    }

    @Transactional
    public ExecucaoPenalResponse editar(Long execucaoId, EdicaoExecucaoRequest req, String por) {
        ExecucaoPenal e = buscar(execucaoId);
        String pec = req.getNumeroExecucao().trim();
        if (!pec.equals(e.getNumeroExecucao()) && execucaoPenalRepository.existsByNumeroExecucao(pec))
            throw new IllegalArgumentException("Já existe execução com o PEC " + pec);
        ModalidadeExecucao nova = req.getModalidade();
        if (nova.isTerminoCalculavel() && !e.temPena())
            throw new IllegalArgumentException("A modalidade " + nova.getDescricao() + " exige pena registrada; esta execução não tem término calculável");
        if (nova.isComparecimentoPeriodico() && req.getPeriodicidade() == null && e.getProcesso().getPeriodicidade() == null)
            throw new IllegalArgumentException("Informe a periodicidade: " + nova.getDescricao() + " tem agenda de comparecimento");

        StringBuilder mudancas = new StringBuilder();
        if (e.getModalidade() != nova) mudancas.append("modalidade ").append(e.getModalidade()).append(" -> ").append(nova).append("; ");
        if (!pec.equals(e.getNumeroExecucao())) mudancas.append("PEC ").append(e.getNumeroExecucao()).append(" -> ").append(pec).append("; ");

        e.setNumeroExecucao(pec);
        e.setModalidade(nova);
        e.setGuiaRecolhimento(req.getGuiaRecolhimento());
        e.setNumeroSeeu(req.getNumeroSeeu());
        e.setDataDistribuicao(req.getDataDistribuicao());
        e.setObservacoes(req.getObservacoes());
        if (req.getPeriodicidade() != null) e.getProcesso().setPeriodicidade(req.getPeriodicidade());
        if (!nova.isComparecimentoPeriodico()) e.getProcesso().setPeriodicidade(null);
        if (e.getSituacaoExecucao() == SituacaoExecucao.EM_CUMPRIMENTO) e.ajustarAgendaDoProcesso();
        if (mudancas.length() > 0) registrarNoHistorico(e, "Dados alterados: " + mudancas, por);
        return toResponse(e);
    }

    private ExecucaoPenal buscar(Long id) {
        return execucaoPenalRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Execução não encontrada: " + id));
    }

    // trilha sem tabela nova: providencia ja concluida
    private void registrarNoHistorico(ExecucaoPenal e, String texto, String por) {
        providenciaRepository.save(Providencia.builder()
                .execucaoPenal(e)
                .tipo(TipoProvidencia.CERTIFICAR)
                .descricao(texto.length() > 500 ? texto.substring(0, 500) : texto)
                .concluidaEm(TempoAtual.hoje())
                .concluidaPor(por)
                .registradoPor(por)
                .build());
    }

    @Transactional
    public void registrarIncidenteEvasao(Long processoId, String motivo, String registradoPor) {
        execucaoPenalRepository.findByProcessoId(processoId).ifPresent(execucao -> {
            IncidenteExecucao incidente = IncidenteExecucao.builder()
                    .execucaoPenal(execucao)
                    .tipo(br.jus.tjba.aclp.model.enums.TipoIncidente.EVASAO)
                    .dataOcorrencia(TempoAtual.hoje())
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

    @Transactional
    public ProvidenciaResponse registrarProvidencia(Long execucaoId, RegistroProvidenciaRequest req,
                                                    String registradoPor) {
        ExecucaoPenal execucao = execucaoPenalRepository.findById(execucaoId)
                .orElseThrow(() -> new EntityNotFoundException("Execução não encontrada: " + execucaoId));
        Providencia p = providenciaRepository.save(Providencia.builder()
                .execucaoPenal(execucao)
                .tipo(req.getTipo())
                .descricao(req.getDescricao() != null ? req.getDescricao().trim() : null)
                .prazo(req.getPrazo())
                .prioritaria(req.isPrioritaria())
                .registradoPor(registradoPor)
                .build());
        return ProvidenciaResponse.fromEntity(p);
    }

    @Transactional
    public ProvidenciaResponse concluirProvidencia(Long providenciaId, String por) {
        Providencia p = providenciaRepository.findById(providenciaId)
                .orElseThrow(() -> new EntityNotFoundException("Providência não encontrada: " + providenciaId));
        p.concluir(por);
        ExecucaoPenal e = p.getExecucaoPenal();
        if (e.getModalidade() == ModalidadeExecucao.TRATAMENTO_AMBULATORIAL
                && p.getTipo() == TipoProvidencia.OFICIAR && p.getRegistradoPor().equals(SISTEMA)
                && e.getSituacaoExecucao() == SituacaoExecucao.EM_CUMPRIMENTO) {
            criarAutomatica(e, TipoProvidencia.OFICIAR, "Solicitar laudo atualizado ao CAPS", PRAZO_LAUDO_DIAS);
        }
        return ProvidenciaResponse.fromEntity(p);
    }

    @Transactional
    public ProvidenciaResponse editarProvidencia(Long providenciaId, RegistroProvidenciaRequest req) {
        Providencia p = providenciaRepository.findById(providenciaId)
                .orElseThrow(() -> new EntityNotFoundException("Providência não encontrada: " + providenciaId));
        if (!p.isPendente()) throw new IllegalArgumentException("Providência concluída não pode ser editada");
        p.setTipo(req.getTipo());
        p.setDescricao(req.getDescricao() != null ? req.getDescricao().trim() : null);
        p.setPrazo(req.getPrazo());
        p.setPrioritaria(req.isPrioritaria());
        return ProvidenciaResponse.fromEntity(p);
    }

    @Transactional
    public void excluirProvidencia(Long providenciaId) {
        Providencia p = providenciaRepository.findById(providenciaId)
                .orElseThrow(() -> new EntityNotFoundException("Providência não encontrada: " + providenciaId));
        if (!p.isPendente()) throw new IllegalArgumentException("Providência concluída faz parte do histórico e não pode ser excluída");
        providenciaRepository.delete(p);
    }

    @Transactional(readOnly = true)
    public List<ProvidenciaResponse> listarProvidencias(Long execucaoId) {
        if (!execucaoPenalRepository.existsById(execucaoId))
            throw new EntityNotFoundException("Execução não encontrada: " + execucaoId);
        return providenciaRepository.findByExecucaoPenalIdOrderByConcluidaEmAscPrazoAscCriadoEmDesc(execucaoId)
                .stream().map(ProvidenciaResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<ProvidenciaResponse> listarPendentes() {
        return providenciaRepository.findPendentes().stream().map(ProvidenciaResponse::fromEntity).toList();
    }

    // D-60: abre a providencia de atestado uma vez por execucao
    @Transactional
    public int gerarProvidenciasExtincao() {
        int criadas = 0;
        for (ExecucaoPenal e : execucaoPenalRepository.findProximasExtincao(TempoAtual.hoje().plusDays(PRAZO_EXTINCAO_DIAS))) {
            if (providenciaRepository.existsByExecucaoPenalIdAndTipoAndConcluidaEmIsNull(e.getId(), TipoProvidencia.EXPEDIR_ATESTADO_PENA)) continue;
            providenciaRepository.save(Providencia.builder()
                    .execucaoPenal(e)
                    .tipo(TipoProvidencia.EXPEDIR_ATESTADO_PENA)
                    .descricao("Instruir extinção da punibilidade — término previsto " + e.getDataTerminoPrevista())
                    .prazo(e.getDataTerminoPrevista().minusDays(15))
                    .prioritaria(true)
                    .registradoPor(SISTEMA)
                    .build());
            criadas++;
        }
        return criadas;
    }

    @Transactional(readOnly = true)
    public List<ExecucaoPenalResponse> inativas(int dias) {
        LocalDate limite = TempoAtual.hoje().minusDays(dias);
        return listarTodas().stream()
                .filter(r -> r.getSituacaoExecucao() == SituacaoExecucao.EM_CUMPRIMENTO)
                .filter(r -> r.getUltimaMovimentacao() == null || r.getUltimaMovimentacao().isBefore(limite))
                .toList();
    }
}

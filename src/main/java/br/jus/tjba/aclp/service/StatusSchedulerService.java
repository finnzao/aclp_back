package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.repository.CustodiadoRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusSchedulerService {

    private final CustodiadoRepository custodiadoRepository;
    private final ProcessoRepository processoRepository;

    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void verificarStatusDiario() {
        log.info("Iniciando verificação automática diária de status - {}", LocalDate.now());
        long marcados = executarVerificacaoStatus();
        if (marcados > 0) {
            log.warn("{} processos foram marcados como INADIMPLENTES", marcados);
        } else {
            log.info("Nenhum processo ficou inadimplente hoje");
        }
    }

    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void verificarStatusPeriodico() {
        log.debug("Verificação periódica de status - {}", LocalDate.now());
        executarVerificacaoStatus();
    }

    /**
     * Verifica processos EM_CONFORMIDADE com proximo_comparecimento < hoje
     * e marca como INADIMPLENTE (usa ProcessoRepository)
     */
    private long executarVerificacaoStatus() {
        // Buscar processos atrasados via ProcessoRepository
        List<Processo> processosAtrasados = processoRepository.findAtrasadosParaVerificacao();
        long contadorProcessos = 0;

        for (Processo processo : processosAtrasados) {
            processo.setStatus(StatusComparecimento.INADIMPLENTE);
            processoRepository.save(processo);
            contadorProcessos++;

            long diasAtraso = java.time.temporal.ChronoUnit.DAYS
                    .between(processo.getProximoComparecimento(), LocalDate.now());
            log.warn("INADIMPLENTE: Processo {} (ID: {}) - Custodiado: {} - {} dias de atraso",
                    processo.getNumeroProcesso(), processo.getId(),
                    processo.getCustodiado() != null ? processo.getCustodiado().getNome() : "N/A",
                    diasAtraso);
        }

        // Manter compatibilidade: também atualizar custodiados (temporário)
        List<Custodiado> custodiadosEmConformidade = custodiadoRepository.findByStatus(StatusComparecimento.EM_CONFORMIDADE);
        LocalDate hoje = LocalDate.now();
        long contadorCustodiados = 0;

        for (Custodiado pessoa : custodiadosEmConformidade) {
            if (pessoa.getProximoComparecimento() != null && pessoa.getProximoComparecimento().isBefore(hoje)) {
                pessoa.setStatus(StatusComparecimento.INADIMPLENTE);
                custodiadoRepository.save(pessoa);
                contadorCustodiados++;
            }
        }

        if (contadorProcessos > 0 || contadorCustodiados > 0) {
            log.info("Verificação concluída: {} processos e {} custodiados marcados como inadimplentes",
                    contadorProcessos, contadorCustodiados);
        }

        return Math.max(contadorProcessos, contadorCustodiados);
    }

    @Transactional
    public long verificarStatusManual() {
        log.info("Verificação MANUAL de status solicitada");
        return executarVerificacaoStatus();
    }

    /**
     * Reprocessa todos os processos ativos + custodiados (compatibilidade)
     */
    @Transactional
    public long reprocessarTodosStatus() {
        log.info("REPROCESSAMENTO COMPLETO de todos os status");
        LocalDate hoje = LocalDate.now();
        long inadimplentes = 0;

        // Reprocessar processos
        List<Processo> todosProcessos = processoRepository.findAllAtivosComCustodiado();
        for (Processo processo : todosProcessos) {
            StatusComparecimento novoStatus = (processo.getProximoComparecimento() != null && processo.getProximoComparecimento().isBefore(hoje))
                    ? StatusComparecimento.INADIMPLENTE : StatusComparecimento.EM_CONFORMIDADE;

            if (!processo.getStatus().equals(novoStatus)) {
                processo.setStatus(novoStatus);
                processoRepository.save(processo);
                log.info("Processo {} status alterado para {}", processo.getNumeroProcesso(), novoStatus);
            }
            if (novoStatus == StatusComparecimento.INADIMPLENTE) inadimplentes++;
        }

        // Compatibilidade: reprocessar custodiados
        List<Custodiado> todosCustodiados = custodiadoRepository.findAll();
        for (Custodiado pessoa : todosCustodiados) {
            StatusComparecimento statusNovo = (pessoa.getProximoComparecimento() == null || !pessoa.getProximoComparecimento().isBefore(hoje))
                    ? StatusComparecimento.EM_CONFORMIDADE : StatusComparecimento.INADIMPLENTE;

            if (!pessoa.getStatus().equals(statusNovo)) {
                pessoa.setStatus(statusNovo);
                custodiadoRepository.save(pessoa);
            }
        }

        log.info("Reprocessamento concluído: {} processos inadimplentes", inadimplentes);
        return inadimplentes;
    }

    public StatusInfo obterStatusInfo() {
        // Usar ProcessoRepository para contadores principais
        long totalProcessos = processoRepository.countBySituacaoProcesso(
                br.jus.tjba.aclp.model.enums.SituacaoProcesso.ATIVO);
        long emConformidade = processoRepository.countByStatusAtivo(StatusComparecimento.EM_CONFORMIDADE);
        long inadimplentes = processoRepository.countByStatusAtivo(StatusComparecimento.INADIMPLENTE);

        // Fallback para custodiados se não houver processos migrados ainda
        if (totalProcessos == 0) {
            totalProcessos = custodiadoRepository.count();
            emConformidade = custodiadoRepository.countByStatus(StatusComparecimento.EM_CONFORMIDADE);
            inadimplentes = custodiadoRepository.countByStatus(StatusComparecimento.INADIMPLENTE);
        }

        return StatusInfo.builder()
                .totalCustodiados(totalProcessos)
                .emConformidade(emConformidade)
                .inadimplentes(inadimplentes)
                .dataConsulta(LocalDate.now())
                .percentualConformidade(totalProcessos > 0 ? (double) emConformidade / totalProcessos * 100 : 0.0)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class StatusInfo {
        private long totalCustodiados;
        private long emConformidade;
        private long inadimplentes;
        private LocalDate dataConsulta;
        private double percentualConformidade;
    }
}

package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.model.Pessoa;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.repository.PessoaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Serviço para atualização automática do status das pessoas
 * Executa verificações periódicas para identificar inadimplentes
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusSchedulerService {

    private final PessoaRepository pessoaRepository;

    /**
     * Executa TODOS OS DIAS à 01:00 da manhã
     * Verifica pessoas que ficaram inadimplentes
     */
    @Scheduled(cron = "0 0 1 * * *") // Segundos Minutos Horas Dia Mês DiaSemana
    @Transactional
    public void verificarStatusDiario() {
        log.info("🔄 Iniciando verificação automática diária de status - {}", LocalDate.now());

        long inadimplentesMarcados = executarVerificacaoStatus();

        if (inadimplentesMarcados > 0) {
            log.warn("⚠️  {} pessoas foram marcadas como INADIMPLENTES", inadimplentesMarcados);
        } else {
            log.info("✅ Nenhuma pessoa ficou inadimplente hoje");
        }
    }

    /**
     * Executa a cada 6 HORAS durante o dia
     * Para capturar mudanças durante o expediente
     */
    @Scheduled(cron = "0 0 */6 * * *") // A cada 6 horas: 00:00, 06:00, 12:00, 18:00
    @Transactional
    public void verificarStatusPeriodico() {
        log.debug("🔄 Verificação periódica de status - {}", LocalDate.now());
        executarVerificacaoStatus();
    }

    /**
     * Lógica principal de verificação de status
     */
    private long executarVerificacaoStatus() {
        LocalDate hoje = LocalDate.now();
        long contador = 0;

        // Buscar apenas pessoas EM_CONFORMIDADE (para otimizar)
        List<Pessoa> pessoasEmConformidade = pessoaRepository.findByStatus(StatusComparecimento.EM_CONFORMIDADE);

        log.debug("Verificando {} pessoas em conformidade", pessoasEmConformidade.size());

        for (Pessoa pessoa : pessoasEmConformidade) {
            if (pessoa.getProximoComparecimento() != null &&
                    pessoa.getProximoComparecimento().isBefore(hoje)) {

                // Pessoa está atrasada - marcar como inadimplente
                pessoa.setStatus(StatusComparecimento.INADIMPLENTE);
                pessoaRepository.save(pessoa);
                contador++;

                long diasAtraso = java.time.temporal.ChronoUnit.DAYS
                        .between(pessoa.getProximoComparecimento(), hoje);

                log.warn("❌ INADIMPLENTE: {} (ID: {}) - {} dias de atraso",
                        pessoa.getNome(), pessoa.getId(), diasAtraso);
            }
        }

        if (contador > 0) {
            log.info("✅ Verificação concluída: {} pessoas marcadas como inadimplentes", contador);
        }

        return contador;
    }

    /**
     * Método manual para forçar verificação (útil para testes)
     */
    @Transactional
    public long verificarStatusManual() {
        log.info("🔧 Verificação MANUAL de status solicitada");
        return executarVerificacaoStatus();
    }

    /**
     * Método para reprocessar TODAS as pessoas (útil após mudanças)
     */
    @Transactional
    public long reprocessarTodosStatus() {
        log.info("🔄 REPROCESSAMENTO COMPLETO de todos os status");

        LocalDate hoje = LocalDate.now();
        long emConformidade = 0;
        long inadimplentes = 0;

        List<Pessoa> todasPessoas = pessoaRepository.findAll();

        for (Pessoa pessoa : todasPessoas) {
            StatusComparecimento statusAnterior = pessoa.getStatus();
            StatusComparecimento statusNovo;

            // Determinar status correto baseado na data
            if (pessoa.getProximoComparecimento() == null) {
                statusNovo = StatusComparecimento.EM_CONFORMIDADE;
            } else if (pessoa.getProximoComparecimento().isBefore(hoje)) {
                statusNovo = StatusComparecimento.INADIMPLENTE;
                inadimplentes++;
            } else {
                statusNovo = StatusComparecimento.EM_CONFORMIDADE;
                emConformidade++;
            }

            // Atualizar apenas se mudou
            if (!statusAnterior.equals(statusNovo)) {
                pessoa.setStatus(statusNovo);
                pessoaRepository.save(pessoa);

                log.info("📝 Status alterado: {} (ID: {}) {} → {}",
                        pessoa.getNome(), pessoa.getId(), statusAnterior, statusNovo);
            }
        }

        log.info("✅ Reprocessamento concluído: {} em conformidade, {} inadimplentes",
                emConformidade, inadimplentes);

        return inadimplentes;
    }

    /**
     * Obter estatísticas de status das pessoas
     */
    public StatusInfo obterStatusInfo() {
        long totalPessoas = pessoaRepository.count();
        long emConformidade = pessoaRepository.countByStatus(StatusComparecimento.EM_CONFORMIDADE);
        long inadimplentes = pessoaRepository.countByStatus(StatusComparecimento.INADIMPLENTE);

        return StatusInfo.builder()
                .totalPessoas(totalPessoas)
                .emConformidade(emConformidade)
                .inadimplentes(inadimplentes)
                .dataConsulta(LocalDate.now())
                .percentualConformidade(totalPessoas > 0 ?
                        (double) emConformidade / totalPessoas * 100 : 0.0)
                .build();
    }

    /**
     * DTO para informações de status
     */
    @lombok.Data
    @lombok.Builder
    public static class StatusInfo {
        private long totalPessoas;
        private long emConformidade;
        private long inadimplentes;
        private LocalDate dataConsulta;
        private double percentualConformidade;
    }
}
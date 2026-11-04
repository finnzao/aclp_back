package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.ModalidadeExecucao;
import br.jus.tjba.aclp.model.enums.OrigemCalculo;
import br.jus.tjba.aclp.model.enums.SituacaoExecucao;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// 1:0..1 com Processo. Prazos pelo CP art. 10: o dia do inicio conta.
// Tratamento ambulatorial nao tem pena nem termino: campos de calculo ficam nulos.
@Entity
@Table(name = "execucoes_penais")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ExecucaoPenal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: converter para DTO dentro da transacao
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false, unique = true)
    @JsonIgnore
    private Processo processo;

    @NotBlank
    @Pattern(regexp = "\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}",
            message = "Número da execução (PEC) deve seguir o padrão CNJ: 0000000-00.0000.0.00.0000")
    @Column(name = "numero_execucao", nullable = false, unique = true, length = 25)
    private String numeroExecucao;

    @Size(max = 50)
    @Column(name = "guia_recolhimento", length = 50)
    private String guiaRecolhimento;

    // espelho informativo do SEEU, sem integracao
    @Size(max = 30)
    @Column(name = "numero_seeu", length = 30)
    private String numeroSeeu;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade", nullable = false, length = 30)
    @Builder.Default
    private ModalidadeExecucao modalidade = ModalidadeExecucao.ABERTO;

    @Column(name = "data_distribuicao")
    private LocalDate dataDistribuicao;

    @NotNull
    @Column(name = "data_inicio_cumprimento", nullable = false)
    private LocalDate dataInicioCumprimento;

    @Min(1)
    @Column(name = "pena_total_dias")
    private Integer penaTotalDias;

    @Column(name = "dias_detraidos", nullable = false)
    @Builder.Default
    private Integer diasDetraidos = 0;

    @Column(name = "dias_remidos", nullable = false)
    @Builder.Default
    private Integer diasRemidos = 0;

    // termino sem abatimentos; imutavel apos o registro
    @Column(name = "data_termino_original")
    private LocalDate dataTerminoOriginal;

    // materializada para a consulta D-60 usar indice
    @Column(name = "data_termino_prevista")
    private LocalDate dataTerminoPrevista;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_calculo", length = 30)
    private OrigemCalculo origemCalculo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "situacao_execucao", nullable = false, length = 30)
    @Builder.Default
    private SituacaoExecucao situacaoExecucao = SituacaoExecucao.EM_CUMPRIMENTO;

    @Size(max = 500)
    @Column(name = "observacoes", length = 500)
    private String observacoes;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    /** SENTENCA: data final -> pena em dias. */
    public static int calcularPenaDias(LocalDate inicio, LocalDate termino) {
        if (inicio == null || termino == null)
            throw new IllegalArgumentException("Data de início e de término são obrigatórias");
        if (termino.isBefore(inicio))
            throw new IllegalArgumentException("Data de término não pode ser anterior à de início");
        return (int) ChronoUnit.DAYS.between(inicio, termino) + 1;
    }

    /** DOSIMETRIA: anos/meses/dias -> data final pelo calendario. */
    public static LocalDate calcularTerminoPorTempo(LocalDate inicio, int anos, int meses, int dias) {
        if (inicio == null)
            throw new IllegalArgumentException("Data de início é obrigatória");
        if (anos < 0 || meses < 0 || dias < 0)
            throw new IllegalArgumentException("Tempo de pena não pode ter componente negativo");
        if (anos == 0 && meses == 0 && dias == 0)
            throw new IllegalArgumentException("Tempo de pena não pode ser zero");
        return inicio.plusYears(anos).plusMonths(meses).plusDays(dias).minusDays(1);
    }

    /** LEP 126: 3 dias trabalhados ou 12h de estudo = 1 dia remido. */
    public static int calcularRemicao(int diasTrabalhados, int horasEstudo) {
        if (diasTrabalhados < 0 || horasEstudo < 0)
            throw new IllegalArgumentException("Dias trabalhados e horas de estudo não podem ser negativos");
        return diasTrabalhados / 3 + horasEstudo / 12;
    }

    /** Soma remição já decidida; abatimentos nunca alcançam a pena inteira. */
    public void adicionarRemicao(int dias) {
        if (dias <= 0)
            throw new IllegalArgumentException("Remição deve ser de pelo menos 1 dia");
        if (!temPena())
            throw new IllegalArgumentException("Esta modalidade não tem pena em dias para remir");
        int total = (diasDetraidos != null ? diasDetraidos : 0)
                  + (diasRemidos != null ? diasRemidos : 0) + dias;
        if (total >= penaTotalDias)
            throw new IllegalArgumentException("Abatimentos (" + total
                    + " dias) não podem igualar ou superar a pena (" + penaTotalDias + " dias)");
        diasRemidos = (diasRemidos != null ? diasRemidos : 0) + dias;
        dataTerminoPrevista = calcularTerminoPrevisto();
    }

    // interrupcao ou atestado novo do SEEU: move a data original; abatimentos continuam valendo
    public void retificarTermino(LocalDate novoTermino) {
        if (novoTermino == null || novoTermino.isBefore(dataInicioCumprimento))
            throw new IllegalArgumentException("Novo término não pode ser anterior ao início do cumprimento");
        dataTerminoOriginal = novoTermino;
        penaTotalDias = calcularPenaDias(dataInicioCumprimento, novoTermino);
        dataTerminoPrevista = calcularTerminoPrevisto();
    }

    public boolean temPena() {
        return penaTotalDias != null && dataTerminoOriginal != null;
    }

    /** original - (detraidos + remidos). */
    public LocalDate calcularTerminoPrevisto() {
        if (dataTerminoOriginal == null) return null;
        int abatimento = (diasDetraidos != null ? diasDetraidos : 0)
                       + (diasRemidos  != null ? diasRemidos  : 0);
        return dataTerminoOriginal.minusDays(abatimento);
    }

    /** Incidente que exige justificacao suspende a execucao (LEP 118 p2). */
    public void aoRegistrarIncidente(br.jus.tjba.aclp.model.enums.TipoIncidente tipo) {
        if (tipo.isExigeJustificacao() && situacaoExecucao == SituacaoExecucao.EM_CUMPRIMENTO) {
            situacaoExecucao = SituacaoExecucao.AGUARDANDO_JUSTIFICACAO;
        }
    }

    /** Unica porta para REGREDIDA/EXTINTA: regressao exige incidente decidido. */
    public void aoDecidirIncidente(br.jus.tjba.aclp.model.enums.ResultadoIncidente resultado) {
        switch (resultado) {
            case REGRESSAO_DECRETADA -> {
                situacaoExecucao = SituacaoExecucao.REGREDIDA;
                encerrarCobrancaDoProcesso(br.jus.tjba.aclp.model.enums.SituacaoProcesso.SUSPENSO);
            }
            case EXTINCAO_DECLARADA -> {
                situacaoExecucao = SituacaoExecucao.EXTINTA;
                encerrarCobrancaDoProcesso(br.jus.tjba.aclp.model.enums.SituacaoProcesso.ENCERRADO);
            }
            case JUSTIFICADO, ADVERTENCIA, ARQUIVADO -> {
                if (situacaoExecucao == SituacaoExecucao.AGUARDANDO_JUSTIFICACAO) {
                    situacaoExecucao = SituacaoExecucao.EM_CUMPRIMENTO;
                }
            }
            case NAO_JUSTIFICADO, AGUARDANDO -> { /* segue aguardando decisão de regressão */ }
        }
    }

    // prisao em outro processo ou decisao judicial: para a cobranca sem mexer na pena
    public void suspender() {
        if (situacaoExecucao == SituacaoExecucao.EXTINTA || situacaoExecucao == SituacaoExecucao.REGREDIDA
                || situacaoExecucao == SituacaoExecucao.SUSPENSA)
            throw new IllegalArgumentException("Execução " + situacaoExecucao.getDescricao().toLowerCase() + " não pode ser suspensa");
        situacaoExecucao = SituacaoExecucao.SUSPENSA;
        encerrarCobrancaDoProcesso(br.jus.tjba.aclp.model.enums.SituacaoProcesso.SUSPENSO);
    }

    public void retomar() {
        if (situacaoExecucao != SituacaoExecucao.SUSPENSA)
            throw new IllegalArgumentException("Só execução suspensa pode ser retomada");
        situacaoExecucao = SituacaoExecucao.EM_CUMPRIMENTO;
        if (processo != null) {
            processo.setSituacaoProcesso(br.jus.tjba.aclp.model.enums.SituacaoProcesso.ATIVO);
            ajustarAgendaDoProcesso();
        }
    }

    // agenda so nas modalidades com comparecimento; o ciclo reinicia hoje
    public void ajustarAgendaDoProcesso() {
        if (processo == null) return;
        if (modalidade.isComparecimentoPeriodico() && processo.getPeriodicidade() != null) {
            if (processo.getProximoComparecimento() == null) {
                processo.setUltimoComparecimento(br.jus.tjba.aclp.util.TempoAtual.hoje());
                processo.calcularProximoComparecimento();
            }
        } else {
            processo.setProximoComparecimento(null);
        }
        processo.atualizarStatusBaseadoEmData();
    }

    // quem saiu do regime (preso ou pena quitada) nao pode continuar sendo cobrado
    private void encerrarCobrancaDoProcesso(br.jus.tjba.aclp.model.enums.SituacaoProcesso destino) {
        if (processo != null) {
            processo.setSituacaoProcesso(destino);
            processo.setProximoComparecimento(null);
        }
    }

    public long getDiasCumpridos() {
        LocalDate hoje = br.jus.tjba.aclp.util.TempoAtual.hoje();
        if (!temPena() || hoje.isBefore(dataInicioCumprimento)) return 0;
        return Math.min(ChronoUnit.DAYS.between(dataInicioCumprimento, hoje) + 1, penaTotalDias);
    }

    public long getDiasRestantes() {
        LocalDate hoje = br.jus.tjba.aclp.util.TempoAtual.hoje();
        if (dataTerminoPrevista == null) return 0;
        return hoje.isAfter(dataTerminoPrevista) ? 0
                : ChronoUnit.DAYS.between(hoje, dataTerminoPrevista);
    }

    @PrePersist
    public void prePersist() {
        if (criadoEm == null) criadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();
        if (version == null) version = 0L;
        if (situacaoExecucao == null) situacaoExecucao = SituacaoExecucao.EM_CUMPRIMENTO;
        if (modalidade == null) modalidade = ModalidadeExecucao.ABERTO;
        if (diasDetraidos == null) diasDetraidos = 0;
        if (diasRemidos == null) diasRemidos = 0;
        if (dataTerminoPrevista == null && dataTerminoOriginal != null)
            dataTerminoPrevista = calcularTerminoPrevisto();
    }

    @PreUpdate
    public void preUpdate() {
        atualizadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();
        dataTerminoPrevista = calcularTerminoPrevisto();
    }
}

package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "processos",
        indexes = {
                @Index(name = "idx_processo_custodiado", columnList = "custodiado_id"),
                @Index(name = "idx_processo_numero", columnList = "numero_processo"),
                @Index(name = "idx_processo_status", columnList = "status"),
                @Index(name = "idx_processo_situacao", columnList = "situacao_processo"),
                @Index(name = "idx_processo_proximo", columnList = "proximo_comparecimento"),
                @Index(name = "idx_processo_status_proximo", columnList = "status, proximo_comparecimento"),
                @Index(name = "idx_processo_custodiado_situacao", columnList = "custodiado_id, situacao_processo")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Processo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custodiado_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_processo_custodiado"))
    @JsonIgnore
    private Custodiado custodiado;

    @JsonProperty("custodiadoId")
    public Long getCustodiadoId() {
        return custodiado != null ? custodiado.getId() : null;
    }

    @JsonProperty("custodiadoNome")
    public String getCustodiadoNome() {
        return custodiado != null ? custodiado.getNome() : null;
    }

    @JsonProperty("custodiadoCpf")
    public String getCustodiadoCpf() {
        return custodiado != null ? custodiado.getCpf() : null;
    }

    @NotBlank(message = "Número do processo é obrigatório")
    @Column(name = "numero_processo", nullable = false, length = 25)
    private String numeroProcesso;

    @NotBlank(message = "Vara é obrigatória")
    @Size(max = 100)
    @Column(name = "vara", nullable = false, length = 100)
    private String vara;

    @NotBlank(message = "Comarca é obrigatória")
    @Size(max = 100)
    @Column(name = "comarca", nullable = false, length = 100)
    private String comarca;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Data da decisão é obrigatória")
    @PastOrPresent(message = "Data da decisão não pode ser futura")
    @Column(name = "data_decisao", nullable = false)
    private LocalDate dataDecisao;

    @NotNull(message = "Periodicidade é obrigatória")
    @Min(value = 1, message = "Periodicidade deve ser no mínimo 1 dia")
    @Max(value = 365, message = "Periodicidade não pode exceder 365 dias")
    @Column(name = "periodicidade", nullable = false)
    private Integer periodicidade;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Data do comparecimento inicial é obrigatória")
    @Column(name = "data_comparecimento_inicial", nullable = false)
    private LocalDate dataComparecimentoInicial;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StatusComparecimento status = StatusComparecimento.EM_CONFORMIDADE;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "ultimo_comparecimento")
    private LocalDate ultimoComparecimento;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Column(name = "proximo_comparecimento")
    private LocalDate proximoComparecimento;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "situacao_processo", nullable = false, length = 20)
    @Builder.Default
    private SituacaoProcesso situacaoProcesso = SituacaoProcesso.ATIVO;

    @Size(max = 500)
    @Column(name = "observacoes", length = 500)
    private String observacoes;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    // Historico de comparecimentos vinculado ao processo
    @OneToMany(mappedBy = "processo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("dataComparecimento DESC")
    @Builder.Default
    @JsonIgnore
    private List<HistoricoComparecimento> historicoComparecimentos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
        if (version == null) version = 0L;
        if (status == null) status = StatusComparecimento.EM_CONFORMIDADE;
        if (situacaoProcesso == null) situacaoProcesso = SituacaoProcesso.ATIVO;
        if (ultimoComparecimento == null) ultimoComparecimento = dataComparecimentoInicial;
        if (proximoComparecimento == null && situacaoProcesso.isAtivo()) calcularProximoComparecimento();
    }

    @PreUpdate
    public void preUpdate() {
        atualizadoEm = LocalDateTime.now();
        if (!situacaoProcesso.isAtivo()) proximoComparecimento = null;
    }

    public void calcularProximoComparecimento() {
        if (ultimoComparecimento != null && periodicidade != null && situacaoProcesso.isAtivo()) {
            proximoComparecimento = ultimoComparecimento.plusDays(periodicidade);
        }
    }

    public void atualizarStatusBaseadoEmData() {
        if (situacaoProcesso.isAtivo() && proximoComparecimento != null && proximoComparecimento.isBefore(LocalDate.now())) {
            status = StatusComparecimento.INADIMPLENTE;
        } else if (situacaoProcesso.isAtivo()) {
            status = StatusComparecimento.EM_CONFORMIDADE;
        }
    }

    public long getDiasAtraso() {
        if (proximoComparecimento == null || !situacaoProcesso.isAtivo()) return 0;
        LocalDate hoje = LocalDate.now();
        return hoje.isAfter(proximoComparecimento) ? ChronoUnit.DAYS.between(proximoComparecimento, hoje) : 0;
    }

    public boolean isInadimplente() {
        return situacaoProcesso.isAtivo() && (status == StatusComparecimento.INADIMPLENTE ||
                (proximoComparecimento != null && proximoComparecimento.isBefore(LocalDate.now())));
    }

    public boolean isComparecimentoHoje() {
        return situacaoProcesso.isAtivo() && proximoComparecimento != null &&
                proximoComparecimento.equals(LocalDate.now());
    }

    public String getPeriodicidadeDescricao() {
        if (periodicidade == null) return "Não definida";
        return switch (periodicidade) {
            case 7 -> "Semanal";
            case 15 -> "Quinzenal";
            case 30 -> "Mensal";
            case 60 -> "Bimensal";
            case 90 -> "Trimestral";
            case 180 -> "Semestral";
            default -> periodicidade + " dias";
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Processo processo = (Processo) o;
        return Objects.equals(id, processo.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}

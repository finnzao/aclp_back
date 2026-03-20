package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.TipoValidacao;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "historico_comparecimentos",
        indexes = {
                @Index(name = "idx_historico_custodiado", columnList = "custodiado_id"),
                @Index(name = "idx_historico_processo", columnList = "processo_id"),
                @Index(name = "idx_historico_data", columnList = "data_comparecimento"),
                @Index(name = "idx_historico_tipo", columnList = "tipo_validacao"),
                @Index(name = "idx_historico_custodiado_data", columnList = "custodiado_id, data_comparecimento DESC"),
                @Index(name = "idx_historico_processo_data", columnList = "processo_id, data_comparecimento DESC"),
                @Index(name = "idx_historico_mudanca_endereco", columnList = "mudanca_endereco"),
                @Index(name = "idx_historico_custodiado_mudanca", columnList = "custodiado_id, mudanca_endereco")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricoComparecimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NOVO: Referência principal agora é o Processo
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_id",
            foreignKey = @ForeignKey(name = "fk_comparecimento_processo"))
    @JsonIgnore
    private Processo processo;

    // MANTIDO TEMPORARIAMENTE para compatibilidade durante transição
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custodiado_id",
            foreignKey = @ForeignKey(name = "fk_historico_custodiado"))
    @JsonIgnore
    private Custodiado custodiado;

    // Navega via processo -> custodiado (caminho principal)
    @JsonProperty("custodiadoId")
    public Long getCustodiadoId() {
        if (processo != null && processo.getCustodiado() != null) return processo.getCustodiado().getId();
        return custodiado != null ? custodiado.getId() : null;
    }

    @JsonProperty("custodiadoNome")
    public String getCustodiadoNome() {
        if (processo != null && processo.getCustodiado() != null) return processo.getCustodiado().getNome();
        return custodiado != null ? custodiado.getNome() : null;
    }

    @JsonProperty("processoId")
    public Long getProcessoId() {
        return processo != null ? processo.getId() : null;
    }

    @JsonProperty("numeroProcesso")
    public String getNumeroProcesso() {
        return processo != null ? processo.getNumeroProcesso() : null;
    }

    @NotNull(message = "Data do comparecimento é obrigatória")
    @Column(name = "data_comparecimento", nullable = false)
    private LocalDate dataComparecimento;

    @JsonFormat(pattern = "HH:mm:ss")
    @Column(name = "hora_comparecimento")
    private LocalTime horaComparecimento;

    @NotNull(message = "Tipo de validação é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_validacao", nullable = false, length = 20)
    private TipoValidacao tipoValidacao;

    @NotBlank(message = "Validado por é obrigatório")
    @Size(max = 100)
    @Column(name = "validado_por", nullable = false, length = 100)
    private String validadoPor;

    @Size(max = 500)
    @Column(name = "observacoes", length = 500)
    private String observacoes;

    @Column(name = "anexos", columnDefinition = "TEXT")
    private String anexos;

    @Column(name = "mudanca_endereco", nullable = false)
    @Builder.Default
    private Boolean mudancaEndereco = Boolean.FALSE;

    @Size(max = 500)
    @Column(name = "motivo_mudanca_endereco", length = 500)
    private String motivoMudancaEndereco;

    @OneToMany(mappedBy = "historicoComparecimento", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<HistoricoEndereco> enderecosAlterados = new ArrayList<>();

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    public void prePersist() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
        if (version == null) version = 0L;
        if (mudancaEndereco == null) mudancaEndereco = Boolean.FALSE;
    }

    @PreUpdate
    public void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    public LocalDateTime getDataHoraComparecimento() {
        if (dataComparecimento == null) return null;
        return horaComparecimento != null ? dataComparecimento.atTime(horaComparecimento) : dataComparecimento.atStartOfDay();
    }

    public String getResumo() {
        StringBuilder resumo = new StringBuilder();
        resumo.append(dataComparecimento.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        resumo.append(" - ").append(tipoValidacao.getLabel());
        if (horaComparecimento != null) {
            resumo.append(" às ").append(horaComparecimento.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        }
        if (Boolean.TRUE.equals(mudancaEndereco)) resumo.append(" (com mudança de endereço)");
        return resumo.toString();
    }

    public boolean isComparecimentoVirtual() { return tipoValidacao != null && tipoValidacao.isVirtual(); }
    public boolean isComparecimentoPresencial() { return tipoValidacao != null && tipoValidacao.requerPresencaFisica(); }
    public boolean isCadastroInicial() { return tipoValidacao != null && tipoValidacao.isCadastroInicial(); }
    public boolean houveMudancaEndereco() { return Boolean.TRUE.equals(mudancaEndereco); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((HistoricoComparecimento) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}

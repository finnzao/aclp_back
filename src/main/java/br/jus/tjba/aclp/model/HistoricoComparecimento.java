package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.TipoValidacao;
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
                @Index(name = "idx_historico_pessoa", columnList = "pessoa_id"),
                @Index(name = "idx_historico_data", columnList = "data_comparecimento"),
                @Index(name = "idx_historico_tipo", columnList = "tipo_validacao"),
                @Index(name = "idx_historico_pessoa_data", columnList = "pessoa_id, data_comparecimento DESC"),
                @Index(name = "idx_historico_mudanca_endereco", columnList = "mudanca_endereco"),
                @Index(name = "idx_historico_pessoa_mudanca", columnList = "pessoa_id, mudanca_endereco")
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

    @NotNull(message = "Pessoa é obrigatória")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_historico_pessoa"))
    private Pessoa pessoa;

    @NotNull(message = "Data do comparecimento é obrigatória")
    @Column(name = "data_comparecimento", nullable = false)
    private LocalDate dataComparecimento;

    @Column(name = "hora_comparecimento")
    private LocalTime horaComparecimento;

    @NotNull(message = "Tipo de validação é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_validacao", nullable = false, length = 20)
    private TipoValidacao tipoValidacao;

    @NotBlank(message = "Validado por é obrigatório")
    @Size(max = 100, message = "Validado por deve ter no máximo 100 caracteres")
    @Column(name = "validado_por", nullable = false, length = 100)
    private String validadoPor;

    @Size(max = 500, message = "Observações deve ter no máximo 500 caracteres")
    @Column(name = "observacoes", length = 500)
    private String observacoes;

    @Column(name = "anexos", columnDefinition = "TEXT")
    private String anexos;

    // === NOVA FUNCIONALIDADE: CONTROLE DE MUDANÇA DE ENDEREÇO ===

    @Column(name = "mudanca_endereco", nullable = false)
    @Builder.Default
    private Boolean mudancaEndereco = Boolean.FALSE;

    @Size(max = 500, message = "Motivo da mudança deve ter no máximo 500 caracteres")
    @Column(name = "motivo_mudanca_endereco", length = 500)
    private String motivoMudancaEndereco;

    // === RELACIONAMENTOS ===

    @OneToMany(mappedBy = "historicoComparecimento", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<HistoricoEndereco> enderecosAlterados = new ArrayList<>();

    // === AUDITORIA ===

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    // === MÉTODOS DE CICLO DE VIDA ===

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.version == null) {
            this.version = 0L;
        }
        if (this.mudancaEndereco == null) {
            this.mudancaEndereco = Boolean.FALSE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    // === MÉTODOS UTILITÁRIOS ===

    public LocalDateTime getDataHoraComparecimento() {
        if (dataComparecimento == null) return null;
        if (horaComparecimento == null) return dataComparecimento.atStartOfDay();
        return dataComparecimento.atTime(horaComparecimento);
    }

    public String getResumo() {
        StringBuilder resumo = new StringBuilder();
        resumo.append(dataComparecimento.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        resumo.append(" - ").append(tipoValidacao.getLabel());

        if (horaComparecimento != null) {
            resumo.append(" às ").append(horaComparecimento.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (Boolean.TRUE.equals(mudancaEndereco)) {
            resumo.append(" (com mudança de endereço)");
        }

        return resumo.toString();
    }

    public boolean isComparecimentoVirtual() {
        return tipoValidacao != null && tipoValidacao.isVirtual();
    }

    public boolean isComparecimentoPresencial() {
        return tipoValidacao != null && tipoValidacao.requerPresencaFisica();
    }

    public boolean isCadastroInicial() {
        return tipoValidacao != null && tipoValidacao.isCadastroInicial();
    }

    public boolean isComparecimentoRegular() {
        return tipoValidacao != null && tipoValidacao.isComparecimentoRegular();
    }

    public boolean houveMudancaEndereco() {
        return Boolean.TRUE.equals(mudancaEndereco);
    }

    public boolean isComparecimentoAtrasado() {
        if (pessoa == null || dataComparecimento == null) return false;

        // Se é cadastro inicial, não pode estar atrasado
        if (isCadastroInicial()) return false;

        // Verifica se a data do comparecimento é posterior ao próximo comparecimento esperado
        LocalDate proximoEsperado = pessoa.getProximoComparecimento();
        return proximoEsperado != null && dataComparecimento.isAfter(proximoEsperado);
    }

    public long getDiasAtraso() {
        if (!isComparecimentoAtrasado()) return 0;

        LocalDate proximoEsperado = pessoa.getProximoComparecimento();
        return java.time.temporal.ChronoUnit.DAYS.between(proximoEsperado, dataComparecimento);
    }

    /**
     * Adiciona um histórico de endereço relacionado a este comparecimento
     */
    public void adicionarEnderecoAlterado(HistoricoEndereco historicoEndereco) {
        if (enderecosAlterados == null) {
            enderecosAlterados = new ArrayList<>();
        }
        enderecosAlterados.add(historicoEndereco);
        historicoEndereco.setHistoricoComparecimento(this);
        this.mudancaEndereco = Boolean.TRUE;
    }

    /**
     * Remove um histórico de endereço relacionado a este comparecimento
     */
    public void removerEnderecoAlterado(HistoricoEndereco historicoEndereco) {
        if (enderecosAlterados != null) {
            enderecosAlterados.remove(historicoEndereco);
            historicoEndereco.setHistoricoComparecimento(null);

            // Se não há mais endereços alterados, marca como false
            if (enderecosAlterados.isEmpty()) {
                this.mudancaEndereco = Boolean.FALSE;
                this.motivoMudancaEndereco = null;
            }
        }
    }

    /**
     * Retorna o total de endereços alterados neste comparecimento
     */
    public int getTotalEnderecosAlterados() {
        return enderecosAlterados != null ? enderecosAlterados.size() : 0;
    }

    /**
     * Verifica se este comparecimento teve mudanças de endereço
     */
    public boolean temEnderecosAlterados() {
        return enderecosAlterados != null && !enderecosAlterados.isEmpty();
    }

    /**
     * Retorna uma descrição detalhada do comparecimento
     */
    public String getDescricaoCompleta() {
        StringBuilder desc = new StringBuilder();
        desc.append("Comparecimento ").append(tipoValidacao.getLabel().toLowerCase());
        desc.append(" em ").append(dataComparecimento.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        if (horaComparecimento != null) {
            desc.append(" às ").append(horaComparecimento.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        }

        if (houveMudancaEndereco()) {
            desc.append(". Houve mudança de endereço");
            if (motivoMudancaEndereco != null && !motivoMudancaEndereco.trim().isEmpty()) {
                desc.append(" (").append(motivoMudancaEndereco).append(")");
            }
        }

        if (observacoes != null && !observacoes.trim().isEmpty()) {
            desc.append(". Obs: ").append(observacoes);
        }

        return desc.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoricoComparecimento that = (HistoricoComparecimento) o;
        return Objects.equals(pessoa, that.pessoa) &&
                Objects.equals(dataComparecimento, that.dataComparecimento) &&
                Objects.equals(horaComparecimento, that.horaComparecimento) &&
                Objects.equals(tipoValidacao, that.tipoValidacao);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pessoa, dataComparecimento, horaComparecimento, tipoValidacao);
    }
}
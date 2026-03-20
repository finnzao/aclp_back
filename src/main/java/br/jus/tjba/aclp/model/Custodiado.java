package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.SituacaoCustodiado;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "custodiados",
        indexes = {
                @Index(name = "idx_custodiado_cpf", columnList = "cpf"),
                @Index(name = "idx_custodiado_rg", columnList = "rg"),
                @Index(name = "idx_custodiado_situacao", columnList = "situacao"),
                @Index(name = "idx_custodiado_nome", columnList = "nome")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Custodiado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 150)
    @Pattern(regexp = "^[A-Za-zÀ-ÿ\\s'.-]+$", message = "Nome deve conter apenas letras e caracteres válidos")
    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Pattern(regexp = "\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}", message = "CPF deve ter o formato 000.000.000-00")
    @Column(name = "cpf", length = 14)
    private String cpf;

    @Size(max = 20)
    @Column(name = "rg", length = 20)
    private String rg;

    @NotBlank(message = "Contato é obrigatório")
    @Pattern(regexp = "\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}", message = "Contato deve ter formato válido")
    @Column(name = "contato", nullable = false, length = 20)
    private String contato;

    // Situação do custodiado no sistema (ATIVO/ARQUIVADO)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 20)
    @Builder.Default
    private SituacaoCustodiado situacao = SituacaoCustodiado.ATIVO;

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

    // ========== CAMPOS PROCESSUAIS MANTIDOS TEMPORARIAMENTE ==========
    // Serão removidos após migração completa do frontend (Passo 5 do SQL)

    @Column(name = "processo", length = 25)
    private String processo;

    @Column(name = "vara", length = 100)
    private String vara;

    @Column(name = "comarca", length = 100)
    private String comarca;

    @Column(name = "data_decisao")
    private java.time.LocalDate dataDecisao;

    @Column(name = "periodicidade")
    private Integer periodicidade;

    @Column(name = "data_comparecimento_inicial")
    private java.time.LocalDate dataComparecimentoInicial;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private br.jus.tjba.aclp.model.enums.StatusComparecimento status;

    @Column(name = "ultimo_comparecimento")
    private java.time.LocalDate ultimoComparecimento;

    @Column(name = "proximo_comparecimento")
    private java.time.LocalDate proximoComparecimento;

    // ========== FIM CAMPOS TEMPORÁRIOS ==========

    // NOVO: Relação OneToMany com Processo
    @OneToMany(mappedBy = "custodiado", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore
    private List<Processo> processos = new ArrayList<>();

    // Mantido: Histórico de comparecimentos (temporário, será via Processo)
    @OneToMany(mappedBy = "custodiado", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("dataComparecimento DESC")
    @Builder.Default
    @JsonIgnore
    private List<HistoricoComparecimento> historicoComparecimentos = new ArrayList<>();

    @OneToMany(mappedBy = "custodiado", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("dataInicio DESC")
    @Builder.Default
    @JsonIgnore
    private List<HistoricoEndereco> historicoEnderecos = new ArrayList<>();

    @AssertTrue(message = "Pelo menos CPF ou RG deve ser informado")
    public boolean isDocumentosValidos() {
        return (cpf != null && !cpf.trim().isEmpty()) || (rg != null && !rg.trim().isEmpty());
    }

    @PrePersist
    public void prePersist() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
        if (version == null) version = 0L;
        if (situacao == null) situacao = SituacaoCustodiado.ATIVO;
        // Campos processuais temporários
        if (status == null) status = br.jus.tjba.aclp.model.enums.StatusComparecimento.EM_CONFORMIDADE;
        if (ultimoComparecimento == null && dataComparecimentoInicial != null) ultimoComparecimento = dataComparecimentoInicial;
        if (proximoComparecimento == null && situacao.isAtivo() && periodicidade != null) calcularProximoComparecimento();
    }

    @PreUpdate
    public void preUpdate() {
        atualizadoEm = LocalDateTime.now();
        if (situacao.isArquivado()) proximoComparecimento = null;
    }

    public void setCpf(String cpf) {
        if (cpf != null) {
            String digits = cpf.replaceAll("[^\\d]", "");
            if (digits.length() == 11) {
                this.cpf = digits.substring(0, 3) + "." + digits.substring(3, 6) + "." +
                        digits.substring(6, 9) + "-" + digits.substring(9);
            } else {
                this.cpf = cpf;
            }
        } else {
            this.cpf = null;
        }
    }

    public void setNome(String nome) {
        this.nome = nome != null ? nome.trim() : null;
    }

    // Métodos de cálculo TEMPORÁRIOS (mantidos para compatibilidade durante transição)
    public void calcularProximoComparecimento() {
        if (ultimoComparecimento != null && periodicidade != null && situacao.isAtivo()) {
            proximoComparecimento = ultimoComparecimento.plusDays(periodicidade);
        }
    }

    public void atualizarStatusBaseadoEmData() {
        if (situacao.isAtivo() && proximoComparecimento != null && proximoComparecimento.isBefore(java.time.LocalDate.now())) {
            status = br.jus.tjba.aclp.model.enums.StatusComparecimento.INADIMPLENTE;
        } else if (situacao.isAtivo()) {
            status = br.jus.tjba.aclp.model.enums.StatusComparecimento.EM_CONFORMIDADE;
        }
    }

    public long getDiasAtraso() {
        if (proximoComparecimento == null || situacao.isArquivado()) return 0;
        java.time.LocalDate hoje = java.time.LocalDate.now();
        return hoje.isAfter(proximoComparecimento) ?
                java.time.temporal.ChronoUnit.DAYS.between(proximoComparecimento, hoje) : 0;
    }

    public boolean isInadimplente() {
        return situacao.isAtivo() && (status == br.jus.tjba.aclp.model.enums.StatusComparecimento.INADIMPLENTE ||
                (proximoComparecimento != null && proximoComparecimento.isBefore(java.time.LocalDate.now())));
    }

    public boolean isComparecimentoHoje() {
        return situacao.isAtivo() && proximoComparecimento != null && proximoComparecimento.equals(java.time.LocalDate.now());
    }

    public String getPeriodicidadeDescricao() {
        if (periodicidade == null) return "Não definida";
        return switch (periodicidade) {
            case 7 -> "Semanal"; case 15 -> "Quinzenal"; case 30 -> "Mensal";
            case 60 -> "Bimensal"; case 90 -> "Trimestral"; case 180 -> "Semestral";
            default -> periodicidade + " dias";
        };
    }

    // Situação
    public boolean isAtivo() { return situacao != null && situacao.isAtivo(); }
    public boolean isArquivado() { return situacao != null && situacao.isArquivado(); }

    public void arquivar() {
        situacao = SituacaoCustodiado.ARQUIVADO;
        proximoComparecimento = null;
        atualizadoEm = LocalDateTime.now();
    }

    public void reativar() {
        situacao = SituacaoCustodiado.ATIVO;
        calcularProximoComparecimento();
        atualizarStatusBaseadoEmData();
        atualizadoEm = LocalDateTime.now();
    }

    public boolean podeSerExcluidoFisicamente() {
        return (historicoComparecimentos == null || historicoComparecimentos.isEmpty()) &&
                (historicoEnderecos == null || historicoEnderecos.isEmpty());
    }

    public HistoricoEndereco getEnderecoAtual() {
        return historicoEnderecos.stream().filter(HistoricoEndereco::isEnderecoAtivo).findFirst().orElse(null);
    }

    public String getEnderecoResumido() {
        if (historicoEnderecos == null || historicoEnderecos.isEmpty()) return "Endereço não carregado";
        HistoricoEndereco ativo = getEnderecoAtual();
        return ativo != null ? ativo.getEnderecoResumido() : "Sem endereço ativo";
    }

    public String getEnderecoCompleto() {
        if (historicoEnderecos == null || historicoEnderecos.isEmpty()) return "Endereço não informado";
        HistoricoEndereco ativo = getEnderecoAtual();
        return ativo != null ? ativo.getEnderecoCompleto() : "Endereço não informado";
    }

    public String getCidadeEstado() {
        if (historicoEnderecos == null || historicoEnderecos.isEmpty()) return "Não informado";
        HistoricoEndereco ativo = getEnderecoAtual();
        return ativo != null ? ativo.getCidade() + " - " + ativo.getEstado() : "Não informado";
    }

    public String getIdentificacao() {
        if (cpf != null && !cpf.trim().isEmpty()) return "CPF: " + cpf;
        if (rg != null && !rg.trim().isEmpty()) return "RG: " + rg;
        return "Sem documento";
    }

    public void adicionarHistorico(HistoricoComparecimento historico) {
        if (historicoComparecimentos == null) historicoComparecimentos = new ArrayList<>();
        historicoComparecimentos.add(historico);
        historico.setCustodiado(this);
    }

    public void adicionarHistoricoEndereco(HistoricoEndereco historicoEndereco) {
        if (historicoEnderecos == null) historicoEnderecos = new ArrayList<>();
        historicoEnderecos.add(historicoEndereco);
        historicoEndereco.setCustodiado(this);
    }

    public String getResumo() {
        return String.format("%s - %s - %s", nome, getIdentificacao(), situacao.getLabel());
    }

    public String getSituacaoDescricao() {
        return situacao != null ? situacao.getLabel() : "Não definida";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((Custodiado) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}

package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "pessoas",
        indexes = {
                @Index(name = "idx_pessoa_cpf", columnList = "cpf"),
                @Index(name = "idx_pessoa_rg", columnList = "rg"),
                @Index(name = "idx_pessoa_processo", columnList = "processo"),
                @Index(name = "idx_pessoa_status", columnList = "status"),
                @Index(name = "idx_pessoa_proximo_comparecimento", columnList = "proximo_comparecimento"),
                @Index(name = "idx_pessoa_status_proximo", columnList = "status, proximo_comparecimento"),
                @Index(name = "idx_pessoa_comarca_status", columnList = "comarca, status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pessoa_cpf", columnNames = "cpf"),
                @UniqueConstraint(name = "uk_pessoa_rg", columnNames = "rg")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Pessoa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 150, message = "Nome deve ter entre 2 e 150 caracteres")
    @Pattern(regexp = "^[A-Za-zÀ-ÿ\\s'.-]+$", message = "Nome deve conter apenas letras, espaços e caracteres especiais válidos")
    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Pattern(regexp = "\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}", message = "CPF deve ter o formato 000.000.000-00")
    @Column(name = "cpf", unique = true, length = 14)
    private String cpf;

    @Size(max = 20, message = "RG deve ter no máximo 20 caracteres")
    @Column(name = "rg", unique = true, length = 20)
    private String rg;

    @NotBlank(message = "Contato é obrigatório")
    @Pattern(regexp = "\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}", message = "Contato deve ter formato válido de telefone")
    @Column(name = "contato", nullable = false, length = 20)
    private String contato;

    @NotBlank(message = "Processo é obrigatório")
    @Pattern(regexp = "\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}",
            message = "Processo deve ter o formato 0000000-00.0000.0.00.0000")
    @Column(name = "processo", nullable = false, length = 25)
    private String processo;

    @NotBlank(message = "Vara é obrigatória")
    @Size(max = 100, message = "Vara deve ter no máximo 100 caracteres")
    @Column(name = "vara", nullable = false, length = 100)
    private String vara;

    @NotBlank(message = "Comarca é obrigatória")
    @Size(max = 100, message = "Comarca deve ter no máximo 100 caracteres")
    @Column(name = "comarca", nullable = false, length = 100)
    private String comarca;

    @NotNull(message = "Data da decisão é obrigatória")
    @PastOrPresent(message = "Data da decisão não pode ser futura")
    @Column(name = "data_decisao", nullable = false)
    private LocalDate dataDecisao;

    @NotNull(message = "Periodicidade é obrigatória")
    @Min(value = 1, message = "Periodicidade deve ser maior que zero")
    @Max(value = 365, message = "Periodicidade não pode ser maior que 365 dias")
    @Column(name = "periodicidade", nullable = false)
    private Integer periodicidade;

    @NotNull(message = "Data do comparecimento inicial é obrigatória")
    @Column(name = "data_comparecimento_inicial", nullable = false)
    private LocalDate dataComparecimentoInicial;

    @NotNull(message = "Status é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusComparecimento status;

    @NotNull(message = "Primeiro comparecimento é obrigatório")
    @Column(name = "primeiro_comparecimento", nullable = false)
    private LocalDate primeiroComparecimento;

    @NotNull(message = "Último comparecimento é obrigatório")
    @Column(name = "ultimo_comparecimento", nullable = false)
    private LocalDate ultimoComparecimento;

    @NotNull(message = "Próximo comparecimento é obrigatório")
    @Column(name = "proximo_comparecimento", nullable = false)
    private LocalDate proximoComparecimento;

    @Valid
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "endereco_id", foreignKey = @ForeignKey(name = "fk_pessoa_endereco"))
    private Endereco endereco;

    @Size(max = 500, message = "Observações deve ter no máximo 500 caracteres")
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

    @OneToMany(mappedBy = "pessoa", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("dataComparecimento DESC")
    @Builder.Default
    private List<HistoricoComparecimento> historicoComparecimentos = new ArrayList<>();

    @AssertTrue(message = "Pelo menos CPF ou RG deve ser informado")
    public boolean isDocumentosValidos() {
        return (cpf != null && !cpf.trim().isEmpty()) ||
                (rg != null && !rg.trim().isEmpty());
    }

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.version == null) {
            this.version = 0L;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public void setCpf(String cpf) {
        if (cpf != null) {
            String digits = cpf.replaceAll("[^\\d]", "");
            if (digits.length() == 11) {
                this.cpf = digits.substring(0, 3) + "." +
                        digits.substring(3, 6) + "." +
                        digits.substring(6, 9) + "-" +
                        digits.substring(9);
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

    public void calcularProximoComparecimento() {
        if (ultimoComparecimento != null && periodicidade != null) {
            this.proximoComparecimento = ultimoComparecimento.plusDays(periodicidade);
        }
    }

    public long getDiasAtraso() {
        if (proximoComparecimento == null) return 0;
        LocalDate hoje = LocalDate.now();
        return hoje.isAfter(proximoComparecimento) ?
                ChronoUnit.DAYS.between(proximoComparecimento, hoje) : 0;
    }

    public boolean isComparecimentoHoje() {
        return proximoComparecimento != null &&
                proximoComparecimento.equals(LocalDate.now());
    }

    public boolean isAtrasado() {
        return proximoComparecimento != null &&
                proximoComparecimento.isBefore(LocalDate.now());
    }

    public boolean isProximoComparecimento(int dias) {
        if (proximoComparecimento == null) return false;
        LocalDate limite = LocalDate.now().plusDays(dias);
        return !proximoComparecimento.isBefore(LocalDate.now()) &&
                !proximoComparecimento.isAfter(limite);
    }

    public String getIdentificacao() {
        if (cpf != null && !cpf.trim().isEmpty()) {
            return "CPF: " + cpf;
        } else if (rg != null && !rg.trim().isEmpty()) {
            return "RG: " + rg;
        }
        return "Sem documento";
    }

    public String getPeriodicidadeDescricao() {
        if (periodicidade == null) return "Não definida";
        if (periodicidade == 7) return "Semanal";
        if (periodicidade == 15) return "Quinzenal";
        if (periodicidade == 30) return "Mensal";
        if (periodicidade == 60) return "Bimensal";
        if (periodicidade == 90) return "Trimestral";
        if (periodicidade == 180) return "Semestral";
        return periodicidade + " dias";
    }

    public void adicionarHistorico(HistoricoComparecimento historico) {
        if (historicoComparecimentos == null) {
            historicoComparecimentos = new ArrayList<>();
        }
        historicoComparecimentos.add(historico);
        historico.setPessoa(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pessoa pessoa = (Pessoa) o;
        return Objects.equals(processo, pessoa.processo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(processo);
    }
}
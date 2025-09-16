package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@Table(name = "custodiados",
        indexes = {
                @Index(name = "idx_custodiado_cpf", columnList = "cpf"),
                @Index(name = "idx_custodiado_rg", columnList = "rg"),
                @Index(name = "idx_custodiado_processo", columnList = "processo"),
                @Index(name = "idx_custodiado_status", columnList = "status"),
                @Index(name = "idx_custodiado_proximo_comparecimento", columnList = "proximo_comparecimento"),
                @Index(name = "idx_custodiado_status_proximo", columnList = "status, proximo_comparecimento"),
                @Index(name = "idx_custodiado_comarca_status", columnList = "comarca, status")
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
    @Size(min = 2, max = 150, message = "Nome deve ter entre 2 e 150 caracteres")
    @Pattern(regexp = "^[A-Za-zÀ-ÿ\\s'.-]+$", message = "Nome deve conter apenas letras, espaços e caracteres especiais válidos")
    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Pattern(regexp = "\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}", message = "CPF deve ter o formato 000.000.000-00")
    @Column(name = "cpf", length = 14)
    private String cpf;

    @Size(max = 20, message = "RG deve ter no máximo 20 caracteres")
    @Column(name = "rg", length = 20)
    private String rg;

    @NotBlank(message = "Contato é obrigatório")
    @Pattern(regexp = "\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}", message = "Contato deve ter formato válido de telefone")
    @Column(name = "contato", nullable = false, length = 20)
    private String contato;

    // Processo não é mais único - vários custodiados podem ter o mesmo processo
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

    // Campos mantidos para performance (atualizados ao registrar comparecimento)
    @NotNull(message = "Status é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusComparecimento status;

    @Column(name = "ultimo_comparecimento")
    private LocalDate ultimoComparecimento;

    @Column(name = "proximo_comparecimento")
    private LocalDate proximoComparecimento;

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

    // Relacionamentos - IGNORAR na serialização JSON para evitar referências circulares
    @OneToMany(mappedBy = "custodiado", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("dataComparecimento DESC")
    @Builder.Default
    @JsonIgnore // IMPORTANTE: Ignora na serialização JSON
    private List<HistoricoComparecimento> historicoComparecimentos = new ArrayList<>();

    @OneToMany(mappedBy = "custodiado", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("dataInicio DESC")
    @Builder.Default
    @JsonIgnore // IMPORTANTE: Ignora na serialização JSON
    private List<HistoricoEndereco> historicoEnderecos = new ArrayList<>();

    // Validações
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
        if (this.status == null) {
            this.status = StatusComparecimento.EM_CONFORMIDADE;
        }

        // Inicializar datas se for o primeiro cadastro
        if (this.ultimoComparecimento == null) {
            this.ultimoComparecimento = this.dataComparecimentoInicial;
        }
        if (this.proximoComparecimento == null) {
            calcularProximoComparecimento();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    // Métodos de formatação
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

    // Métodos de cálculo
    public void calcularProximoComparecimento() {
        if (ultimoComparecimento != null && periodicidade != null) {
            this.proximoComparecimento = ultimoComparecimento.plusDays(periodicidade);
        }
    }

    public void atualizarStatusBaseadoEmData() {
        if (proximoComparecimento != null && proximoComparecimento.isBefore(LocalDate.now())) {
            this.status = StatusComparecimento.INADIMPLENTE;
        } else {
            this.status = StatusComparecimento.EM_CONFORMIDADE;
        }
    }

    public long getDiasAtraso() {
        if (proximoComparecimento == null) return 0;
        LocalDate hoje = LocalDate.now();
        return hoje.isAfter(proximoComparecimento) ?
                ChronoUnit.DAYS.between(proximoComparecimento, hoje) : 0;
    }

    public boolean isInadimplente() {
        return status == StatusComparecimento.INADIMPLENTE ||
                (proximoComparecimento != null && proximoComparecimento.isBefore(LocalDate.now()));
    }

    public boolean isComparecimentoHoje() {
        return proximoComparecimento != null &&
                proximoComparecimento.equals(LocalDate.now());
    }

    public boolean isProximoComparecimento(int dias) {
        if (proximoComparecimento == null) return false;
        LocalDate limite = LocalDate.now().plusDays(dias);
        return !proximoComparecimento.isBefore(LocalDate.now()) &&
                !proximoComparecimento.isAfter(limite);
    }

    // Métodos para endereço - SEM carregar relacionamentos automaticamente
    public HistoricoEndereco getEnderecoAtual() {
        // Não acessa a lista diretamente para evitar lazy loading
        // Este método deve ser usado apenas quando a lista já foi carregada
        return historicoEnderecos.stream()
                .filter(HistoricoEndereco::isEnderecoAtivo)
                .findFirst()
                .orElse(null);
    }

    /**
     * Retorna o endereço atual em formato resumido.
     * Evita lazy loading ao verificar se a lista já foi inicializada.
     *
     * @return String com endereço resumido ou mensagem padrão
     */
    public String getEnderecoResumido() {
        // Verificar se a lista de endereços foi inicializada para evitar lazy loading
        if (historicoEnderecos == null || historicoEnderecos.isEmpty()) {
            return "Endereço não carregado";
        }

        // Buscar endereço ativo
        HistoricoEndereco enderecoAtivo = historicoEnderecos.stream()
                .filter(HistoricoEndereco::isEnderecoAtivo)
                .findFirst()
                .orElse(null);

        if (enderecoAtivo != null) {
            return enderecoAtivo.getEnderecoResumido();
        }

        return "Sem endereço ativo";
    }

    /**
     * Retorna o endereço completo atual.
     * Evita lazy loading ao verificar se a lista já foi inicializada.
     *
     * @return String com endereço completo ou mensagem padrão
     */
    public String getEnderecoCompleto() {
        // Verificar se a lista de endereços foi inicializada para evitar lazy loading
        if (historicoEnderecos == null || historicoEnderecos.isEmpty()) {
            return "Endereço não informado";
        }

        // Buscar endereço ativo
        HistoricoEndereco enderecoAtivo = historicoEnderecos.stream()
                .filter(HistoricoEndereco::isEnderecoAtivo)
                .findFirst()
                .orElse(null);

        if (enderecoAtivo != null) {
            return enderecoAtivo.getEnderecoCompleto();
        }

        return "Endereço não informado";
    }

    /**
     * Retorna cidade e estado do endereço atual.
     * Evita lazy loading ao verificar se a lista já foi inicializada.
     *
     * @return String no formato "Cidade - Estado" ou mensagem padrão
     */
    public String getCidadeEstado() {
        // Verificar se a lista de endereços foi inicializada para evitar lazy loading
        if (historicoEnderecos == null || historicoEnderecos.isEmpty()) {
            return "Não informado";
        }

        // Buscar endereço ativo
        HistoricoEndereco enderecoAtivo = historicoEnderecos.stream()
                .filter(HistoricoEndereco::isEnderecoAtivo)
                .findFirst()
                .orElse(null);

        if (enderecoAtivo != null) {
            return enderecoAtivo.getCidade() + " - " + enderecoAtivo.getEstado();
        }

        return "Não informado";
    }

    // Métodos auxiliares que NÃO dependem dos relacionamentos
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
        historico.setCustodiado(this);
    }

    public void adicionarHistoricoEndereco(HistoricoEndereco historicoEndereco) {
        if (historicoEnderecos == null) {
            historicoEnderecos = new ArrayList<>();
        }
        historicoEnderecos.add(historicoEndereco);
        historicoEndereco.setCustodiado(this);
    }

    public String getResumo() {
        return String.format("%s - %s",
                nome,
                getIdentificacao());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Custodiado custodiado = (Custodiado) o;
        return Objects.equals(id, custodiado.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
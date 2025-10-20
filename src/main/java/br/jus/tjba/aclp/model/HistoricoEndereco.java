package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.EstadoBrasil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "historico_enderecos",
        indexes = {
                @Index(name = "idx_historico_endereco_custodiado", columnList = "custodiado_id"),
                @Index(name = "idx_historico_endereco_data_inicio", columnList = "data_inicio"),
                @Index(name = "idx_historico_endereco_data_fim", columnList = "data_fim"),
                @Index(name = "idx_historico_endereco_custodiado_periodo", columnList = "custodiado_id, data_inicio, data_fim"),
                @Index(name = "idx_historico_endereco_ativo", columnList = "custodiado_id, ativo"),
                @Index(name = "idx_historico_endereco_cep", columnList = "cep"),
                @Index(name = "idx_historico_endereco_cidade", columnList = "cidade"),
                @Index(name = "idx_historico_endereco_estado", columnList = "estado")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class HistoricoEndereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Custodiado é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custodiado_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_historico_endereco_custodiado"))
    private Custodiado custodiado;

    // Dados do endereço
    @NotBlank(message = "CEP é obrigatório")
    @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP deve ter o formato 00000-000")
    @Column(name = "cep", nullable = false, length = 9)
    private String cep;

    @NotBlank(message = "Logradouro é obrigatório")
    @Size(min = 5, max = 200, message = "Logradouro deve ter entre 5 e 200 caracteres")
    @Column(name = "logradouro", nullable = false, length = 200)
    private String logradouro;

    @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
    @Column(name = "numero", length = 20)
    private String numero;

    @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres")
    @Column(name = "complemento", length = 100)
    private String complemento;

    @NotBlank(message = "Bairro é obrigatório")
    @Size(min = 2, max = 100, message = "Bairro deve ter entre 2 e 100 caracteres")
    @Column(name = "bairro", nullable = false, length = 100)
    private String bairro;

    @NotBlank(message = "Cidade é obrigatória")
    @Size(min = 2, max = 100, message = "Cidade deve ter entre 2 e 100 caracteres")
    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;

    @NotBlank(message = "Estado é obrigatório")
    @Size(min = 2, max = 2, message = "Estado deve ter exatamente 2 caracteres")
    @Pattern(regexp = "[A-Z]{2}", message = "Estado deve ser uma sigla válida com 2 letras maiúsculas")
    @Column(name = "estado", nullable = false, length = 2)
    private String estado;

    // Controle temporal
    @NotNull(message = "Data de início é obrigatória")
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    // Flag para indicar endereço ativo (substitui tabela enderecos separada)
    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private Boolean ativo = Boolean.TRUE;

    @Size(max = 500, message = "Motivo da alteração deve ter no máximo 500 caracteres")
    @Column(name = "motivo_alteracao", length = 500)
    private String motivoAlteracao;

    @Size(max = 100, message = "Validado por deve ter no máximo 100 caracteres")
    @Column(name = "validado_por", length = 100)
    private String validadoPor;

    // Referência ao comparecimento quando houve mudança
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "historico_comparecimento_id",
            foreignKey = @ForeignKey(name = "fk_historico_endereco_comparecimento"))
    @JsonIgnore // ✅ CORREÇÃO: Evita referência circular com HistoricoComparecimento
    private HistoricoComparecimento historicoComparecimento;

    // Auditoria
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
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.version == null) {
            this.version = 0L;
        }
        if (this.ativo == null) {
            this.ativo = Boolean.TRUE;
        }
        validarEstado();
        formatarCep();
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
        validarEstado();
        formatarCep();
    }

    // Métodos de formatação
    public void setCep(String cep) {
        if (cep != null) {
            String digits = cep.replaceAll("[^\\d]", "");
            if (digits.length() == 8) {
                this.cep = digits.substring(0, 5) + "-" + digits.substring(5);
            } else {
                this.cep = cep;
            }
        } else {
            this.cep = null;
        }
    }

    public void setEstado(String estado) {
        if (estado != null) {
            String estadoLimpo = estado.trim().toUpperCase();

            if (!EstadoBrasil.isValidSigla(estadoLimpo)) {
                throw new IllegalArgumentException(
                        String.format("Estado '%s' é inválido. Estados válidos: %s",
                                estado, EstadoBrasil.getSiglasValidas())
                );
            }

            this.estado = estadoLimpo;
        } else {
            this.estado = null;
        }
    }

    private void validarEstado() {
        if (this.estado != null && !EstadoBrasil.isValidSigla(this.estado)) {
            throw new IllegalArgumentException(
                    String.format("Estado '%s' é inválido. Estados válidos: %s",
                            this.estado, EstadoBrasil.getSiglasValidas())
            );
        }
    }

    private void formatarCep() {
        if (this.cep != null) {
            setCep(this.cep);
        }
    }

    // Métodos utilitários
    public EstadoBrasil getEstadoBrasil() {
        return this.estado != null ? EstadoBrasil.fromString(this.estado) : null;
    }

    public void setEstadoBrasil(EstadoBrasil estadoBrasil) {
        this.estado = estadoBrasil != null ? estadoBrasil.getSigla() : null;
    }

    public String getEnderecoCompleto() {
        StringBuilder sb = new StringBuilder();
        sb.append(logradouro);
        if (numero != null && !numero.trim().isEmpty()) {
            sb.append(", ").append(numero);
        }
        if (complemento != null && !complemento.trim().isEmpty()) {
            sb.append(", ").append(complemento);
        }
        sb.append(", ").append(bairro);
        sb.append(", ").append(cidade).append(" - ").append(estado);
        if (cep != null) {
            sb.append(", CEP: ").append(cep);
        }
        return sb.toString();
    }

    public String getEnderecoResumido() {
        return String.format("%s, %s - %s",
                logradouro + (numero != null ? ", " + numero : ""),
                cidade,
                estado);
    }

    public String getCepSomenteNumeros() {
        return cep != null ? cep.replaceAll("[^\\d]", "") : null;
    }

    public String getNomeEstado() {
        EstadoBrasil estadoBrasil = getEstadoBrasil();
        return estadoBrasil != null ? estadoBrasil.getNome() : this.estado;
    }

    public String getRegiaoEstado() {
        EstadoBrasil estadoBrasil = getEstadoBrasil();
        return estadoBrasil != null ? estadoBrasil.getRegiao() : "Não identificada";
    }

    public boolean isEnderecoAtivo() {
        return Boolean.TRUE.equals(ativo);
    }

    public boolean isEnderecoHistorico() {
        return !isEnderecoAtivo();
    }

    public long getDiasResidencia() {
        LocalDate fim = dataFim != null ? dataFim : LocalDate.now();
        return java.time.temporal.ChronoUnit.DAYS.between(dataInicio, fim);
    }

    public String getPeriodoResidencia() {
        String inicio = dataInicio.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if (isEnderecoAtivo() || dataFim == null) {
            return "Desde " + inicio + " (atual)";
        } else {
            String fim = dataFim.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            return inicio + " até " + fim;
        }
    }

    public boolean isCompleto() {
        return cep != null && !cep.trim().isEmpty() &&
                logradouro != null && !logradouro.trim().isEmpty() &&
                bairro != null && !bairro.trim().isEmpty() &&
                cidade != null && !cidade.trim().isEmpty() &&
                estado != null && !estado.trim().isEmpty();
    }

    // Método para finalizar endereço quando houver mudança
    public void finalizarEndereco(LocalDate dataFim) {
        this.dataFim = dataFim;
        this.ativo = Boolean.FALSE;
    }

    /**
     * Método auxiliar para obter ID do comparecimento sem causar lazy loading
     * Usado quando precisamos saber se o endereço está vinculado a um comparecimento
     *
     * @return ID do comparecimento ou null se não vinculado
     */
    public Long getHistoricoComparecimentoId() {
        // Como o campo historicoComparecimento tem @JsonIgnore,
        // podemos acessar apenas o ID sem causar problemas de serialização
        return historicoComparecimento != null ? historicoComparecimento.getId() : null;
    }

    /**
     * Verifica se este endereço foi criado durante um comparecimento
     *
     * @return true se foi uma mudança durante comparecimento
     */
    public boolean isMudancaDuranteComparecimento() {
        return historicoComparecimento != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoricoEndereco that = (HistoricoEndereco) o;
        return Objects.equals(custodiado, that.custodiado) &&
                Objects.equals(cep, that.cep) &&
                Objects.equals(logradouro, that.logradouro) &&
                Objects.equals(numero, that.numero) &&
                Objects.equals(bairro, that.bairro) &&
                Objects.equals(cidade, that.cidade) &&
                Objects.equals(estado, that.estado) &&
                Objects.equals(dataInicio, that.dataInicio);
    }

    @Override
    public int hashCode() {
        return Objects.hash(custodiado, cep, logradouro, numero, bairro, cidade, estado, dataInicio);
    }
}
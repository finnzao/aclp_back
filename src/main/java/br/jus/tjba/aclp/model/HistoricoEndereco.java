package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.EstadoBrasil;
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
                @Index(name = "idx_historico_endereco_pessoa", columnList = "pessoa_id"),
                @Index(name = "idx_historico_endereco_data_inicio", columnList = "data_inicio"),
                @Index(name = "idx_historico_endereco_data_fim", columnList = "data_fim"),
                @Index(name = "idx_historico_endereco_pessoa_periodo", columnList = "pessoa_id, data_inicio, data_fim"),
                @Index(name = "idx_historico_endereco_ativo", columnList = "pessoa_id, data_fim"),
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

    @NotNull(message = "Pessoa é obrigatória")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_historico_endereco_pessoa"))
    private Pessoa pessoa;

    // === DADOS DO ENDEREÇO ===

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

    // === CONTROLE TEMPORAL ===

    @NotNull(message = "Data de início é obrigatória")
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Size(max = 500, message = "Motivo da alteração deve ter no máximo 500 caracteres")
    @Column(name = "motivo_alteracao", length = 500)
    private String motivoAlteracao;

    @Size(max = 100, message = "Validado por deve ter no máximo 100 caracteres")
    @Column(name = "validado_por", length = 100)
    private String validadoPor;

    // === REFERÊNCIA AO COMPARECIMENTO ===

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "historico_comparecimento_id",
            foreignKey = @ForeignKey(name = "fk_historico_endereco_comparecimento"))
    private HistoricoComparecimento historicoComparecimento;

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
        validarEstado();
        formatarCep();
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
        validarEstado();
        formatarCep();
    }

    // === MÉTODOS DE FORMATAÇÃO ===

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

    // === MÉTODOS UTILITÁRIOS ===

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
        return dataFim == null;
    }

    public boolean isEnderecoHistorico() {
        return dataFim != null;
    }

    public long getDiasResidencia() {
        LocalDate fim = dataFim != null ? dataFim : LocalDate.now();
        return java.time.temporal.ChronoUnit.DAYS.between(dataInicio, fim);
    }

    public String getPeriodoResidencia() {
        String inicio = dataInicio.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if (dataFim == null) {
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

    /**
     * Cria uma cópia deste endereço histórico para um novo endereço atual
     */
    public Endereco toEnderecoAtual() {
        return Endereco.builder()
                .cep(this.cep)
                .logradouro(this.logradouro)
                .numero(this.numero)
                .complemento(this.complemento)
                .bairro(this.bairro)
                .cidade(this.cidade)
                .estado(this.estado)
                .build();
    }

    /**
     * Cria um histórico a partir de um endereço atual
     */
    public static HistoricoEndereco fromEnderecoAtual(Endereco endereco, Pessoa pessoa, LocalDate dataInicio) {
        return HistoricoEndereco.builder()
                .pessoa(pessoa)
                .cep(endereco.getCep())
                .logradouro(endereco.getLogradouro())
                .numero(endereco.getNumero())
                .complemento(endereco.getComplemento())
                .bairro(endereco.getBairro())
                .cidade(endereco.getCidade())
                .estado(endereco.getEstado())
                .dataInicio(dataInicio)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoricoEndereco that = (HistoricoEndereco) o;
        return Objects.equals(pessoa, that.pessoa) &&
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
        return Objects.hash(pessoa, cep, logradouro, numero, bairro, cidade, estado, dataInicio);
    }
}
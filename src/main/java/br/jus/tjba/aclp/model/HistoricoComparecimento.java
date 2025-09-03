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
import java.util.Objects;

@Entity
@Table(name = "historico_comparecimentos",
        indexes = {
                @Index(name = "idx_historico_pessoa", columnList = "pessoa_id"),
                @Index(name = "idx_historico_data", columnList = "data_comparecimento"),
                @Index(name = "idx_historico_tipo", columnList = "tipo_validacao"),
                @Index(name = "idx_historico_pessoa_data", columnList = "pessoa_id, data_comparecimento DESC")
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

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public LocalDateTime getDataHoraComparecimento() {
        if (dataComparecimento == null) return null;
        if (horaComparecimento == null) return dataComparecimento.atStartOfDay();
        return dataComparecimento.atTime(horaComparecimento);
    }

    public String getResumo() {
        return String.format("%s - %s em %s",
                dataComparecimento,
                tipoValidacao.getLabel(),
                horaComparecimento != null ? horaComparecimento : "horário não informado");
    }

    public boolean isComparecimentoVirtual() {
        return tipoValidacao != null && tipoValidacao.isVirtual();
    }

    public boolean isJustificativa() {
        return tipoValidacao != null && tipoValidacao.isJustificativa();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoricoComparecimento that = (HistoricoComparecimento) o;
        return Objects.equals(pessoa, that.pessoa) &&
                Objects.equals(dataComparecimento, that.dataComparecimento) &&
                Objects.equals(horaComparecimento, that.horaComparecimento);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pessoa, dataComparecimento, horaComparecimento);
    }
}
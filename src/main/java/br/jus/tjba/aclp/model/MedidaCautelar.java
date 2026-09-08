package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.TipoMedidaCautelar;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medidas_cautelares",
        indexes = @Index(name = "idx_medida_processo", columnList = "processo_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedidaCautelar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "processo_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Processo processo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_medida", nullable = false, length = 40)
    private TipoMedidaCautelar tipoMedida;

    @Column(name = "data_imposicao", nullable = false)
    private LocalDate dataImposicao;

    @Column(name = "data_revogacao")
    private LocalDate dataRevogacao;

    @Column(name = "detalhe", length = 500)
    private String detalhe;

    @Column(name = "registrado_por", nullable = false, length = 100)
    private String registradoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();

    @Version
    @Builder.Default
    private Long version = 0L;

    public boolean isVigente() {
        return dataRevogacao == null;
    }

    public void revogar(LocalDate data) {
        if (data == null) throw new IllegalArgumentException("Data de revogação é obrigatória");
        if (data.isBefore(dataImposicao))
            throw new IllegalArgumentException("Revogação não pode ser anterior à imposição");
        if (dataRevogacao != null) throw new IllegalArgumentException("Medida já revogada");
        dataRevogacao = data;
    }
}

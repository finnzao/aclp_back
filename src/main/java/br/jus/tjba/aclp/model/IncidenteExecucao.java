package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.ResultadoIncidente;
import br.jus.tjba.aclp.model.enums.TipoIncidente;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Incidente da execucao (LEP 118 p2). O sistema registra; decisao e do juizo,
 * lancada por ADMIN — nao ha outro caminho para REGREDIDA/EXTINTA.
 */
@Entity
@Table(name = "incidentes_execucao")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidenteExecucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execucao_penal_id", nullable = false)
    @JsonIgnore
    private ExecucaoPenal execucaoPenal;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40)
    private TipoIncidente tipo;

    @NotNull
    @Column(name = "data_ocorrencia", nullable = false)
    private LocalDate dataOcorrencia;

    @NotBlank
    @Size(max = 1000)
    @Column(name = "descricao", nullable = false, length = 1000)
    private String descricao;

    // registrada, nunca designada
    @Column(name = "data_audiencia")
    private LocalDate dataAudiencia;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, length = 30)
    @Builder.Default
    private ResultadoIncidente resultado = ResultadoIncidente.AGUARDANDO;

    @Column(name = "data_decisao")
    private LocalDate dataDecisao;

    @NotBlank
    @Column(name = "registrado_por", nullable = false, length = 100)
    private String registradoPor;

    @Column(name = "decidido_por", length = 100)
    private String decididoPor;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    public boolean isDecidido() {
        return resultado != ResultadoIncidente.AGUARDANDO;
    }

    @PrePersist
    public void prePersist() {
        if (criadoEm == null) criadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();
        if (version == null) version = 0L;
        if (resultado == null) resultado = ResultadoIncidente.AGUARDANDO;
    }
}

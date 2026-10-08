package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.TipoProvidencia;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "providencias_execucao")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Providencia {

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
    private TipoProvidencia tipo;

    @Size(max = 500)
    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "prazo")
    private LocalDate prazo;

    @Column(name = "prioritaria", nullable = false)
    @Builder.Default
    private boolean prioritaria = false;

    @Column(name = "concluida_em")
    private LocalDate concluidaEm;

    @Column(name = "registrado_por", nullable = false, length = 100)
    private String registradoPor;

    @Column(name = "concluida_por", length = 100)
    private String concluidaPor;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();

    public boolean isPendente() { return concluidaEm == null; }

    public boolean isVencida() {
        return isPendente() && prazo != null && prazo.isBefore(br.jus.tjba.aclp.util.TempoAtual.hoje());
    }

    public void concluir(String por) {
        if (!isPendente()) throw new IllegalArgumentException("Providência já concluída");
        concluidaEm = br.jus.tjba.aclp.util.TempoAtual.hoje();
        concluidaPor = por;
    }
}

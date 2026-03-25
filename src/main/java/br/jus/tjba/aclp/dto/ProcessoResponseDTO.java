package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.model.enums.SituacaoProcesso;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO para resposta de Processo — evita LazyInitializationException.
 *
 * PROBLEMA RESOLVIDO:
 *   Processo#getCustodiadoCpf() / getCustodiadoNome() eram getters @JsonProperty
 *   em cima de um relacionamento @ManyToOne(fetch = LAZY). O Jackson tentava
 *   serializar esses campos fora da sessão Hibernate → LazyInitializationException.
 *
 * SOLUÇÃO:
 *   Converter para DTO dentro da transação (sessão ainda aberta) e retornar
 *   o DTO no lugar da entidade. Assim o Jackson só serializa POJOs simples.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessoResponseDTO {

    private Long id;

    // ---- Dados do custodiado (copiados enquanto a sessão está aberta) ----
    private Long custodiadoId;
    private String custodiadoNome;
    private String custodiadoCpf;

    // ---- Dados processuais ----
    private String numeroProcesso;
    private String vara;
    private String comarca;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataDecisao;

    private Integer periodicidade;
    private String periodicidadeDescricao;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataComparecimentoInicial;

    private StatusComparecimento status;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate ultimoComparecimento;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate proximoComparecimento;

    private SituacaoProcesso situacaoProcesso;
    private String observacoes;

    private Long diasAtraso;
    private boolean inadimplente;
    private boolean comparecimentoHoje;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime criadoEm;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime atualizadoEm;

    /**
     * Converte entidade → DTO dentro da sessão Hibernate (transação ativa).
     * Chame este método APENAS enquanto a sessão estiver aberta (dentro do service).
     */
    public static ProcessoResponseDTO fromEntity(Processo p) {
        if (p == null) return null;

        // Acesso ao custodiado aqui, enquanto a sessão ainda está aberta
        Long custId = null;
        String custNome = null;
        String custCpf = null;
        if (p.getCustodiado() != null) {
            custId   = p.getCustodiado().getId();
            custNome = p.getCustodiado().getNome();
            custCpf  = p.getCustodiado().getCpf();
        }

        return ProcessoResponseDTO.builder()
                .id(p.getId())
                .custodiadoId(custId)
                .custodiadoNome(custNome)
                .custodiadoCpf(custCpf)
                .numeroProcesso(p.getNumeroProcesso())
                .vara(p.getVara())
                .comarca(p.getComarca())
                .dataDecisao(p.getDataDecisao())
                .periodicidade(p.getPeriodicidade())
                .periodicidadeDescricao(p.getPeriodicidadeDescricao())
                .dataComparecimentoInicial(p.getDataComparecimentoInicial())
                .status(p.getStatus())
                .ultimoComparecimento(p.getUltimoComparecimento())
                .proximoComparecimento(p.getProximoComparecimento())
                .situacaoProcesso(p.getSituacaoProcesso())
                .observacoes(p.getObservacoes())
                .diasAtraso(p.getDiasAtraso())
                .inadimplente(p.isInadimplente())
                .comparecimentoHoje(p.isComparecimentoHoje())
                .criadoEm(p.getCriadoEm())
                .atualizadoEm(p.getAtualizadoEm())
                .build();
    }
}

package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.enums.SituacaoCustodiado;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustodiadoListDTO {

    private String id;
    private Long numericId;
    private String nome;
    private String cpf;
    private String rg;
    private String documentoExibicao;
    private String processo;
    private String vara;
    private String comarca;
    private StatusComparecimento status;
    private SituacaoCustodiado situacao;
    private LocalDate ultimoComparecimento;
    private LocalDate proximoComparecimento;
    private Long diasAtraso;
    private Integer periodicidade;
    private boolean inadimplente;
    private boolean comparecimentoHoje;
    private boolean urgente;

    public static CustodiadoListDTO fromEntity(Custodiado custodiado) {
        String documentoExibicao;
        if (custodiado.getCpf() != null && !custodiado.getCpf().trim().isEmpty()) {
            documentoExibicao = custodiado.getCpf();
        } else if (custodiado.getRg() != null && !custodiado.getRg().trim().isEmpty()) {
            documentoExibicao = custodiado.getRg();
        } else {
            documentoExibicao = "Sem documento";
        }

        long diasAtraso = custodiado.getDiasAtraso();
        Integer periodicidade = custodiado.getPeriodicidade();
        boolean urgente = periodicidade != null && diasAtraso >= periodicidade;

        return CustodiadoListDTO.builder()
                .id(custodiado.getPublicId() != null ? custodiado.getPublicId().toString() : null)
                .numericId(custodiado.getId())
                .nome(custodiado.getNome())
                .cpf(custodiado.getCpf())
                .rg(custodiado.getRg())
                .documentoExibicao(documentoExibicao)
                .processo(custodiado.getProcesso())
                .vara(custodiado.getVara())
                .comarca(custodiado.getComarca())
                .status(custodiado.getStatus())
                .situacao(custodiado.getSituacao())
                .ultimoComparecimento(custodiado.getUltimoComparecimento())
                .proximoComparecimento(custodiado.getProximoComparecimento())
                .diasAtraso(diasAtraso)
                .periodicidade(periodicidade)
                .inadimplente(custodiado.isInadimplente())
                .comparecimentoHoje(custodiado.isComparecimentoHoje())
                .urgente(urgente)
                .build();
    }

    public boolean isEmConformidade() { return status == StatusComparecimento.EM_CONFORMIDADE; }
    public boolean isInadimplente() { return status == StatusComparecimento.INADIMPLENTE; }
    public boolean isAtivo() { return situacao == SituacaoCustodiado.ATIVO; }
    public boolean isArquivado() { return situacao == SituacaoCustodiado.ARQUIVADO; }
    public boolean isUrgente() { return urgente; }

    public String getStatusDescricao() { return status != null ? status.getLabel() : "Não definido"; }
    public String getSituacaoDescricao() { return situacao != null ? situacao.getLabel() : "Não definida"; }
    public String getStatusCssClass() { return status != null ? status.getCssClass() : "secondary"; }
    public String getSituacaoCssClass() { return situacao != null ? situacao.getCssClass() : "secondary"; }
    public String getUrgenciaCssClass() { return urgente ? "danger" : ""; }
    public String getUrgenciaTexto() { return urgente ? "⚠ Urgente" : ""; }

    public String getDiasAtrasoDescricao() {
        if (diasAtraso == null || diasAtraso == 0) return "Em dia";
        if (diasAtraso == 1) return "1 dia atraso";
        return diasAtraso + " dias atraso";
    }

    public String getProcessoVara() {
        if (processo != null && vara != null) return processo + " - " + vara;
        return processo != null ? processo : "Processo não informado";
    }
}

package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.enums.SituacaoCustodiado;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO otimizado para listagens de custodiados
 * Inclui todos os campos necessários para a tabela de listagem
 * NÃO inclui endereço para evitar N+1 queries
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustodiadoListDTO {

    // Identificação
    private Long id;
    private String nome;
    private String cpf;
    private String rg;
    private String documentoExibicao; // CPF ou RG para exibição

    // Processo
    private String processo;
    private String vara;
    private String comarca;

    // Status e Situação
    private StatusComparecimento status;
    private SituacaoCustodiado situacao;

    // Datas de comparecimento
    private LocalDate ultimoComparecimento;
    private LocalDate proximoComparecimento;

    // Controle de atraso
    private Long diasAtraso;
    private Integer periodicidade;
    private boolean inadimplente;
    private boolean comparecimentoHoje;
    private boolean urgente;

    /**
     * Converte entidade para DTO de listagem
     */
    public static CustodiadoListDTO fromEntity(Custodiado custodiado) {
        // Determinar qual documento exibir (prioriza CPF)
        String documentoExibicao;
        if (custodiado.getCpf() != null && !custodiado.getCpf().trim().isEmpty()) {
            documentoExibicao = custodiado.getCpf();
        } else if (custodiado.getRg() != null && !custodiado.getRg().trim().isEmpty()) {
            documentoExibicao = custodiado.getRg();
        } else {
            documentoExibicao = "Sem documento";
        }

        // Calcular se é urgente (dias de atraso >= periodicidade)
        long diasAtraso = custodiado.getDiasAtraso();
        Integer periodicidade = custodiado.getPeriodicidade();
        boolean urgente = periodicidade != null && diasAtraso >= periodicidade;

        return CustodiadoListDTO.builder()
                .id(custodiado.getId())
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

    /**
     * Retorna se o custodiado está em conformidade com os comparecimentos
     */
    public boolean isEmConformidade() {
        return status == StatusComparecimento.EM_CONFORMIDADE;
    }

    /**
     * Retorna se o custodiado está inadimplente
     */
    public boolean isInadimplente() {
        return status == StatusComparecimento.INADIMPLENTE;
    }

    /**
     * Retorna se o custodiado está ativo no sistema
     */
    public boolean isAtivo() {
        return situacao == SituacaoCustodiado.ATIVO;
    }

    /**
     * Retorna se o custodiado está arquivado
     */
    public boolean isArquivado() {
        return situacao == SituacaoCustodiado.ARQUIVADO;
    }

    /**
     * Retorna se o caso é urgente (atraso >= periodicidade)
     * Exemplo: Periodicidade 15 dias + 15 dias de atraso = URGENTE
     */
    public boolean isUrgente() {
        return urgente;
    }

    /**
     * Retorna descrição do status de comparecimento
     */
    public String getStatusDescricao() {
        return status != null ? status.getLabel() : "Não definido";
    }

    /**
     * Retorna descrição da situação do custodiado
     */
    public String getSituacaoDescricao() {
        return situacao != null ? situacao.getLabel() : "Não definida";
    }

    /**
     * Retorna CSS class baseado no status de comparecimento
     */
    public String getStatusCssClass() {
        return status != null ? status.getCssClass() : "secondary";
    }

    /**
     * Retorna CSS class baseado na situação
     */
    public String getSituacaoCssClass() {
        return situacao != null ? situacao.getCssClass() : "secondary";
    }

    /**
     * Retorna CSS class baseado na urgência
     */
    public String getUrgenciaCssClass() {
        return urgente ? "danger" : "";
    }

    /**
     * Retorna texto de urgência formatado
     */
    public String getUrgenciaTexto() {
        if (urgente) {
            return "⚠ Urgente";
        }
        return "";
    }

    /**
     * Retorna descrição completa dos dias de atraso
     */
    public String getDiasAtrasoDescricao() {
        if (diasAtraso == null || diasAtraso == 0) {
            return "Em dia";
        }
        if (diasAtraso == 1) {
            return "1 dia atraso";
        }
        return diasAtraso + " dias atraso";
    }

    /**
     * Retorna informação sobre o processo formatada
     */
    public String getProcessoVara() {
        if (processo != null && vara != null) {
            return processo + " - " + vara;
        }
        return processo != null ? processo : "Processo não informado";
    }
}
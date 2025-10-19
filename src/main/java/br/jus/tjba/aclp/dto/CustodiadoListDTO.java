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
 * DTO simplificado para listagens de custodiados
 * Inclui apenas os campos essenciais para melhor performance
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustodiadoListDTO {

    private Long id;
    private String nome;
    private String cpf;
    private String processo;
    private String comarca;

    // Status de comparecimento (EM_CONFORMIDADE/INADIMPLENTE)
    private StatusComparecimento status;

    // Situação no sistema (ATIVO/ARQUIVADO)
    private SituacaoCustodiado situacao;

    private LocalDate proximoComparecimento;
    private Long diasAtraso;
    private String enderecoResumido;
    private boolean inadimplente;
    private boolean comparecimentoHoje;

    /**
     * Converte entidade para DTO de listagem
     */
    public static CustodiadoListDTO fromEntity(Custodiado custodiado) {
        return CustodiadoListDTO.builder()
                .id(custodiado.getId())
                .nome(custodiado.getNome())
                .cpf(custodiado.getCpf())
                .processo(custodiado.getProcesso())
                .comarca(custodiado.getComarca())
                .status(custodiado.getStatus())
                .situacao(custodiado.getSituacao())
                .proximoComparecimento(custodiado.getProximoComparecimento())
                .diasAtraso(custodiado.getDiasAtraso())
                .enderecoResumido(custodiado.getEnderecoResumido())
                .inadimplente(custodiado.isInadimplente())
                .comparecimentoHoje(custodiado.isComparecimentoHoje())
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
}
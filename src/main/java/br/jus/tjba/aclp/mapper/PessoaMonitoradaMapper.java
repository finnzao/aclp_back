package br.jus.tjba.aclp.mapper;

import br.jus.tjba.aclp.controller.PessoaMonitoradaController.EnderecoDetalhadoDTO;
import br.jus.tjba.aclp.controller.PessoaMonitoradaController.PessoaMonitoradaResponseDTO;
import br.jus.tjba.aclp.dto.HistoricoEnderecoDTO;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.service.HistoricoEnderecoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** B2 do review: era um fromEntity estático recebendo service por parâmetro. */
@Component
@RequiredArgsConstructor
public class PessoaMonitoradaMapper {

    private final HistoricoEnderecoService enderecoService;

    public PessoaMonitoradaResponseDTO paraResponse(PessoaMonitorada custodiado) {
        return PessoaMonitoradaResponseDTO.builder()
                .id(custodiado.getPublicId() != null ? custodiado.getPublicId().toString() : null)
                .nome(custodiado.getNome()).cpf(custodiado.getCpf()).rg(custodiado.getRg())
                .contato(custodiado.getContato()).contatoPendente(custodiado.isContatoPendente())
                .processo(custodiado.getProcesso()).vara(custodiado.getVara()).comarca(custodiado.getComarca())
                .dataDecisao(custodiado.getDataDecisao()).periodicidade(custodiado.getPeriodicidade())
                .periodicidadeDescricao(custodiado.getPeriodicidadeDescricao())
                .dataComparecimentoInicial(custodiado.getDataComparecimentoInicial())
                .status(custodiado.getStatusEfetivo())
                .situacao(custodiado.getSituacao())
                .motivoArquivamento(custodiado.getMotivoArquivamento())
                .ultimoComparecimento(custodiado.getUltimoComparecimento())
                .proximoComparecimento(custodiado.getProximoComparecimento())
                .diasAtraso(custodiado.getDiasAtraso()).observacoes(custodiado.getObservacoes())
                .endereco(mapearEndereco(custodiado.getId()))
                .criadoEm(custodiado.getCriadoEm())
                .atualizadoEm(custodiado.getAtualizadoEm()).identificacao(custodiado.getIdentificacao())
                .inadimplente(custodiado.isInadimplente()).comparecimentoHoje(custodiado.isComparecimentoHoje())
                .build();
    }

    private EnderecoDetalhadoDTO mapearEndereco(Long custodiadoId) {
        Optional<HistoricoEnderecoDTO> enderecoOpt = enderecoService.buscarEnderecoAtivo(custodiadoId);
        if (enderecoOpt.isEmpty()) return null;
        HistoricoEnderecoDTO e = enderecoOpt.get();
        return EnderecoDetalhadoDTO.builder()
                .id(e.getId()).cep(e.getCep())
                .logradouro(e.getLogradouro()).numero(e.getNumero())
                .complemento(e.getComplemento()).bairro(e.getBairro())
                .cidade(e.getCidade()).estado(e.getEstado())
                .nomeEstado(e.getNomeEstado()).regiaoEstado(e.getRegiaoEstado())
                .dataInicio(e.getDataInicio()).dataFim(e.getDataFim())
                .ativo(e.getEnderecoAtivo()).motivoAlteracao(e.getMotivoAlteracao())
                .validadoPor(e.getValidadoPor())
                .enderecoCompleto(e.getEnderecoCompleto())
                .enderecoResumido(e.getEnderecoResumido())
                .diasResidencia(e.getDiasResidencia())
                .periodoResidencia(e.getPeriodoResidencia())
                .criadoEm(e.getCriadoEm()).atualizadoEm(e.getAtualizadoEm())
                .build();
    }
}

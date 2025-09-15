package br.jus.tjba.aclp.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTOs de resposta para operações de comparecimento
 * Movidos do ComparecimentoService para melhor organização
 */
public class ComparecimentoResponseDTO {

    /**
     * DTO para estatísticas de comparecimentos por período
     */
    @Data
    @Builder
    public static class EstatisticasComparecimento {
        private String periodo;
        private long totalComparecimentos;
        private long comparecimentosPresenciais;
        private long comparecimentosOnline;
        private long cadastrosIniciais;
        private long mudancasEndereco;
        private double percentualPresencial;
        private double percentualOnline;
    }

    /**
     * DTO para estatísticas gerais do sistema
     */
    @Data
    @Builder
    public static class EstatisticasGerais {
        private long totalComparecimentos;
        private long comparecimentosPresenciais;
        private long comparecimentosOnline;
        private long cadastrosIniciais;
        private long totalMudancasEndereco;
        private long comparecimentosHoje;
        private long comparecimentosEsteMes;
        private long custodiadosComComparecimento;
        private double percentualPresencial;
        private double percentualOnline;
        private double mediaComparecimentosPorCustodiado;
    }

    /**
     * DTO para resumo completo do sistema
     */
    @Data
    @Builder
    public static class ResumoSistema {
        // Dados básicos
        private long totalCustodiados;
        private long custodiadosEmConformidade;
        private long custodiadosInadimplentes;
        private long comparecimentosHoje;
        private long totalComparecimentos;
        private long comparecimentosEsteMes;
        private long totalMudancasEndereco;
        private long enderecosAtivos;
        private long custodiadosSemHistorico;
        private long custodiadosSemEnderecoAtivo;
        private double percentualConformidade;
        private double percentualInadimplencia;
        private LocalDate dataConsulta;

        // Novos dados
        private RelatorioUltimosMeses relatorioUltimosMeses;
        private List<TendenciaMensal> tendenciaConformidade;
        private ProximosComparecimentos proximosComparecimentos;
        private AnaliseComparecimentos analiseComparecimentos;
        private AnaliseAtrasos analiseAtrasos;
    }

    /**
     * DTO para relatório dos últimos meses
     */
    @Data
    @Builder
    public static class RelatorioUltimosMeses {
        private int mesesAnalisados;
        private LocalDate periodoInicio;
        private LocalDate periodoFim;
        private long totalComparecimentos;
        private long comparecimentosPresenciais;
        private long comparecimentosOnline;
        private long mudancasEndereco;
        private double mediaComparecimentosMensal;
        private double percentualPresencial;
        private double percentualOnline;
    }

    /**
     * DTO para tendência mensal
     */
    @Data
    @Builder
    public static class TendenciaMensal {
        private String mes; // formato: yyyy-MM
        private String mesNome; // formato: "Janeiro/2024"
        private long totalCustodiados;
        private long emConformidade;
        private long inadimplentes;
        private double taxaConformidade;
        private double taxaInadimplencia;
        private long totalComparecimentos;
    }

    /**
     * DTO para próximos comparecimentos
     */
    @Data
    @Builder
    public static class ProximosComparecimentos {
        private int diasAnalisados;
        private long totalPrevistoProximosDias;
        private long totalAtrasados;
        private long comparecimentosHoje;
        private long comparecimentosAmanha;
        private List<ComparecimentoDiario> detalhesPorDia;
        private List<DetalheCustodiado> custodiadosAtrasados;
    }

    /**
     * DTO para comparecimentos diários
     */
    @Data
    @Builder
    public static class ComparecimentoDiario {
        private LocalDate data;
        private String diaSemana;
        private int totalPrevisto;
        private List<DetalheCustodiado> custodiados;
    }

    /**
     * DTO para detalhe de custodiado
     */
    @Data
    @Builder
    public static class DetalheCustodiado {
        private Long id;
        private String nome;
        private String processo;
        private String periodicidade;
        private long diasAtraso;
    }

    /**
     * DTO para análise de comparecimentos
     */
    @Data
    @Builder
    public static class AnaliseComparecimentos {
        private long comparecimentosUltimos30Dias;
        private long comparecimentosOnlineUltimos30Dias;
        private long comparecimentosPresenciaisUltimos30Dias;
        private double taxaOnlineUltimos30Dias;
        private Map<String, Long> comparecimentosPorDiaSemana;
        private Map<Integer, Long> comparecimentosPorHora;
    }

    /**
     * DTO para análise de atrasos
     */
    @Data
    @Builder
    public static class AnaliseAtrasos {
        private long totalCustodiadosAtrasados;
        private long totalAtrasados30Dias; // Atrasados entre 31-60 dias
        private long totalAtrasados60Dias; // Atrasados entre 61-90 dias
        private long totalAtrasados90Dias; // Atrasados há mais de 90 dias
        private long totalAtrasadosMais90Dias; // Atrasados há mais de 90 dias
        private double mediaDiasAtraso;
        private Map<String, Long> distribuicaoAtrasos; // Distribuição por faixas
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasados30Dias;
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasados60Dias;
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasados90Dias;
        private List<DetalheCustodiadoAtrasado> custodiadosAtrasadosMais90Dias;
        private DetalheCustodiadoAtrasado custodiadoMaiorAtraso;
        private LocalDate dataAnalise;
    }

    /**
     * DTO para detalhe de custodiado atrasado
     */
    @Data
    @Builder
    public static class DetalheCustodiadoAtrasado {
        private Long id;
        private String nome;
        private String processo;
        private String periodicidade;
        private long diasAtraso;
        private LocalDate dataUltimoComparecimento;
        private LocalDate dataProximoComparecimento;
        private String vara;
        private String comarca;
        private String contato;
        private String enderecoAtual;
    }
}
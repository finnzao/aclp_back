package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.ExecucaoPenal;
import br.jus.tjba.aclp.model.enums.ModalidadeExecucao;
import br.jus.tjba.aclp.model.enums.OrigemCalculo;
import br.jus.tjba.aclp.model.enums.SituacaoExecucao;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class ExecucaoPenalDTO {

    // Campos condicionais por card validados no service (Bean Validation nao
    // expressa "obrigatorio SE origem = X").
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegistroPenaRequest {

        // opcional quando aninhado no cadastro inicial; o service valida no endpoint direto
        private Long processoId;

        @NotNull(message = "Modalidade é obrigatória")
        private ModalidadeExecucao modalidade;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataDistribuicao;

        // obrigatoria exceto em tratamento ambulatorial; validado no service
        private OrigemCalculo origemCalculo;

        @NotBlank(message = "Número da execução (PEC) é obrigatório")
        @Pattern(regexp = "\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}",
                message = "Número da execução deve seguir o padrão CNJ: 0000000-00.0000.0.00.0000")
        private String numeroExecucao;

        @Size(max = 50)
        private String guiaRecolhimento;

        @Size(max = 30)
        private String numeroSeeu;

        @NotNull(message = "Data de início do cumprimento é obrigatória")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataInicio;

        // opcional: pena bruta + detracao (ausente = guia ja liquida)
        @Min(0)
        private Integer diasDetraidos;

        // Card 1 — SENTENCA
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataTermino;

        // Card 2 — DOSIMETRIA_CALCULADA
        @Min(0) private Integer anos;
        @Min(0) private Integer meses;
        @Min(0) private Integer dias;

        @Size(max = 500)
        private String observacoes;
    }

    // Opcao A: diasTrabalhados/horasEstudo (o sistema converte pela LEP 126).
    // Opcao B: dias direto, ja calculado externamente.
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RemicaoRequest {

        @Min(1)
        private Integer dias;

        @Min(0)
        private Integer diasTrabalhados;

        @Min(0)
        private Integer horasEstudo;

        @Size(max = 200)
        private String observacao;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RetificarTerminoRequest {

        @NotNull(message = "Nova data de término é obrigatória")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate novoTermino;

        @NotBlank(message = "Motivo é obrigatório")
        @Size(max = 400)
        private String motivo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MotivoRequest {
        @NotBlank(message = "Motivo é obrigatório")
        @Size(max = 400)
        private String motivo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExtincaoRequest {
        @NotNull(message = "Data da sentença é obrigatória")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataSentenca;

        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 1000)
        private String descricao;
    }

    // so dados cadastrais; pena e termino tem acoes proprias
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EdicaoExecucaoRequest {
        @NotBlank
        @Pattern(regexp = "\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}",
                message = "Número da execução deve seguir o padrão CNJ: 0000000-00.0000.0.00.0000")
        private String numeroExecucao;

        @NotNull
        private ModalidadeExecucao modalidade;

        @Min(1) @Max(365)
        private Integer periodicidade;

        @Size(max = 50)
        private String guiaRecolhimento;

        @Size(max = 30)
        private String numeroSeeu;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataDistribuicao;

        @Size(max = 500)
        private String observacoes;
    }

    @Data
    @Builder
    public static class ExecucaoPenalResponse {
        private Long id;
        private Long processoId;
        private String numeroProcesso;
        private String custodiadoNome;
        private Integer periodicidade;
        private ModalidadeExecucao modalidade;
        private String modalidadeDescricao;
        private LocalDate dataDistribuicao;
        private String numeroExecucao;
        private String guiaRecolhimento;
        private String numeroSeeu;
        private LocalDate dataInicioCumprimento;
        private Integer penaTotalDias;
        private Integer diasDetraidos;
        private Integer diasRemidos;
        private LocalDate dataTerminoOriginal;
        private LocalDate dataTerminoPrevista;
        private OrigemCalculo origemCalculo;
        private SituacaoExecucao situacaoExecucao;
        private String situacaoDescricao;
        private long diasCumpridos;
        private long diasRestantes;
        private String observacoes;
        private LocalDate ultimaMovimentacao;
        private long providenciasPendentes;
        private long providenciasVencidas;

        // chamar dentro da transacao (processo LAZY)
        public static ExecucaoPenalResponse fromEntity(ExecucaoPenal e) {
            return ExecucaoPenalResponse.builder()
                    .id(e.getId())
                    .processoId(e.getProcesso().getId())
                    .numeroProcesso(e.getProcesso().getNumeroProcesso())
                    .custodiadoNome(e.getProcesso().getCustodiado() != null
                            ? e.getProcesso().getCustodiado().getNome() : null)
                    .periodicidade(e.getProcesso().getPeriodicidade())
                    .modalidade(e.getModalidade())
                    .modalidadeDescricao(e.getModalidade().getDescricao())
                    .dataDistribuicao(e.getDataDistribuicao())
                    .numeroExecucao(e.getNumeroExecucao())
                    .guiaRecolhimento(e.getGuiaRecolhimento())
                    .numeroSeeu(e.getNumeroSeeu())
                    .dataInicioCumprimento(e.getDataInicioCumprimento())
                    .penaTotalDias(e.getPenaTotalDias())
                    .diasDetraidos(e.getDiasDetraidos())
                    .diasRemidos(e.getDiasRemidos())
                    .dataTerminoOriginal(e.getDataTerminoOriginal())
                    .dataTerminoPrevista(e.getDataTerminoPrevista())
                    .origemCalculo(e.getOrigemCalculo())
                    .situacaoExecucao(e.getSituacaoExecucao())
                    .situacaoDescricao(e.getSituacaoExecucao().getDescricao())
                    .diasCumpridos(e.getDiasCumpridos())
                    .diasRestantes(e.getDiasRestantes())
                    .observacoes(e.getObservacoes())
                    .build();
        }
    }
}

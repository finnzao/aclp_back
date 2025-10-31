package br.jus.tjba.aclp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricoComparecimentoResponseDTO {
    private Long id;
    private Long custodiadoId;
    private String custodiadoNome;
    private LocalDate dataComparecimento;
    private LocalTime horaComparecimento;
    private String tipoValidacao;
    private String validadoPor;
    private String observacoes;
    private Boolean mudancaEndereco;
    private String motivoMudancaEndereco;
}
package br.jus.tjba.aclp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * CORREÇÃO DE PERFORMANCE: Campos numeroProcesso e custodiadoCpf adicionados.
 *
 * Anteriormente estes campos não existiam na resposta, forçando o frontend
 * a fazer requisições individuais para buscar o processo de cada
 * comparecimento (problema N+1 no frontend).
 *
 * Agora o número do processo e CPF do custodiado são incluídos diretamente,
 * eliminando a necessidade de requisições extras.
 *
 * RETROCOMPATIBILIDADE: Campos novos são adicionais — nenhum campo existente
 * foi removido ou renomeado. Frontend antigo ignora campos desconhecidos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricoComparecimentoResponseDTO {

    // ===== CAMPOS EXISTENTES (inalterados) =====
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

    // ===== NOVOS CAMPOS — CORREÇÃO DE PERFORMANCE =====

    /**
     * Número do processo vinculado ao comparecimento.
     * Evita que o frontend precise buscar processos separadamente (N+1).
     * Pode ser null se o comparecimento não está vinculado a um processo.
     */
    private String numeroProcesso;

    /**
     * CPF do custodiado para facilitar buscas e exibição no frontend.
     * Evita requisição extra para buscar dados do custodiado.
     */
    private String custodiadoCpf;
}

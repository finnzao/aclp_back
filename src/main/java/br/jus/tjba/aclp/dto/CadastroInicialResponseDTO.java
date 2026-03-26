package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de resposta do cadastro inicial.
 * Retorna dados do custodiado, processo e endereço criados.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CadastroInicialResponseDTO {

    // ---- Custodiado ----
    private String custodiadoId;  // UUID público
    private String nome;
    private String cpf;
    private String rg;
    private String contato;
    private boolean contatoPendente;
    private String identificacao;

    // ---- Processo ----
    private Long processoId;
    private String numeroProcesso;
    private String vara;
    private String comarca;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataDecisao;

    private Integer periodicidade;
    private String periodicidadeDescricao;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataComparecimentoInicial;

    private StatusComparecimento statusProcesso;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate proximoComparecimento;

    // ---- Endereço ----
    private String enderecoCompleto;
    private String enderecoResumido;
    private String cidade;
    private String estado;

    // ---- Metadados ----
    private String observacoes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime criadoEm;
}

package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PessoaDTO {

    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    private String cpf;
    private String rg;

    @NotBlank(message = "Contato é obrigatório")
    private String contato;

    @NotBlank(message = "Processo é obrigatório")
    private String processo;

    @NotBlank(message = "Vara é obrigatória")
    private String vara;

    @NotBlank(message = "Comarca é obrigatória")
    private String comarca;

    @NotNull(message = "Data da decisão é obrigatória")
    private LocalDate dataDecisao;

    @NotNull(message = "Periodicidade é obrigatória")
    private Integer periodicidade;

    @NotNull(message = "Data do comparecimento inicial é obrigatória")
    private LocalDate dataComparecimentoInicial;

    private StatusComparecimento status;
    private LocalDate primeiroComparecimento;
    private LocalDate ultimoComparecimento;
    private LocalDate proximoComparecimento;
    private String observacoes;

    // Endereco
    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String estado;
}
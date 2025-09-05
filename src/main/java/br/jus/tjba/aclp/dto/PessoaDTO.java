package br.jus.tjba.aclp.dto;

import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(min = 2, max = 150, message = "Nome deve ter entre 2 e 150 caracteres")
    private String nome;

    @Pattern(regexp = "\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}",
            message = "CPF deve ter o formato 000.000.000-00")
    private String cpf;

    @Size(max = 20, message = "RG deve ter no máximo 20 caracteres")
    private String rg;

    @NotBlank(message = "Contato é obrigatório")
    @Pattern(regexp = "\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}",
            message = "Contato deve ter formato válido de telefone")
    private String contato;

    @NotBlank(message = "Processo é obrigatório")
    @Pattern(regexp = "\\d{7}-\\d{2}\\.\\d{4}\\.\\d{1}\\.\\d{2}\\.\\d{4}",
            message = "Processo deve ter o formato 0000000-00.0000.0.00.0000")
    private String processo;

    @NotBlank(message = "Vara é obrigatória")
    @Size(max = 100, message = "Vara deve ter no máximo 100 caracteres")
    private String vara;

    @NotBlank(message = "Comarca é obrigatória")
    @Size(max = 100, message = "Comarca deve ter no máximo 100 caracteres")
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

    @Size(max = 500, message = "Observações deve ter no máximo 500 caracteres")
    private String observacoes;

    // === CAMPOS DE ENDEREÇO - AGORA OBRIGATÓRIOS ===

    @NotBlank(message = "CEP é obrigatório")
    @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP deve ter o formato 00000-000")
    private String cep;

    @NotBlank(message = "Logradouro é obrigatório")
    @Size(min = 5, max = 200, message = "Logradouro deve ter entre 5 e 200 caracteres")
    private String logradouro;

    @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
    private String numero;

    @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres")
    private String complemento;

    @NotBlank(message = "Bairro é obrigatório")
    @Size(min = 2, max = 100, message = "Bairro deve ter entre 2 e 100 caracteres")
    private String bairro;

    @NotBlank(message = "Cidade é obrigatória")
    @Size(min = 2, max = 100, message = "Cidade deve ter entre 2 e 100 caracteres")
    private String cidade;

    @NotBlank(message = "Estado é obrigatório")
    @Size(min = 2, max = 2, message = "Estado deve ter exatamente 2 caracteres")
    @Pattern(regexp = "[A-Z]{2}", message = "Estado deve ser uma sigla válida com 2 letras maiúsculas")
    private String estado;

    /**
     * Valida se pelo menos um documento foi fornecido
     */
    public boolean hasDocumento() {
        return (cpf != null && !cpf.trim().isEmpty()) ||
                (rg != null && !rg.trim().isEmpty());
    }

    /**
     * Valida se todos os campos obrigatórios de endereço estão preenchidos
     */
    public boolean hasEnderecoCompleto() {
        return cep != null && !cep.trim().isEmpty() &&
                logradouro != null && !logradouro.trim().isEmpty() &&
                bairro != null && !bairro.trim().isEmpty() &&
                cidade != null && !cidade.trim().isEmpty() &&
                estado != null && !estado.trim().isEmpty();
    }

    /**
     * Limpa e formata os dados antes da validação
     */
    public void limparEFormatarDados() {
        if (nome != null) {
            nome = nome.trim();
        }
        if (cpf != null) {
            cpf = cpf.trim();
        }
        if (rg != null) {
            rg = rg.trim();
        }
        if (contato != null) {
            contato = contato.trim();
        }
        if (processo != null) {
            processo = processo.trim();
        }
        if (vara != null) {
            vara = vara.trim();
        }
        if (comarca != null) {
            comarca = comarca.trim();
        }
        if (observacoes != null) {
            observacoes = observacoes.trim();
        }

        // Limpar dados de endereço
        if (cep != null) {
            cep = cep.trim();
        }
        if (logradouro != null) {
            logradouro = logradouro.trim();
        }
        if (numero != null) {
            numero = numero.trim();
        }
        if (complemento != null) {
            complemento = complemento.trim();
        }
        if (bairro != null) {
            bairro = bairro.trim();
        }
        if (cidade != null) {
            cidade = cidade.trim();
        }
        if (estado != null) {
            estado = estado.trim().toUpperCase();
        }
    }
}
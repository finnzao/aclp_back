package br.jus.tjba.aclp.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO unificado para cadastro inicial em uma única requisição.
 * Cria: Custodiado + Endereço + Processo + Primeiro Comparecimento.
 *
 * Corresponde ao formulário do frontend com 6 seções:
 *   1. Dados Pessoais (nome, contato)
 *   2. Documentos (cpf e/ou rg)
 *   3. Dados Processuais (processo, vara, comarca, datas)
 *   4. Periodicidade
 *   5. Endereço
 *   6. Observações
 *
 * Regras:
 * - Pelo menos CPF ou RG deve ser informado
 * - Contato é opcional; se vazio, salva como "Pendente"
 * - Processo é obrigatório e será criado na tabela processos
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CadastroInicialDTO {

    // ======================== 1. DADOS PESSOAIS ========================

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 150, message = "Nome deve ter entre 2 e 150 caracteres")
    private String nome;

    /**
     * Contato/Telefone — OPCIONAL.
     * Se não informado, será salvo como "Pendente" no banco de dados.
     */
    @Pattern(regexp = "[\\d\\s().-]*",
            message = "Contato deve conter apenas números e caracteres de formatação")
    private String contato;

    // ======================== 2. DOCUMENTOS ========================

    /**
     * CPF — opcional se RG for informado.
     */
    @Pattern(regexp = "\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}|\\d{11}|",
            message = "CPF deve ter o formato 000.000.000-00 ou apenas números")
    private String cpf;

    /**
     * RG — opcional se CPF for informado.
     */
    @Size(max = 20, message = "RG deve ter no máximo 20 caracteres")
    private String rg;

    // ======================== 3. DADOS PROCESSUAIS ========================

    @NotBlank(message = "Número do processo é obrigatório")
    @Pattern(regexp = "[\\d.-]+",
            message = "Processo deve conter apenas números, pontos e hífens")
    private String processo;

    @NotBlank(message = "Vara é obrigatória")
    @Size(max = 100, message = "Vara deve ter no máximo 100 caracteres")
    private String vara;

    @NotBlank(message = "Comarca é obrigatória")
    @Size(max = 100, message = "Comarca deve ter no máximo 100 caracteres")
    private String comarca;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Data da decisão é obrigatória")
    private LocalDate dataDecisao;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataComparecimentoInicial;

    // ======================== 4. PERIODICIDADE ========================

    @NotNull(message = "Periodicidade é obrigatória")
    @Min(value = 1, message = "Periodicidade mínima é 1 dia")
    @Max(value = 365, message = "Periodicidade máxima é 365 dias")
    private Integer periodicidade;

    // ======================== 5. ENDEREÇO ========================

    @NotBlank(message = "CEP é obrigatório")
    @Pattern(regexp = "\\d{5}-?\\d{3}|\\d{8}", message = "CEP deve ter o formato 00000-000 ou apenas números")
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

    // ======================== 6. OBSERVAÇÕES ========================

    @Size(max = 500, message = "Observações deve ter no máximo 500 caracteres")
    private String observacoes;

    // ======================== VALIDAÇÕES ========================

    /**
     * Pelo menos CPF ou RG deve ser informado.
     */
    @AssertTrue(message = "Pelo menos CPF ou RG deve ser informado")
    public boolean isDocumentoValido() {
        boolean temCpf = cpf != null && !cpf.trim().isEmpty();
        boolean temRg = rg != null && !rg.trim().isEmpty();
        return temCpf || temRg;
    }

    /**
     * Retorna o contato efetivo: valor informado ou "Pendente".
     */
    public String getContatoEfetivo() {
        if (contato == null || contato.trim().isEmpty()) {
            return "Pendente";
        }
        return contato.trim();
    }

    /**
     * Limpa e formata os dados antes do processamento.
     */
    public void limparEFormatarDados() {
        if (nome != null) nome = nome.trim().toUpperCase();
        if (cpf != null) cpf = cpf.trim();
        if (rg != null) rg = rg.trim().toUpperCase();
        if (contato != null) contato = contato.trim();
        if (processo != null) processo = processo.trim();
        if (vara != null) vara = vara.trim().toUpperCase();
        if (comarca != null) comarca = comarca.trim().toUpperCase();
        if (observacoes != null) observacoes = observacoes.trim();
        if (cep != null) cep = cep.trim().replaceAll("[^\\d]", "");
        if (logradouro != null) logradouro = logradouro.trim();
        if (numero != null) numero = numero.trim();
        if (complemento != null) complemento = complemento.trim();
        if (bairro != null) bairro = bairro.trim();
        if (cidade != null) cidade = cidade.trim();
        if (estado != null) estado = estado.trim().toUpperCase();
    }
}

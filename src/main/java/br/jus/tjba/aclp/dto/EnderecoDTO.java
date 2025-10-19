package br.jus.tjba.aclp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnderecoDTO {
    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String estado;
    private String enderecoCompleto;

    // Construtor para uso em JPQL
    public EnderecoDTO(String cep, String logradouro, String numero,
                       String complemento, String bairro, String cidade, String estado) {
        this.cep = cep;
        this.logradouro = logradouro;
        this.numero = numero;
        this.complemento = complemento;
        this.bairro = bairro;
        this.cidade = cidade;
        this.estado = estado;
        this.enderecoCompleto = construirEnderecoCompleto();
    }

    private String construirEnderecoCompleto() {
        StringBuilder sb = new StringBuilder();
        if (logradouro != null) sb.append(logradouro);
        if (numero != null && !numero.isEmpty()) sb.append(", ").append(numero);
        if (complemento != null && !complemento.isEmpty()) sb.append(" - ").append(complemento);
        if (bairro != null) sb.append(" - ").append(bairro);
        if (cidade != null && estado != null) sb.append(" - ").append(cidade).append("/").append(estado);
        return sb.toString();
    }
}
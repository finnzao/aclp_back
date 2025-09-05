package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.EstadoBrasil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "enderecos",
        indexes = {
                @Index(name = "idx_endereco_cep", columnList = "cep"),
                @Index(name = "idx_endereco_cidade", columnList = "cidade"),
                @Index(name = "idx_endereco_estado", columnList = "estado")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "CEP é obrigatório")
    @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP deve ter o formato 00000-000")
    @Column(name = "cep", nullable = false, length = 9)
    private String cep;

    @NotBlank(message = "Logradouro é obrigatório")
    @Size(min = 5, max = 200, message = "Logradouro deve ter entre 5 e 200 caracteres")
    @Column(name = "logradouro", nullable = false, length = 200)
    private String logradouro;

    @Size(max = 20, message = "Número deve ter no máximo 20 caracteres")
    @Column(name = "numero", length = 20)
    private String numero;

    @Size(max = 100, message = "Complemento deve ter no máximo 100 caracteres")
    @Column(name = "complemento", length = 100)
    private String complemento;

    @NotBlank(message = "Bairro é obrigatório")
    @Size(min = 2, max = 100, message = "Bairro deve ter entre 2 e 100 caracteres")
    @Column(name = "bairro", nullable = false, length = 100)
    private String bairro;

    @NotBlank(message = "Cidade é obrigatória")
    @Size(min = 2, max = 100, message = "Cidade deve ter entre 2 e 100 caracteres")
    @Column(name = "cidade", nullable = false, length = 100)
    private String cidade;

    @NotBlank(message = "Estado é obrigatório")
    @Size(min = 2, max = 2, message = "Estado deve ter exatamente 2 caracteres")
    @Pattern(regexp = "[A-Z]{2}", message = "Estado deve ser uma sigla válida com 2 letras maiúsculas")
    @Column(name = "estado", nullable = false, length = 2)
    private String estado;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.version == null) {
            this.version = 0L;
        }
        // Validar estado antes de persistir
        validarEstado();
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
        // Validar estado antes de atualizar
        validarEstado();
    }

    /**
     * Formata o CEP automaticamente
     */
    public void setCep(String cep) {
        if (cep != null) {
            String digits = cep.replaceAll("[^\\d]", "");
            if (digits.length() == 8) {
                this.cep = digits.substring(0, 5) + "-" + digits.substring(5);
            } else {
                this.cep = cep;
            }
        } else {
            this.cep = null;
        }
    }

    /**
     * Valida e formata o estado
     */
    public void setEstado(String estado) {
        if (estado != null) {
            String estadoLimpo = estado.trim().toUpperCase();

            // Validar se é um estado brasileiro válido
            if (!EstadoBrasil.isValidSigla(estadoLimpo)) {
                throw new IllegalArgumentException(
                        String.format("Estado '%s' é inválido. Estados válidos: %s",
                                estado, EstadoBrasil.getSiglasValidas())
                );
            }

            this.estado = estadoLimpo;
        } else {
            this.estado = null;
        }
    }

    /**
     * Valida o estado usando o enum
     */
    private void validarEstado() {
        if (this.estado != null && !EstadoBrasil.isValidSigla(this.estado)) {
            throw new IllegalArgumentException(
                    String.format("Estado '%s' é inválido. Estados válidos: %s",
                            this.estado, EstadoBrasil.getSiglasValidas())
            );
        }
    }

    /**
     * Retorna o enum do estado
     */
    public EstadoBrasil getEstadoBrasil() {
        return this.estado != null ? EstadoBrasil.fromString(this.estado) : null;
    }

    /**
     * Define o estado usando o enum
     */
    public void setEstadoBrasil(EstadoBrasil estadoBrasil) {
        this.estado = estadoBrasil != null ? estadoBrasil.getSigla() : null;
    }

    public String getEnderecoCompleto() {
        StringBuilder sb = new StringBuilder();
        sb.append(logradouro);
        if (numero != null && !numero.trim().isEmpty()) {
            sb.append(", ").append(numero);
        }
        if (complemento != null && !complemento.trim().isEmpty()) {
            sb.append(", ").append(complemento);
        }
        sb.append(", ").append(bairro);
        sb.append(", ").append(cidade).append(" - ").append(estado);
        if (cep != null) {
            sb.append(", CEP: ").append(cep);
        }
        return sb.toString();
    }

    public String getEnderecoResumido() {
        return String.format("%s, %s - %s",
                logradouro + (numero != null ? ", " + numero : ""),
                cidade,
                estado);
    }

    public String getCepSomenteNumeros() {
        return cep != null ? cep.replaceAll("[^\\d]", "") : null;
    }

    /**
     * Retorna o nome completo do estado
     */
    public String getNomeEstado() {
        EstadoBrasil estadoBrasil = getEstadoBrasil();
        return estadoBrasil != null ? estadoBrasil.getNome() : this.estado;
    }

    /**
     * Retorna a região do estado
     */
    public String getRegiaoEstado() {
        EstadoBrasil estadoBrasil = getEstadoBrasil();
        return estadoBrasil != null ? estadoBrasil.getRegiao() : "Não identificada";
    }

    /**
     * Valida se todos os campos obrigatórios estão preenchidos
     */
    public boolean isCompleto() {
        return cep != null && !cep.trim().isEmpty() &&
                logradouro != null && !logradouro.trim().isEmpty() &&
                bairro != null && !bairro.trim().isEmpty() &&
                cidade != null && !cidade.trim().isEmpty() &&
                estado != null && !estado.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Endereco endereco = (Endereco) o;
        return Objects.equals(cep, endereco.cep) &&
                Objects.equals(logradouro, endereco.logradouro) &&
                Objects.equals(numero, endereco.numero) &&
                Objects.equals(bairro, endereco.bairro) &&
                Objects.equals(cidade, endereco.cidade) &&
                Objects.equals(estado, endereco.estado);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cep, logradouro, numero, bairro, cidade, estado);
    }
}
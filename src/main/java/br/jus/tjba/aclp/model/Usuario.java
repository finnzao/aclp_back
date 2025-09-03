package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "usuarios",
        indexes = {
                @Index(name = "idx_usuario_email", columnList = "email"),
                @Index(name = "idx_usuario_tipo", columnList = "tipo"),
                @Index(name = "idx_usuario_ativo", columnList = "ativo")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank(message = "Senha é obrigatória")
    @Column(name = "senha", nullable = false)
    private String senha;

    @NotNull(message = "Tipo é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoUsuario tipo = TipoUsuario.USUARIO;

    @Size(max = 100, message = "Departamento deve ter no máximo 100 caracteres")
    @Column(name = "departamento", length = 100)
    private String departamento;

    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = Boolean.TRUE;

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    @Column(name = "configuracoes", columnDefinition = "TEXT")
    private String configuracoes;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public boolean isAdmin() {
        return tipo != null && tipo.isAdmin();
    }

    public boolean isUsuario() {
        return tipo != null && tipo.isUsuario();
    }

    public void registrarLogin() {
        this.ultimoLogin = LocalDateTime.now();
    }

    public String getNomeCompleto() {
        return nome + (departamento != null ? " (" + departamento + ")" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Usuario usuario = (Usuario) o;
        return Objects.equals(email, usuario.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email);
    }
}
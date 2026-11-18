package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.StatusConvite;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "convites", indexes = {
        @Index(name = "idx_convites_token", columnList = "token"),
        @Index(name = "idx_convites_status", columnList = "status"),
        @Index(name = "idx_convites_email", columnList = "email"),
        @Index(name = "idx_convites_expira_status", columnList = "expira_em, status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Convite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email do convidado - será o login dele no sistema
     * PODE SER NULL para convites genéricos/reutilizáveis
     * Quando NULL, o usuário deverá informar o email ao ativar o convite
     */
    @Column(nullable = true, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false, length = 20)
    private TipoUsuario tipoUsuario;

    /**
     * Token único para validação do convite
     * Gerado automaticamente via UUID
     */
    @Column(unique = true, nullable = false, length = 100)
    private String token;

    /**
     * Status atual do convite
     * PENDENTE: aguardando ativação
     * ATIVADO: convite foi usado e conta criada
     * EXPIRADO: passou do prazo de validade
     * CANCELADO: admin cancelou o convite
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConvite status;

    /**
     * Comarca copiada do admin que criou o convite
     * O usuário criado terá a mesma comarca
     */
    @Column(name = "comarca", length = 100)
    private String comarca;

    /**
     * Departamento copiado do admin que criou o convite
     * O usuário criado terá o mesmo departamento
     */
    @Column(name = "departamento", length = 100)
    private String departamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    /**
     * Usuário criado quando o convite foi ativado
     * null enquanto convite está pendente
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "ip_criacao", length = 45)
    private String ipCriacao;

    @Column(name = "ip_ativacao", length = 45)
    private String ipAtivacao;

    /**
     * Quantidade de vezes que o link pode ser usado
     * SEMPRE 1 = uso único (não permite reutilização)
     */
    @Column(name = "quantidade_usos")
    @Builder.Default
    private Integer quantidadeUsos = 1;

    /**
     * Quantidade de vezes que o link já foi usado
     * 0 = não usado, 1 = já usado
     */
    @Column(name = "usos_realizados")
    @Builder.Default
    private Integer usosRealizados = 0;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "ativado_em")
    private LocalDateTime ativadoEm;

    @PrePersist
    protected void onCreate() {
        if (this.criadoEm == null) {
            this.criadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();
        }
        if (this.expiraEm == null) {
            this.expiraEm = br.jus.tjba.aclp.util.TempoAtual.agora().plusDays(7); // 7 dias de validade
        }
        if (this.token == null) {
            this.token = UUID.randomUUID().toString();
        }
        if (this.status == null) {
            this.status = StatusConvite.PENDENTE;
        }
        if (this.quantidadeUsos == null) {
            this.quantidadeUsos = 1;
        }
        if (this.usosRealizados == null) {
            this.usosRealizados = 0;
        }
    }

    public boolean isGenerico() {
        return this.email == null || this.email.trim().isEmpty();
    }

    public boolean isValido() {
        return status == StatusConvite.PENDENTE &&
                !isExpirado() &&
                usosRealizados == 0; // Só válido se nunca foi usado
    }

    public boolean isExpirado() {
        return br.jus.tjba.aclp.util.TempoAtual.agora().isAfter(expiraEm);
    }

    public boolean temUsosDisponiveis() {
        return usosRealizados == 0;
    }

    public int getUsosRestantes() {
        return usosRealizados == 0 ? 1 : 0;
    }

    public void registrarUso() {
        this.usosRealizados = 1;
        this.status = StatusConvite.ATIVADO;
        this.ativadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();
    }

    /**
     * Ativa o convite após criação do usuário (uso único)
     * @param usuario Usuário criado
     * @param ip IP de onde foi ativado
     */
    public void ativar(Usuario usuario, String ip) {
        this.usuario = usuario;
        this.status = StatusConvite.ATIVADO;
        this.ativadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();
        this.ipAtivacao = ip;
        this.usosRealizados = 1;
    }

    public void cancelar() {
        this.status = StatusConvite.CANCELADO;
    }

    public void expirar() {
        this.status = StatusConvite.EXPIRADO;
    }

    public boolean validarEmail(String emailParaValidar) {
        if (emailParaValidar == null || emailParaValidar.trim().isEmpty()) {
            return false;
        }

        // Se convite tem email específico, deve coincidir
        if (!isGenerico()) {
            return this.email.equalsIgnoreCase(emailParaValidar.trim());
        }

        // Se é genérico, qualquer email válido é aceito
        return true;
    }

    public String getTipoConviteDescricao() {
        if (isGenerico()) {
            return "Link Genérico (Uso Único)";
        } else {
            return "Convite Específico (Uso Único)";
        }
    }
}
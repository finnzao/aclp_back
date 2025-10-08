package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.UserInviteDTO.*;
import br.jus.tjba.aclp.model.UserInvite;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.enums.StatusUsuario;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.repository.UserInviteRepository;
import br.jus.tjba.aclp.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serviço para gerenciar convites de usuários
 * Implementa o fluxo de convite com link de primeiro acesso
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserInviteService {

    private final UserInviteRepository inviteRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${aclp.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${aclp.invite.default-expiry-hours:72}")
    private int defaultExpiryHours;

    /**
     * Cria novo convite (apenas ADMIN pode executar)
     */
    @Transactional
    public ConviteResponseDTO criarConvite(CriarConviteDTO dto, Usuario adminCriador, String ipAddress) {
        log.info("Criando convite para usuário - Email: {}, Tipo: {}, Admin: {}",
                dto.getEmail(), dto.getTipoUsuario(), adminCriador.getEmail());

        // Validar se admin tem permissão
        if (!adminCriador.isAdmin()) {
            throw new IllegalArgumentException("Apenas administradores podem criar convites");
        }

        // Verificar se email já está em uso
        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email já está cadastrado no sistema");
        }

        // Verificar se já existe convite pendente para este email
        if (inviteRepository.existsPendingByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Já existe um convite pendente para este email");
        }

        // Criar convite
        String token = UUID.randomUUID().toString();
        LocalDateTime expiraEm = LocalDateTime.now().plusHours(
                dto.getValidadeHoras() != null ? dto.getValidadeHoras() : defaultExpiryHours
        );

        UserInvite invite = UserInvite.builder()
                .token(token)
                .email(dto.getEmail())
                .nome(dto.getNome())
                .tipoUsuario(dto.getTipoUsuario())
                .departamento(dto.getDepartamento())
                .telefone(dto.getTelefone())
                .escopo(dto.getEscopo())
                .status("PENDING")
                .criadoPor(adminCriador)
                .expiraEm(expiraEm)
                .ipCriacao(ipAddress)
                .build();

        UserInvite saved = inviteRepository.save(invite);

        // Gerar link de ativação
        String linkAtivacao = gerarLinkAtivacao(token);

        // Enviar email
        enviarEmailConvite(saved, linkAtivacao, dto.getMensagemPersonalizada());

        log.info("Convite criado com sucesso - ID: {}, Email: {}, Expira em: {}",
                saved.getId(), saved.getEmail(), saved.getExpiraEm());

        return ConviteResponseDTO.builder()
                .id(saved.getId())
                .token(token)
                .email(saved.getEmail())
                .nome(saved.getNome())
                .tipoUsuario(saved.getTipoUsuario())
                .linkAtivacao(linkAtivacao)
                .expiraEm(saved.getExpiraEm())
                .horasValidade(saved.getHorasRestantes())
                .status("success")
                .message("Convite enviado com sucesso para " + saved.getEmail())
                .build();
    }

    /**
     * Valida token de convite
     */
    @Transactional(readOnly = true)
    public TokenInfoDTO validarToken(String token) {
        log.debug("Validando token de convite");

        UserInvite invite = inviteRepository.findByToken(token)
                .orElse(null);

        if (invite == null) {
            return TokenInfoDTO.builder()
                    .valido(false)
                    .status("INVALID")
                    .message("Token inválido ou não encontrado")
                    .build();
        }

        if (invite.isExpirado()) {
            return TokenInfoDTO.builder()
                    .valido(false)
                    .status("EXPIRED")
                    .message("Este convite expirou")
                    .email(invite.getEmail())
                    .build();
        }

        if (invite.isAceito()) {
            return TokenInfoDTO.builder()
                    .valido(false)
                    .status("ALREADY_USED")
                    .message("Este convite já foi utilizado")
                    .email(invite.getEmail())
                    .build();
        }

        if (!invite.podeTentar()) {
            return TokenInfoDTO.builder()
                    .valido(false)
                    .status("TOO_MANY_ATTEMPTS")
                    .message("Muitas tentativas de acesso. Solicite um novo convite.")
                    .email(invite.getEmail())
                    .build();
        }

        return TokenInfoDTO.builder()
                .valido(true)
                .status("VALID")
                .email(invite.getEmail())
                .nome(invite.getNome())
                .tipoUsuario(invite.getTipoUsuario())
                .departamento(invite.getDepartamento())
                .expiraEm(invite.getExpiraEm())
                .horasRestantes(invite.getHorasRestantes())
                .message("Token válido")
                .build();
    }

    /**
     * Ativa convite e cria usuário
     */
    @Transactional
    public Usuario ativarConvite(AtivarConviteDTO dto, String ipAddress) {
        log.info("Ativando convite - Token: {}", dto.getToken());

        // Buscar e validar convite
        UserInvite invite = inviteRepository.findByToken(dto.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Token inválido"));

        // Validações
        if (invite.isExpirado()) {
            throw new IllegalArgumentException("Convite expirado");
        }

        if (invite.isAceito()) {
            throw new IllegalArgumentException("Convite já utilizado");
        }

        if (!invite.podeTentar()) {
            throw new IllegalArgumentException("Muitas tentativas. Solicite novo convite.");
        }

        // Incrementar tentativas
        invite.incrementarTentativas();

        // Verificar se email já foi cadastrado (double check)
        if (usuarioRepository.existsByEmail(invite.getEmail())) {
            invite.marcarComoAceito(ipAddress); // Marca como usado
            inviteRepository.save(invite);
            throw new IllegalArgumentException("Email já cadastrado no sistema");
        }

        // Criar usuário
        Usuario novoUsuario = Usuario.builder()
                .nome(invite.getNome())
                .email(invite.getEmail())
                .senha(passwordEncoder.encode(dto.getSenha()))
                .tipo(invite.getTipoUsuario())
                .departamento(invite.getDepartamento())
                .statusUsuario(StatusUsuario.ACTIVE) // Ativo após definir senha
                .ativo(true)
                .mfaEnabled(dto.getHabilitarMFA() != null ? dto.getHabilitarMFA() : false)
                .build();

        Usuario usuarioSalvo = usuarioRepository.save(novoUsuario);

        // Marcar convite como aceito
        invite.marcarComoAceito(ipAddress);
        inviteRepository.save(invite);

        // Enviar email de boas-vindas
        enviarEmailBoasVindas(usuarioSalvo);

        log.info("Usuário criado com sucesso via convite - ID: {}, Email: {}",
                usuarioSalvo.getId(), usuarioSalvo.getEmail());

        return usuarioSalvo;
    }

    /**
     * Reenviar convite
     */
    @Transactional
    public ConviteResponseDTO reenviarConvite(ReenviarConviteDTO dto, Usuario adminSolicitante) {
        log.info("Reenviando convite - ID: {}, Admin: {}", dto.getConviteId(), adminSolicitante.getEmail());

        if (!adminSolicitante.isAdmin()) {
            throw new IllegalArgumentException("Apenas administradores podem reenviar convites");
        }

        UserInvite invite = inviteRepository.findById(dto.getConviteId())
                .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));

        if (invite.isAceito()) {
            throw new IllegalArgumentException("Convite já foi aceito");
        }

        // Gerar novo token e data de expiração
        invite.setToken(UUID.randomUUID().toString());
        invite.setExpiraEm(LocalDateTime.now().plusHours(dto.getNovaValidadeHoras()));
        invite.setStatus("PENDING");
        invite.setTentativasAcesso(0);

        UserInvite updated = inviteRepository.save(invite);

        // Reenviar email
        String linkAtivacao = gerarLinkAtivacao(updated.getToken());
        enviarEmailConvite(updated, linkAtivacao, dto.getMensagemPersonalizada());

        return ConviteResponseDTO.builder()
                .id(updated.getId())
                .token(updated.getToken())
                .email(updated.getEmail())
                .linkAtivacao(linkAtivacao)
                .expiraEm(updated.getExpiraEm())
                .status("success")
                .message("Convite reenviado com sucesso")
                .build();
    }

    /**
     * Cancelar convite
     */
    @Transactional
    public void cancelarConvite(CancelarConviteDTO dto, Usuario adminSolicitante) {
        log.info("Cancelando convite - ID: {}, Admin: {}", dto.getConviteId(), adminSolicitante.getEmail());

        if (!adminSolicitante.isAdmin()) {
            throw new IllegalArgumentException("Apenas administradores podem cancelar convites");
        }

        UserInvite invite = inviteRepository.findById(dto.getConviteId())
                .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));

        if (invite.isAceito()) {
            throw new IllegalArgumentException("Convite já foi aceito e não pode ser cancelado");
        }

        invite.cancelar();
        inviteRepository.save(invite);

        log.info("Convite cancelado - ID: {}, Email: {}", invite.getId(), invite.getEmail());
    }

    /**
     * Listar convites (para admin)
     */
    @Transactional(readOnly = true)
    public List<ConviteListDTO> listarConvites(String status) {
        List<UserInvite> invites = status != null ?
                inviteRepository.findByStatus(status) :
                inviteRepository.findAll();

        return invites.stream()
                .map(this::toListDTO)
                .collect(Collectors.toList());
    }

    /**
     * Limpar convites expirados (job automático)
     */
    @Transactional
    public int limparConvitesExpirados() {
        log.debug("Limpando convites expirados");

        List<UserInvite> expirados = inviteRepository.findExpiredInvites(LocalDateTime.now());

        for (UserInvite invite : expirados) {
            invite.marcarComoExpirado();
        }

        inviteRepository.saveAll(expirados);

        if (!expirados.isEmpty()) {
            log.info("Marcados {} convites como expirados", expirados.size());
        }

        return expirados.size();
    }

    // Métodos privados auxiliares

    private String gerarLinkAtivacao(String token) {
        return String.format("%s/ativar-conta?token=%s", frontendUrl, token);
    }

    private void enviarEmailConvite(UserInvite invite, String linkAtivacao, String mensagemPersonalizada) {
        String tipoTexto = invite.getTipoUsuario() == TipoUsuario.ADMIN ? "Administrador" : "Usuário";

        String conteudo = String.format("""
                Olá %s,
                
                Você foi convidado(a) para acessar o Sistema ACLP do TJBA com perfil de %s.
                
                Para ativar sua conta, clique no link abaixo e defina sua senha:
                %s
                
                %s
                
                Informações do convite:
                - Email: %s
                - Departamento: %s
                - Validade: %d horas
                - Expira em: %s
                
                Importante:
                - Este link é de uso único
                - Após expirar, será necessário solicitar novo convite
                - Use uma senha forte com letras, números e símbolos
                
                Atenciosamente,
                Sistema ACLP - TJBA
                """,
                invite.getNome(),
                tipoTexto,
                linkAtivacao,
                mensagemPersonalizada != null ? "\n" + mensagemPersonalizada + "\n" : "",
                invite.getEmail(),
                invite.getDepartamento() != null ? invite.getDepartamento() : "Não especificado",
                invite.getHorasRestantes(),
                invite.getExpiraEm()
        );

        emailService.enviarEmail(invite.getEmail(), "Convite - Sistema ACLP TJBA", conteudo);
    }

    private void enviarEmailBoasVindas(Usuario usuario) {
        String conteudo = String.format("""
                Olá %s,
                
                Sua conta no Sistema ACLP foi ativada com sucesso!
                
                Você já pode acessar o sistema com seu email e senha cadastrados.
                
                Informações da conta:
                - Email: %s
                - Tipo de acesso: %s
                - Departamento: %s
                
                Para acessar o sistema: %s
                
                Em caso de dúvidas, entre em contato com o suporte.
                
                Atenciosamente,
                Sistema ACLP - TJBA
                """,
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTipo().getLabel(),
                usuario.getDepartamento() != null ? usuario.getDepartamento() : "Não especificado",
                frontendUrl
        );

        emailService.enviarEmail(usuario.getEmail(), "Bem-vindo ao Sistema ACLP", conteudo);
    }

    private ConviteListDTO toListDTO(UserInvite invite) {
        return ConviteListDTO.builder()
                .id(invite.getId())
                .email(invite.getEmail())
                .nome(invite.getNome())
                .tipoUsuario(invite.getTipoUsuario())
                .departamento(invite.getDepartamento())
                .status(invite.getStatus())
                .criadoEm(invite.getCriadoEm())
                .expiraEm(invite.getExpiraEm())
                .aceitoEm(invite.getAceitoEm())
                .criadoPorNome(invite.getCriadoPor().getNome())
                .horasRestantes(invite.getHorasRestantes())
                .tentativasAcesso(invite.getTentativasAcesso())
                .build();
    }
}
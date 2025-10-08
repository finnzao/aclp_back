package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ConviteDTO.*;
import br.jus.tjba.aclp.model.Convite;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.enums.StatusConvite;
import br.jus.tjba.aclp.model.enums.StatusUsuario;
import br.jus.tjba.aclp.repository.ConviteRepository;
import br.jus.tjba.aclp.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço para gerenciamento de convites
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConviteService {


    private final ConviteRepository conviteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthService authService;

    @Value("${aclp.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Cria convite copiando comarca e departamento do admin logado
     */
    @Transactional
    public ConviteResponse criarConvite(CriarConviteRequest request, HttpServletRequest httpRequest) {
        log.info("Criando convite para: {}", request.getEmail());

        String email = request.getEmail().toLowerCase().trim();

        // Verificar se email já está cadastrado
        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email já cadastrado no sistema");
        }

        // Verificar se já existe convite pendente
        if (conviteRepository.existsByEmailAndStatus(email, StatusConvite.PENDENTE)) {
            throw new IllegalArgumentException("Já existe um convite pendente para este email");
        }

        // Pegar admin atual
        Usuario admin = authService.getUsuarioAtual();
        if (admin == null) {
            throw new IllegalArgumentException("Usuário não autenticado");
        }

        // Criar convite COPIANDO dados do admin
        Convite convite = Convite.builder()
                .email(email)
                .tipoUsuario(request.getTipoUsuario())
                .comarca(admin.getComarca())           // COPIA comarca do admin
                .departamento(admin.getDepartamento()) // COPIA departamento do admin
                .criadoPor(admin)
                .ipCriacao(extractIpAddress(httpRequest))
                .build();

        convite = conviteRepository.save(convite);

        // Enviar email
        try {
            enviarEmailConvite(convite);
        } catch (Exception e) {
            log.error("Erro ao enviar email de convite: {}", e.getMessage());
        }

        String linkConvite = String.format("%s/invite/%s", frontendUrl, convite.getToken());

        log.info("Convite criado - ID: {}, Comarca: {}, Departamento: {}",
                convite.getId(), convite.getComarca(), convite.getDepartamento());

        return ConviteResponse.builder()
                .id(convite.getId())
                .token(convite.getToken())
                .email(convite.getEmail())
                .tipoUsuario(convite.getTipoUsuario())
                .status(convite.getStatus())
                .linkConvite(linkConvite)
                .comarca(convite.getComarca())
                .departamento(convite.getDepartamento())
                .criadoEm(convite.getCriadoEm())
                .expiraEm(convite.getExpiraEm())
                .criadoPorNome(admin.getNome())
                .criadoPorId(admin.getId())
                .build();
    }

    /**
     * Valida convite retornando comarca e departamento para pré-preencher
     */
    @Transactional(readOnly = true)
    public ValidarConviteResponse validarConvite(String token) {
        log.info("Validando convite - Token: {}", token);

        Convite convite = conviteRepository.findByToken(token).orElse(null);

        if (convite == null) {
            return ValidarConviteResponse.builder()
                    .valido(false)
                    .mensagem("Convite não encontrado")
                    .build();
        }

        if (convite.isExpirado()) {
            return ValidarConviteResponse.builder()
                    .valido(false)
                    .mensagem("Este convite expirou em " + convite.getExpiraEm())
                    .build();
        }

        if (convite.getStatus() != StatusConvite.PENDENTE) {
            String msg = convite.getStatus() == StatusConvite.ATIVADO
                    ? "Este convite já foi utilizado"
                    : "Este convite não está mais disponível";

            return ValidarConviteResponse.builder()
                    .valido(false)
                    .mensagem(msg)
                    .build();
        }

        return ValidarConviteResponse.builder()
                .valido(true)
                .email(convite.getEmail())
                .tipoUsuario(convite.getTipoUsuario())
                .expiraEm(convite.getExpiraEm())
                .comarca(convite.getComarca())           // Retorna para pré-preencher
                .departamento(convite.getDepartamento()) // Retorna para pré-preencher
                .mensagem("Convite válido")
                .build();
    }

    /**
     * Ativa convite criando usuário com comarca e departamento do convite
     */
    @Transactional
    public AtivarConviteResponse ativarConvite(AtivarConviteRequest request, HttpServletRequest httpRequest) {
        log.info("Ativando convite - Token: {}", request.getToken());

        Convite convite = conviteRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));

        if (!convite.isValido()) {
            throw new IllegalArgumentException("Convite inválido ou expirado");
        }

        if (usuarioRepository.existsByEmail(convite.getEmail())) {
            throw new IllegalArgumentException("Este email já está em uso");
        }

        if (!request.senhasCoincidentes()) {
            throw new IllegalArgumentException("As senhas não coincidem");
        }

        // Criar usuário HERDANDO comarca e departamento do convite
        Usuario usuario = Usuario.builder()
                .nome(request.getNome())
                .email(convite.getEmail())
                .senha(passwordEncoder.encode(request.getSenha()))
                .tipo(convite.getTipoUsuario())
                .comarca(convite.getComarca())
                .departamento(convite.getDepartamento())
                .cargo(request.getCargo())
                .ativo(true)
                .statusUsuario(StatusUsuario.ACTIVE)
                .emailVerificado(true)
                .deveTrocarSenha(false)
                .build();

        usuario = usuarioRepository.save(usuario);

        // Atualizar convite
        convite.ativar(usuario, extractIpAddress(httpRequest));
        conviteRepository.save(convite);

        log.info("Convite ativado - Usuario: {}, Comarca: {}, Departamento: {}",
                usuario.getEmail(), usuario.getComarca(), usuario.getDepartamento());

        return AtivarConviteResponse.builder()
                .success(true)
                .message("Conta criada com sucesso! Você já pode fazer login.")
                .usuario(UsuarioInfoDTO.builder()
                        .id(usuario.getId())
                        .nome(usuario.getNome())
                        .email(usuario.getEmail())
                        .tipo(usuario.getTipo())
                        .comarca(usuario.getComarca())
                        .departamento(usuario.getDepartamento())
                        .build())
                .build();
    }

    /**
     * Lista todos os convites
     */
    @Transactional(readOnly = true)
    public List<ConviteListItem> listarConvites() {
        return conviteRepository.findAllOrderByCreatedDesc().stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
    }

    /**
     * Busca convite por ID
     */
    @Transactional(readOnly = true)
    public ConviteResponse buscarPorId(Long id) {
        Convite convite = conviteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));

        String linkConvite = String.format("%s/invite/%s", frontendUrl, convite.getToken());

        return ConviteResponse.builder()
                .id(convite.getId())
                .token(convite.getToken())
                .email(convite.getEmail())
                .tipoUsuario(convite.getTipoUsuario())
                .status(convite.getStatus())
                .linkConvite(linkConvite)
                .criadoEm(convite.getCriadoEm())
                .expiraEm(convite.getExpiraEm())
                .criadoPorNome(convite.getCriadoPor() != null ? convite.getCriadoPor().getNome() : null)
                .build();
    }

    /**
     * Cancela convite
     */
    @Transactional
    public void cancelarConvite(Long id) {
        Convite convite = conviteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));

        if (convite.getStatus() != StatusConvite.PENDENTE) {
            throw new IllegalArgumentException("Apenas convites pendentes podem ser cancelados");
        }

        convite.cancelar();
        conviteRepository.save(convite);

        log.info("Convite cancelado - ID: {}", id);
    }

    /**
     * Reenvia convite
     */
    @Transactional
    public void reenviarConvite(Long id) {
        Convite convite = conviteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));

        if (convite.getStatus() != StatusConvite.PENDENTE) {
            throw new IllegalArgumentException("Apenas convites pendentes podem ser reenviados");
        }

        if (convite.isExpirado()) {
            throw new IllegalArgumentException("Convite expirado. Crie um novo convite.");
        }

        try {
            enviarEmailConvite(convite);
            log.info("Convite reenviado - ID: {}", id);
        } catch (Exception e) {
            log.error("Erro ao reenviar convite: {}", e.getMessage());
            throw new RuntimeException("Erro ao enviar email");
        }
    }

    /**
     * Retorna estatísticas de convites
     */
    @Transactional(readOnly = true)
    public ConviteStats getEstatisticas() {
        long total = conviteRepository.count();
        long pendentes = conviteRepository.countByStatus(StatusConvite.PENDENTE);
        long ativados = conviteRepository.countByStatus(StatusConvite.ATIVADO);
        long expirados = conviteRepository.countByStatus(StatusConvite.EXPIRADO);
        long cancelados = conviteRepository.countByStatus(StatusConvite.CANCELADO);

        return ConviteStats.builder()
                .totalConvites(total)
                .pendentes(pendentes)
                .ativados(ativados)
                .expirados(expirados)
                .cancelados(cancelados)
                .build();
    }

    /**
     * Job agendado para expirar convites automaticamente
     * Executa diariamente à meia-noite
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void expirarConvitesAutomaticamente() {
        log.info("Iniciando verificação de convites expirados");

        List<Convite> convitesExpirados = conviteRepository
                .findConvitesExpirados(LocalDateTime.now());

        for (Convite convite : convitesExpirados) {
            convite.expirar();
            conviteRepository.save(convite);
        }

        log.info("Convites expirados: {}", convitesExpirados.size());
    }

    // ========== MÉTODOS AUXILIARES ==========


    private void enviarEmailConvite(Convite convite) {
        String linkConvite = String.format("%s/invite/%s", frontendUrl, convite.getToken());

        String assunto = "Convite para Sistema ACLP - TJBA";

        String conteudo = String.format("""
                        Olá!
                        
                        Você foi convidado para acessar o Sistema ACLP do Tribunal de Justiça da Bahia.
                        
                        Perfil: %s
                        Comarca: %s
                        Departamento: %s
                        Email: %s
                        
                        Para criar sua conta, acesse o link abaixo:
                        %s
                        
                        Este link é válido até: %s
                        
                        Após criar sua conta, você poderá fazer login com seu email e a senha que escolher.
                        
                        Atenciosamente,
                        Sistema ACLP - TJBA
                        """,
                convite.getTipoUsuario().getLabel(),
                convite.getComarca() != null ? convite.getComarca() : "Não definida",
                convite.getDepartamento() != null ? convite.getDepartamento() : "Não definido",
                convite.getEmail(),
                linkConvite,
                convite.getExpiraEm()
        );

        emailService.enviarEmail(convite.getEmail(), assunto, conteudo);
    }

    private ConviteListItem toListItem(Convite convite) {
        return ConviteListItem.builder()
                .id(convite.getId())
                .email(convite.getEmail())
                .tipoUsuario(convite.getTipoUsuario())
                .status(convite.getStatus())
                .criadoEm(convite.getCriadoEm())
                .expiraEm(convite.getExpiraEm())
                .ativadoEm(convite.getAtivadoEm())
                .expirado(convite.isExpirado())
                .criadoPorNome(convite.getCriadoPor() != null ? convite.getCriadoPor().getNome() : null)
                .usuarioCriadoNome(convite.getUsuario() != null ? convite.getUsuario().getNome() : null)
                .build();
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
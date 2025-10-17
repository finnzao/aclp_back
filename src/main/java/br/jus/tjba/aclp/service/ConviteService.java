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
 * TODOS OS CONVITES SÃO DE USO ÚNICO
 * Suporta dois tipos de convites:
 * 1. Convite Específico: com email definido, enviado por email
 * 2. Convite Genérico: sem email, link compartilhável (uso único)
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
     * Gera link de convite genérico (sem email específico)
     * USO ÚNICO - após ser usado uma vez, não pode ser reutilizado
     */
    @Transactional
    public LinkConviteResponse gerarLinkConvite(GerarLinkConviteRequest request, HttpServletRequest httpRequest) {
        log.info("Gerando link de convite genérico (uso único) - Tipo: {}", request.getTipoUsuario());

        // Validar admin atual
        Usuario admin = authService.getUsuarioAtual();
        if (admin == null) {
            throw new IllegalArgumentException("Usuário não autenticado");
        }

        // Validar parâmetros
        if (request.getDiasValidade() == null || request.getDiasValidade() < 1) {
            request.setDiasValidade(30); // 30 dias padrão
        }

        // Criar convite genérico (SEM email) - USO ÚNICO
        Convite convite = Convite.builder()
                .email(null)  // NULL = convite genérico
                .tipoUsuario(request.getTipoUsuario())
                .comarca(admin.getComarca())
                .departamento(admin.getDepartamento())
                .quantidadeUsos(1)  // SEMPRE 1 - uso único
                .usosRealizados(0)
                .criadoPor(admin)
                .ipCriacao(extractIpAddress(httpRequest))
                .expiraEm(LocalDateTime.now().plusDays(request.getDiasValidade()))
                .build();

        convite = conviteRepository.save(convite);

        // Link usa /cadastro para convites genéricos
        String linkConvite = String.format("%s/cadastro/%s", frontendUrl, convite.getToken());

        log.info("Link genérico criado (uso único) - ID: {}, Token: {}, Validade: {}",
                convite.getId(), convite.getToken(), convite.getExpiraEm());

        return LinkConviteResponse.builder()
                .id(convite.getId())
                .token(convite.getToken())
                .link(linkConvite)
                .tipoUsuario(convite.getTipoUsuario())
                .comarca(convite.getComarca())
                .departamento(convite.getDepartamento())
                .expiraEm(convite.getExpiraEm())
                .criadoPorNome(admin.getNome())
                .usado(false)
                .build();
    }

    /**
     * Cria convite específico (com email) e envia por email
     * USO ÚNICO
     */
    @Transactional
    public ConviteResponse criarConvite(CriarConviteRequest request, HttpServletRequest httpRequest) {
        log.info("Criando convite específico (uso único) para: {}", request.getEmail());

        String email = request.getEmail().toLowerCase().trim();

        // Verificar se email já está cadastrado
        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email já cadastrado no sistema");
        }

        // Verificar se já existe convite pendente
        if (conviteRepository.existsByEmailAndStatus(email, StatusConvite.PENDENTE)) {
            throw new IllegalArgumentException("Já existe um convite pendente para este email");
        }

        // Obter admin atual
        Usuario admin = authService.getUsuarioAtual();
        if (admin == null) {
            throw new IllegalArgumentException("Usuário não autenticado");
        }

        // Criar convite específico (COM email) - USO ÚNICO
        Convite convite = Convite.builder()
                .email(email)
                .tipoUsuario(request.getTipoUsuario())
                .comarca(admin.getComarca())
                .departamento(admin.getDepartamento())
                .quantidadeUsos(1)  // SEMPRE 1 - uso único
                .usosRealizados(0)
                .criadoPor(admin)
                .ipCriacao(extractIpAddress(httpRequest))
                .build();

        convite = conviteRepository.save(convite);

        // Enviar email
        try {
            enviarEmailConvite(convite);
            log.info("Email de convite enviado para: {}", email);
        } catch (Exception e) {
            log.error("Erro ao enviar email de convite: {}", e.getMessage(), e);
        }

        // Link usa /invite para convites específicos
        String linkConvite = String.format("%s/invite/%s", frontendUrl, convite.getToken());

        log.info("Convite específico criado (uso único) - ID: {}, Email: {}",
                convite.getId(), convite.getEmail());

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
                .isGenerico(false)
                .build();
    }

    /**
     * Valida convite retornando informações para pré-preencher formulário
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

        // Verificar se já foi usado
        if (convite.getUsosRealizados() > 0) {
            return ValidarConviteResponse.builder()
                    .valido(false)
                    .mensagem("Este convite já foi utilizado e não pode ser reutilizado")
                    .build();
        }

        // Verificar expiração
        if (convite.isExpirado()) {
            return ValidarConviteResponse.builder()
                    .valido(false)
                    .mensagem("Este convite expirou em " + convite.getExpiraEm())
                    .build();
        }

        // Verificar status
        if (convite.getStatus() != StatusConvite.PENDENTE) {
            String msg;
            if (convite.getStatus() == StatusConvite.ATIVADO) {
                msg = "Este convite já foi utilizado";
            } else if (convite.getStatus() == StatusConvite.CANCELADO) {
                msg = "Este convite foi cancelado";
            } else {
                msg = "Este convite não está mais disponível";
            }

            return ValidarConviteResponse.builder()
                    .valido(false)
                    .mensagem(msg)
                    .build();
        }

        // Definir campos editáveis
        String[] camposEditaveis;
        if (convite.isGenerico()) {
            // Convite genérico: usuário pode preencher email, nome, senha, cargo
            camposEditaveis = new String[]{"email", "nome", "senha", "cargo"};
        } else {
            // Convite específico: email já definido
            camposEditaveis = new String[]{"nome", "senha", "cargo"};
        }

        log.info("Convite válido - Tipo: {}, Genérico: {}",
                convite.getTipoConviteDescricao(), convite.isGenerico());

        return ValidarConviteResponse.builder()
                .valido(true)
                .tipoUsuario(convite.getTipoUsuario())
                .comarca(convite.getComarca())
                .departamento(convite.getDepartamento())
                .expiraEm(convite.getExpiraEm())
                .camposEditaveis(camposEditaveis)
                .mensagem("Convite válido")
                .build();
    }

    /**
     * Ativa convite criando novo usuário
     * USO ÚNICO - após ativação, convite não pode ser reutilizado
     */
    @Transactional
    public AtivarConviteResponse ativarConvite(AtivarConviteRequest request, HttpServletRequest httpRequest) {
        log.info("Ativando convite (uso único) - Token: {}", request.getToken());

        // Buscar convite
        Convite convite = conviteRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));

        // Verificar se já foi usado
        if (convite.getUsosRealizados() > 0) {
            throw new IllegalArgumentException("Este convite já foi utilizado e não pode ser reutilizado");
        }

        // Validar convite
        if (!convite.isValido()) {
            throw new IllegalArgumentException("Convite inválido ou expirado");
        }

        // Validar senhas
        if (!request.senhasCoincidentes()) {
            throw new IllegalArgumentException("As senhas não coincidem");
        }

        // Determinar email final
        String emailFinal;
        if (convite.isGenerico()) {
            // Convite genérico: email vem do request
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                throw new IllegalArgumentException("Email é obrigatório");
            }
            emailFinal = request.getEmail().toLowerCase().trim();
        } else {
            // Convite específico: email vem do convite
            emailFinal = convite.getEmail();
        }

        // Verificar se email já existe
        if (usuarioRepository.existsByEmail(emailFinal)) {
            throw new IllegalArgumentException("Este email já está cadastrado no sistema");
        }

        // Criar usuário herdando dados do convite
        Usuario usuario = Usuario.builder()
                .nome(request.getNome())
                .email(emailFinal)
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

        // Ativar convite (marca como usado - USO ÚNICO)
        convite.ativar(usuario, extractIpAddress(httpRequest));
        conviteRepository.save(convite);

        log.info("Convite ativado com sucesso (uso único) - Usuario: {}, Email: {}, Convite ID: {}",
                usuario.getId(), usuario.getEmail(), convite.getId());

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
                        .cargo(usuario.getCargo())
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

        // Determinar URL apropriada
        String linkConvite = convite.isGenerico()
                ? String.format("%s/cadastro/%s", frontendUrl, convite.getToken())
                : String.format("%s/invite/%s", frontendUrl, convite.getToken());

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
                .criadoPorNome(convite.getCriadoPor() != null ? convite.getCriadoPor().getNome() : null)
                .criadoPorId(convite.getCriadoPor() != null ? convite.getCriadoPor().getId() : null)
                .isGenerico(convite.isGenerico())
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

        log.info("Convite cancelado - ID: {}, Tipo: {}", id, convite.getTipoConviteDescricao());
    }

    /**
     * Reenvia email de convite (apenas para convites específicos)
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

        if (convite.getUsosRealizados() > 0) {
            throw new IllegalArgumentException("Este convite já foi utilizado");
        }

        // Só envia email se for convite específico
        if (convite.isGenerico()) {
            throw new IllegalArgumentException("Convites genéricos não possuem email para reenvio. Compartilhe o link diretamente.");
        }

        try {
            enviarEmailConvite(convite);
            log.info("Convite reenviado - ID: {}, Email: {}", id, convite.getEmail());
        } catch (Exception e) {
            log.error("Erro ao reenviar convite: {}", e.getMessage(), e);
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

        log.info("Convites expirados automaticamente: {}", convitesExpirados.size());
    }

    // ========== MÉTODOS AUXILIARES ==========

    /**
     * Envia email de convite (apenas para convites específicos)
     */
    private void enviarEmailConvite(Convite convite) {
        if (convite.isGenerico()) {
            log.warn("Tentativa de enviar email para convite genérico - ID: {}", convite.getId());
            return;
        }

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
                        
                        ⚠️ IMPORTANTE: Este link é de uso único e válido até: %s
                        Após criar sua conta, o link será invalidado automaticamente.
                        
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

    /**
     * Converte Convite para ConviteListItem
     */
    private ConviteListItem toListItem(Convite convite) {
        return ConviteListItem.builder()
                .id(convite.getId())
                .email(convite.getEmail())
                .tipoUsuario(convite.getTipoUsuario())
                .status(convite.getStatus())
                .comarca(convite.getComarca())
                .departamento(convite.getDepartamento())
                .criadoEm(convite.getCriadoEm())
                .expiraEm(convite.getExpiraEm())
                .ativadoEm(convite.getAtivadoEm())
                .expirado(convite.isExpirado())
                .criadoPorNome(convite.getCriadoPor() != null ? convite.getCriadoPor().getNome() : null)
                .usuarioCriadoNome(convite.getUsuario() != null ? convite.getUsuario().getNome() : null)
                .isGenerico(convite.isGenerico())
                .usado(convite.getUsosRealizados() > 0)
                .build();
    }

    /**
     * Extrai endereço IP da requisição
     */
    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
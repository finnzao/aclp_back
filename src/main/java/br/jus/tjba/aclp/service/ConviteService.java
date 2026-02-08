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

@Slf4j
@Service
@RequiredArgsConstructor
public class ConviteService {

    private final ConviteRepository conviteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailConviteService emailConviteService; // FIX #11: classe separada
    private final AuthService authService;

    @Value("${aclp.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public ConviteResponse criarConvite(CriarConviteRequest request, HttpServletRequest httpRequest) {
        log.info("Criando convite específico para: {}", request.getEmail());

        String email = request.getEmail().toLowerCase().trim();

        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email já cadastrado no sistema");
        }

        if (conviteRepository.existsByEmailAndStatus(email, StatusConvite.PENDENTE)) {
            throw new IllegalArgumentException("Já existe um convite pendente para este email");
        }

        Usuario admin = authService.getUsuarioAtual();
        if (admin == null) {
            throw new IllegalArgumentException("Usuário não autenticado");
        }

        Convite convite = Convite.builder()
                .email(email)
                .tipoUsuario(request.getTipoUsuario())
                .comarca(admin.getComarca())
                .departamento(admin.getDepartamento())
                .quantidadeUsos(1)
                .usosRealizados(0)
                .criadoPor(admin)
                .ipCriacao(extractIpAddress(httpRequest))
                .build();

        convite = conviteRepository.save(convite);

        String linkConvite = String.format("%s/invite/%s", frontendUrl, convite.getToken());

        ConviteResponse response = ConviteResponse.builder()
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

        // FIX #11: Delegar para classe separada (proxy intercepta corretamente)
        emailConviteService.enviarEmailConviteAsync(convite.getId());

        log.info("Convite específico criado - ID: {}, Email: {}", convite.getId(), convite.getEmail());

        return response;
    }

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

        if (convite.getUsosRealizados() > 0) {
            return ValidarConviteResponse.builder()
                    .valido(false)
                    .mensagem("Este convite já foi utilizado")
                    .build();
        }

        if (convite.isExpirado()) {
            return ValidarConviteResponse.builder()
                    .valido(false)
                    .mensagem("Este convite expirou em " + convite.getExpiraEm())
                    .build();
        }

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

        return ValidarConviteResponse.builder()
                .valido(true)
                .email(convite.getEmail())
                .tipoUsuario(convite.getTipoUsuario())
                .comarca(convite.getComarca())
                .departamento(convite.getDepartamento())
                .expiraEm(convite.getExpiraEm())
                .mensagem("Convite válido")
                .build();
    }

    @Transactional
    public AtivarConviteResponse ativarConvite(AtivarConviteRequest request, HttpServletRequest httpRequest) {
        log.info("Ativando convite - Token: {}", request.getToken());

        Convite convite = conviteRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));

        if (convite.getUsosRealizados() > 0) {
            throw new IllegalArgumentException("Este convite já foi utilizado");
        }

        if (!convite.isValido()) {
            throw new IllegalArgumentException("Convite inválido ou expirado");
        }

        if (!request.senhasCoincidentes()) {
            throw new IllegalArgumentException("As senhas não coincidem");
        }

        String emailFinal = convite.getEmail();

        if (emailFinal == null || emailFinal.trim().isEmpty()) {
            throw new IllegalStateException("Convite inválido: email não definido");
        }

        if (usuarioRepository.existsByEmail(emailFinal)) {
            throw new IllegalArgumentException("Este email já está cadastrado no sistema");
        }

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

        convite.ativar(usuario, extractIpAddress(httpRequest));
        conviteRepository.save(convite);

        log.info("Convite ativado com sucesso - Usuario: {}, Email: {}",
                usuario.getId(), usuario.getEmail());

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

    @Transactional(readOnly = true)
    public List<ConviteListItem> listarConvitesDoUsuarioAtual() {
        Usuario usuarioAtual = authService.getUsuarioAtual();

        if (usuarioAtual == null) {
            throw new IllegalArgumentException("Usuário não autenticado");
        }

        return conviteRepository.findByCriadoPorId(usuarioAtual.getId()).stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
    }

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
                .comarca(convite.getComarca())
                .departamento(convite.getDepartamento())
                .criadoEm(convite.getCriadoEm())
                .expiraEm(convite.getExpiraEm())
                .criadoPorNome(convite.getCriadoPor() != null ? convite.getCriadoPor().getNome() : null)
                .criadoPorId(convite.getCriadoPor() != null ? convite.getCriadoPor().getId() : null)
                .build();
    }

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

    // FIX #12: Removido readOnly = true (pode precisar registrar reenvios futuramente)
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

        // FIX #11: Delegar para classe separada
        emailConviteService.enviarEmailConviteAsync(id);
        log.info("Solicitação de reenvio de convite processada - ID: {}", id);
    }

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
                .usado(convite.getUsosRealizados() > 0)
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
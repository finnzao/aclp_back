package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.AuthDTO.*;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.RefreshToken;
import br.jus.tjba.aclp.model.LoginAttempt;
import br.jus.tjba.aclp.repository.UsuarioRepository;
import br.jus.tjba.aclp.repository.RefreshTokenRepository;
import br.jus.tjba.aclp.repository.LoginAttemptRepository;
import br.jus.tjba.aclp.security.JwtTokenProvider;
import br.jus.tjba.aclp.exception.AuthenticationException;
import br.jus.tjba.aclp.exception.AccountLockedException;
import br.jus.tjba.aclp.exception.InvalidTokenException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final AuditService auditService;

    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    @Value("${aclp.auth.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${aclp.auth.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;

    @Value("${aclp.auth.session-timeout-minutes:60}")
    private int sessionTimeoutMinutes;

    @Value("${aclp.auth.max-concurrent-sessions:6}")
    private int maxConcurrentSessions;

    @Value("${aclp.auth.refresh-token-validity-days:7}")
    private int refreshTokenValidityDays;

    @Value("${aclp.auth.password-reset-token-hours:2}")
    private int passwordResetTokenHours;

    @Value("${aclp.auth.mfa-enabled:false}")
    private boolean mfaEnabled;

    @Value("${aclp.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private final SecureRandom secureRandom = new SecureRandom();

    // =====================================================================
    // FIX #6: Job para limpeza de sessões expiradas (a cada 5 minutos)
    // Evita memory leak progressivo no ConcurrentHashMap
    // =====================================================================
    @Scheduled(fixedRate = 300_000)
    public void limparSessoesExpiradas() {
        int antes = activeSessions.size();
        LocalDateTime agora = LocalDateTime.now();

        activeSessions.entrySet().removeIf(entry ->
                entry.getValue().getExpiresAt().isBefore(agora));

        int removidas = antes - activeSessions.size();
        if (removidas > 0) {
            log.info("Sessões expiradas removidas: {} (restantes: {})", removidas, activeSessions.size());
        }
    }

    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request, HttpServletRequest httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Tentativa de login - Email: {}, IP: {}", request.getEmail(), ipAddress);

        try {
            checkRateLimiting(ipAddress);

            Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new BadCredentialsException("Credenciais invalidas"));

            checkAccountLocked(usuario);

            if (!usuario.getAtivo()) {
                throw new DisabledException("Conta desativada");
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getSenha())
            );

            // =====================================================================
            // FIX #3 + #7: MFA corrigido
            // - Se MFA está habilitado e não enviou código, retorna requiresMfa
            // - Se MFA está habilitado e enviou código inválido, lança exceção
            // - Se MFA está habilitado e código válido, continua o fluxo normal
            //   (antes retornava null, causando NullPointerException)
            // - validateMfaCode agora lança UnsupportedOperationException
            //   (MFA não está implementado, não deve aceitar qualquer código)
            // =====================================================================
            if (usuario.getMfaEnabled() && mfaEnabled) {
                if (request.getMfaCode() == null || request.getMfaCode().isEmpty()) {
                    return LoginResponseDTO.builder()
                            .success(false)
                            .requiresMfa(true)
                            .message("Codigo de autenticacao necessario")
                            .build();
                }

                if (!validateMfaCode(usuario, request.getMfaCode())) {
                    throw new AuthenticationException("Codigo de autenticacao invalido");
                }
                // MFA válido — continua o fluxo normal de login abaixo
            }

            handleConcurrentSessions(usuario, request.isForceLogin());

            String accessToken = jwtTokenProvider.generateToken(usuario);
            String refreshToken = generateRefreshToken(usuario, ipAddress, userAgent);

            SessionInfo session = createSession(usuario, accessToken, ipAddress, userAgent);

            usuario.resetarTentativas();
            usuario.registrarLogin();
            usuarioRepository.save(usuario);

            registerSuccessfulLogin(usuario, ipAddress, userAgent);

            auditService.logLogin(usuario, ipAddress, "SUCCESS");

            log.info("Login bem-sucedido - Usuario: {}, Session: {}", usuario.getEmail(), session.getSessionId());

            return LoginResponseDTO.builder()
                    .success(true)
                    .message("Login realizado com sucesso")
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtTokenProvider.getTokenValidity())
                    .sessionId(session.getSessionId())
                    .usuario(toUsuarioDTO(usuario))
                    .build();

        } catch (BadCredentialsException e) {
            handleFailedLogin(request.getEmail(), ipAddress);
            throw new AuthenticationException("Email ou senha incorretos");

        } catch (DisabledException e) {
            auditService.logLogin(request.getEmail(), ipAddress, "DISABLED");
            throw new AuthenticationException("Conta desativada. Entre em contato com o administrador.");

        } catch (AccountLockedException e) {
            throw e;

        } catch (AuthenticationException e) {
            throw e;

        } catch (Exception e) {
            log.error("Erro no login - Email: {}, IP: {}", request.getEmail(), ipAddress, e);
            throw new AuthenticationException("Erro ao realizar login. Tente novamente.");
        }
    }

    @Transactional
    public void logout(String token, HttpServletRequest request) {
        try {
            String email = jwtTokenProvider.getEmailFromToken(token);
            String sessionId = jwtTokenProvider.getSessionIdFromToken(token);
            String ipAddress = extractIpAddress(request);

            log.info("Logout solicitado - Email: {}, Session: {}", email, sessionId);

            jwtTokenProvider.invalidateToken(token);

            refreshTokenRepository.deleteByUsuarioEmail(email);

            activeSessions.remove(sessionId);

            auditService.logLogout(email, ipAddress, "SUCCESS");

            log.info("Logout realizado - Email: {}", email);

        } catch (Exception e) {
            log.error("Erro no logout", e);
        }
    }

    @Transactional
    public RefreshTokenResponseDTO refreshToken(RefreshTokenRequestDTO request, HttpServletRequest httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);

        log.debug("Renovacao de token solicitada - IP: {}", ipAddress);

        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("Refresh token invalido"));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new InvalidTokenException("Refresh token expirado");
        }

        if (refreshToken.isRevoked()) {
            throw new InvalidTokenException("Refresh token revogado");
        }

        Usuario usuario = refreshToken.getUsuario();
        if (!usuario.getAtivo() || !usuario.podeLogar()) {
            throw new AuthenticationException("Usuario nao autorizado");
        }

        String newAccessToken = jwtTokenProvider.generateToken(usuario);

        String newRefreshToken = null;
        if (shouldRotateRefreshToken(refreshToken)) {
            refreshToken.revogar();
            refreshTokenRepository.save(refreshToken);

            newRefreshToken = generateRefreshToken(usuario, ipAddress, httpRequest.getHeader("User-Agent"));
        }

        updateSession(usuario.getEmail(), newAccessToken);

        log.info("Token renovado - Usuario: {}", usuario.getEmail());

        return RefreshTokenResponseDTO.builder()
                .success(true)
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken != null ? newRefreshToken : request.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getTokenValidity())
                .build();
    }

    public TokenValidationResponseDTO validateToken(String token) {
        try {
            boolean isValid = jwtTokenProvider.validateToken(token);

            if (!isValid) {
                return TokenValidationResponseDTO.builder()
                        .valid(false)
                        .message("Token invalido")
                        .build();
            }

            String email = jwtTokenProvider.getEmailFromToken(token);
            Date expiration = jwtTokenProvider.getExpirationDateFromToken(token);

            if (jwtTokenProvider.isTokenBlacklisted(token)) {
                return TokenValidationResponseDTO.builder()
                        .valid(false)
                        .message("Token revogado")
                        .build();
            }

            Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
            if (usuario.isEmpty() || !usuario.get().getAtivo()) {
                return TokenValidationResponseDTO.builder()
                        .valid(false)
                        .message("Usuario invalido")
                        .build();
            }

            return TokenValidationResponseDTO.builder()
                    .valid(true)
                    .email(email)
                    .expiration(expiration)
                    .authorities(jwtTokenProvider.getAuthoritiesFromToken(token))
                    .message("Token valido")
                    .build();

        } catch (Exception e) {
            log.error("Erro ao validar token", e);
            return TokenValidationResponseDTO.builder()
                    .valid(false)
                    .message("Erro ao validar token")
                    .build();
        }
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequestDTO request) {
        String email = request.getEmail().trim().toLowerCase();

        log.info("Recuperacao de senha solicitada - Email: {}", email);

        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);

        if (usuario != null && usuario.getAtivo()) {
            String resetToken = generatePasswordResetToken();
            LocalDateTime expiry = LocalDateTime.now().plusHours(passwordResetTokenHours);

            usuario.setPasswordResetToken(resetToken);
            usuario.setPasswordResetExpiry(expiry);
            usuarioRepository.save(usuario);

            sendPasswordResetEmail(usuario, resetToken);

            auditService.logPasswordResetRequest(email, "SUCCESS");
        }

        log.info("Email de recuperacao processado - Email: {}", email);
    }

    @Transactional
    public void resetPassword(PasswordResetConfirmDTO request) {
        log.info("Reset de senha solicitado");

        Usuario usuario = usuarioRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new InvalidTokenException("Token invalido"));

        if (usuario.getPasswordResetExpiry().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Token expirado");
        }

        validatePasswordStrength(request.getNovaSenha());

        usuario.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        usuario.setPasswordResetToken(null);
        usuario.setPasswordResetExpiry(null);
        usuario.setDeveTrocarSenha(false);
        usuario.setUltimoResetSenha(LocalDateTime.now());

        invalidateAllUserSessions(usuario.getEmail());

        usuarioRepository.save(usuario);

        sendPasswordChangedEmail(usuario);

        auditService.logPasswordReset(usuario.getEmail(), "SUCCESS");

        log.info("Senha resetada com sucesso - Email: {}", usuario.getEmail());
    }

    @Transactional
    public void changePassword(ChangePasswordDTO request, String userEmail) {
        log.info("Alteracao de senha solicitada - Email: {}", userEmail);

        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AuthenticationException("Usuario nao encontrado"));

        if (!passwordEncoder.matches(request.getSenhaAtual(), usuario.getSenha())) {
            throw new AuthenticationException("Senha atual incorreta");
        }

        validatePasswordStrength(request.getNovaSenha());

        if (passwordEncoder.matches(request.getNovaSenha(), usuario.getSenha())) {
            throw new IllegalArgumentException("Nova senha nao pode ser igual a anterior");
        }

        usuario.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        usuario.setDeveTrocarSenha(false);
        usuario.setUltimoResetSenha(LocalDateTime.now());
        usuario.setSenhaExpiraEm(LocalDateTime.now().plusDays(90));

        usuarioRepository.save(usuario);

        sendPasswordChangedEmail(usuario);

        auditService.logPasswordChange(userEmail, "SUCCESS");

        log.info("Senha alterada com sucesso - Email: {}", userEmail);
    }

    public Usuario getUsuarioAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String email = authentication.getName();
        return usuarioRepository.findByEmail(email).orElse(null);
    }

    public Usuario getMockAdminUser() {
        return usuarioRepository.findByEmail("admin@tjba.jus.br")
                .orElseGet(() -> {
                    log.warn("Admin mock nao encontrado, retornando primeiro admin do sistema");
                    return usuarioRepository.findByTipo(br.jus.tjba.aclp.model.enums.TipoUsuario.ADMIN)
                            .stream()
                            .findFirst()
                            .orElse(null);
                });
    }

    public SessionInfoDTO getCurrentSessionInfo(String token) {
        try {
            String sessionId = jwtTokenProvider.getSessionIdFromToken(token);
            SessionInfo session = activeSessions.get(sessionId);

            if (session == null) {
                return null;
            }

            return SessionInfoDTO.builder()
                    .sessionId(session.getSessionId())
                    .userEmail(session.getUserEmail())
                    .ipAddress(session.getIpAddress())
                    .userAgent(session.getUserAgent())
                    .loginTime(session.getLoginTime())
                    .lastActivity(session.getLastActivity())
                    .expiresAt(session.getExpiresAt())
                    .build();

        } catch (Exception e) {
            log.error("Erro ao obter info da sessao", e);
            return null;
        }
    }

    public List<SessionInfoDTO> getUserSessions(String userEmail) {
        return activeSessions.values().stream()
                .filter(s -> s.getUserEmail().equals(userEmail))
                .map(s -> SessionInfoDTO.builder()
                        .sessionId(s.getSessionId())
                        .userEmail(s.getUserEmail())
                        .ipAddress(s.getIpAddress())
                        .userAgent(s.getUserAgent())
                        .loginTime(s.getLoginTime())
                        .lastActivity(s.getLastActivity())
                        .expiresAt(s.getExpiresAt())
                        .build())
                .toList();
    }

    @Transactional
    public void invalidateSession(String sessionId, String requestingUser) {
        SessionInfo session = activeSessions.get(sessionId);

        if (session != null) {
            Usuario usuario = getUsuarioAtual();
            if (!session.getUserEmail().equals(requestingUser) && !usuario.isAdmin()) {
                throw new AuthenticationException("Sem permissao para invalidar esta sessao");
            }

            activeSessions.remove(sessionId);

            refreshTokenRepository.deleteByUsuarioEmail(session.getUserEmail());

            log.info("Sessao invalidada - ID: {}, Usuario: {}", sessionId, session.getUserEmail());
        }
    }

    @Transactional
    public void invalidateAllUserSessions(String userEmail) {
        activeSessions.entrySet().removeIf(entry ->
                entry.getValue().getUserEmail().equals(userEmail));

        refreshTokenRepository.deleteByUsuarioEmail(userEmail);

        jwtTokenProvider.blacklistUserTokens(userEmail);

        log.info("Todas as sessoes invalidadas - Usuario: {}", userEmail);
    }

    private void checkRateLimiting(String ipAddress) {
        long recentAttempts = loginAttemptRepository.countRecentAttemptsByIp(
                ipAddress, LocalDateTime.now().minusMinutes(1));

        if (recentAttempts > 10) {
            throw new AuthenticationException("Muitas tentativas. Aguarde antes de tentar novamente.");
        }
    }

    private void checkAccountLocked(Usuario usuario) {
        if (usuario.getBloqueadoAte() != null &&
                usuario.getBloqueadoAte().isAfter(LocalDateTime.now())) {
            long minutosRestantes = java.time.Duration.between(
                    LocalDateTime.now(), usuario.getBloqueadoAte()).toMinutes();
            throw new AccountLockedException(
                    String.format("Conta bloqueada. Tente novamente em %d minutos.", minutosRestantes));
        }
    }

    private void handleFailedLogin(String email, String ipAddress) {
        LoginAttempt attempt = LoginAttempt.builder()
                .email(email)
                .ipAddress(ipAddress)
                .success(false)
                .attemptTime(LocalDateTime.now())
                .build();
        loginAttemptRepository.save(attempt);

        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            usuario.incrementarTentativasFalhadas();
            usuarioRepository.save(usuario);

            if (usuario.getBloqueadoAte() != null) {
                sendAccountLockedEmail(usuario);
            }
        });
    }

    private void registerSuccessfulLogin(Usuario usuario, String ipAddress, String userAgent) {
        LoginAttempt attempt = LoginAttempt.builder()
                .email(usuario.getEmail())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(true)
                .attemptTime(LocalDateTime.now())
                .build();
        loginAttemptRepository.save(attempt);
    }

    private String generateRefreshToken(Usuario usuario, String ipAddress, String userAgent) {
        String tokenValue = UUID.randomUUID().toString() + "-" + System.currentTimeMillis();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .usuario(usuario)
                .expiryDate(LocalDateTime.now().plusDays(refreshTokenValidityDays))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        refreshTokenRepository.save(refreshToken);

        return tokenValue;
    }

    private SessionInfo createSession(Usuario usuario, String token, String ipAddress, String userAgent) {
        String sessionId = UUID.randomUUID().toString();

        SessionInfo session = SessionInfo.builder()
                .sessionId(sessionId)
                .userEmail(usuario.getEmail())
                .userId(usuario.getId())
                .token(token)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .loginTime(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(sessionTimeoutMinutes))
                .build();

        activeSessions.put(sessionId, session);

        return session;
    }

    private void updateSession(String userEmail, String newToken) {
        activeSessions.values().stream()
                .filter(s -> s.getUserEmail().equals(userEmail))
                .forEach(s -> {
                    s.setToken(newToken);
                    s.setLastActivity(LocalDateTime.now());
                });
    }

    private void handleConcurrentSessions(Usuario usuario, boolean forceLogin) {
        List<SessionInfo> userSessions = activeSessions.values().stream()
                .filter(s -> s.getUserEmail().equals(usuario.getEmail()))
                .sorted(Comparator.comparing(SessionInfo::getLoginTime))
                .toList();

        log.info("Usuario {} possui {} sessoes ativas. Limite: {}",
                usuario.getEmail(), userSessions.size(), maxConcurrentSessions);

        if (userSessions.size() >= maxConcurrentSessions) {
            SessionInfo oldestSession = userSessions.get(0);

            log.info("Limite de sessoes atingido para {}. Removendo sessao mais antiga: {}",
                    usuario.getEmail(), oldestSession.getSessionId());

            activeSessions.remove(oldestSession.getSessionId());

            jwtTokenProvider.invalidateToken(oldestSession.getToken());
        }
    }

    private boolean shouldRotateRefreshToken(RefreshToken token) {
        return token.getCreatedAt().isBefore(LocalDateTime.now().minusDays(1));
    }

    // =====================================================================
    // FIX #3: MFA validateMfaCode agora rejeita tudo (não está implementado)
    // Antes retornava true sempre — qualquer código seria aceito.
    // Agora lança exceção explicando que MFA não está implementado.
    // Quando for implementar TOTP, substituir este método.
    // =====================================================================
    private boolean validateMfaCode(Usuario usuario, String code) {
        // MFA ainda não implementado — rejeitar para evitar bypass de segurança
        log.warn("Tentativa de validação MFA para usuario {} mas MFA não está implementado", usuario.getEmail());
        throw new UnsupportedOperationException(
                "Autenticação multifator (MFA) ainda não está implementada. " +
                        "Desative o MFA para este usuário ou aguarde a implementação.");
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Senha deve ter pelo menos 8 caracteres");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Senha deve conter pelo menos uma letra maiuscula");
        }

        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Senha deve conter pelo menos uma letra minuscula");
        }

        if (!password.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("Senha deve conter pelo menos um numero");
        }

        if (!password.matches(".*[@$!%*?&#].*")) {
            throw new IllegalArgumentException("Senha deve conter pelo menos um caractere especial");
        }
    }

    private String generatePasswordResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private UsuarioDTO toUsuarioDTO(Usuario usuario) {
        return UsuarioDTO.builder()
                .id(usuario.getId())
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .tipo(usuario.getTipo())
                .departamento(usuario.getDepartamento())
                .ultimoLogin(usuario.getUltimoLogin())
                .mfaEnabled(usuario.getMfaEnabled())
                .build();
    }

    private void sendPasswordResetEmail(Usuario usuario, String token) {
        String resetLink = String.format("%s/reset-password?token=%s", frontendUrl, token);

        String content = String.format("""
                Ola %s,
                
                Voce solicitou a recuperacao de senha para o Sistema ACLP.
                
                Clique no link abaixo para criar uma nova senha:
                %s
                
                Este link e valido por %d horas.
                
                Se voce nao solicitou esta recuperacao, ignore este email.
                
                Atenciosamente,
                Sistema ACLP - TJBA
                """, usuario.getNome(), resetLink, passwordResetTokenHours);

        emailService.enviarEmail(usuario.getEmail(), "Recuperacao de Senha - ACLP", content);
    }

    private void sendPasswordChangedEmail(Usuario usuario) {
        String content = String.format("""
                Ola %s,
                
                Sua senha do Sistema ACLP foi alterada com sucesso.
                
                Se voce nao realizou esta alteracao, entre em contato imediatamente com o suporte.
                
                Data/Hora: %s
                
                Atenciosamente,
                Sistema ACLP - TJBA
                """, usuario.getNome(), LocalDateTime.now());

        emailService.enviarEmail(usuario.getEmail(), "Senha Alterada - ACLP", content);
    }

    private void sendAccountLockedEmail(Usuario usuario) {
        String content = String.format("""
                Ola %s,
                
                Sua conta foi temporariamente bloqueada devido a multiplas tentativas de login falhadas.
                
                A conta sera desbloqueada automaticamente em %d minutos.
                
                Se nao foi voce, entre em contato com o suporte.
                
                Atenciosamente,
                Sistema ACLP - TJBA
                """, usuario.getNome(), lockoutDurationMinutes);

        emailService.enviarEmail(usuario.getEmail(), "Conta Bloqueada - ACLP", content);
    }

    @lombok.Data
    @lombok.Builder
    private static class SessionInfo {
        private String sessionId;
        private Long userId;
        private String userEmail;
        private String token;
        private String ipAddress;
        private String userAgent;
        private LocalDateTime loginTime;
        private LocalDateTime lastActivity;
        private LocalDateTime expiresAt;
    }
}
package br.jus.tjba.aclp.security;

import br.jus.tjba.aclp.model.Usuario;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.jsonwebtoken.Jwts.parser;

/**
 * Provedor de tokens JWT
 * Gerencia criação, validação e extração de informações dos tokens
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    // Blacklist de tokens invalidados (em produção usar Redis)
    private final Set<String> tokenBlacklist = ConcurrentHashMap.newKeySet();

    @Value("${aclp.jwt.secret:aclp-secret-key-deve-ter-pelo-menos-256-bits-para-hmac-sha256-funcionar-corretamente}")
    private String jwtSecret;

    @Value("${aclp.jwt.expiration-ms:3600000}") // 1 hora padrão
    private long jwtExpirationMs;

    @Value("${aclp.jwt.issuer:ACLP-TJBA}")
    private String jwtIssuer;

    public JwtTokenProvider() {
        // Gerar chave segura para HMAC-SHA256
        String secret = "aclp-secret-key-deve-ter-pelo-menos-256-bits-para-hmac-sha256-funcionar-corretamente";
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gera token JWT para usuário
     */
    public String generateToken(Usuario usuario) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        String sessionId = UUID.randomUUID().toString();

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", usuario.getId());
        claims.put("email", usuario.getEmail());
        claims.put("nome", usuario.getNome());
        claims.put("tipo", usuario.getTipo().name());
        claims.put("roles", getRoles(usuario));
        claims.put("sessionId", sessionId);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(usuario.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .setIssuer(jwtIssuer)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Gera token com Authentication
     */
    public String generateToken(Authentication authentication) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        String email = authentication.getName();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("roles", roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .setIssuer(jwtIssuer)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Valida token JWT
     */
    public boolean validateToken(String token) {
        try {
            // Verificar se está na blacklist
            if (tokenBlacklist.contains(token)) {
                log.warn("Token está na blacklist");
                return false;
            }

            parser()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);

            return true;

        } catch (SecurityException ex) {
            log.error("Token JWT inválido - assinatura incorreta");
        } catch (MalformedJwtException ex) {
            log.error("Token JWT malformado");
        } catch (ExpiredJwtException ex) {
            log.error("Token JWT expirado");
        } catch (UnsupportedJwtException ex) {
            log.error("Token JWT não suportado");
        } catch (IllegalArgumentException ex) {
            log.error("String JWT vazia");
        }

        return false;
    }

    /**
     * Extrai email do token
     */
    public String getEmailFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getSubject();
    }

    /**
     * Extrai ID do usuário do token
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("userId", Long.class);
    }

    /**
     * Extrai session ID do token
     */
    public String getSessionIdFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("sessionId", String.class);
    }

    /**
     * Extrai data de expiração do token
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getExpiration();
    }

    /**
     * Extrai authorities do token
     */
    public List<String> getAuthoritiesFromToken(String token) {
        Claims claims = getClaims(token);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        return roles != null ? roles : Collections.emptyList();
    }

    /**
     * Extrai todas as claims do token
     */
    public Claims getClaims(String token) {
        return parser()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Invalida token (adiciona à blacklist)
     */
    public void invalidateToken(String token) {
        tokenBlacklist.add(token);
        log.debug("Token adicionado à blacklist");
    }

    /**
     * Verifica se token está na blacklist
     */
    public boolean isTokenBlacklisted(String token) {
        return tokenBlacklist.contains(token);
    }

    /**
     * Adiciona todos os tokens de um usuário à blacklist
     */
    public void blacklistUserTokens(String userEmail) {
        // Em produção, implementar com Redis e padrão de chave
        log.info("Invalidando todos os tokens do usuário: {}", userEmail);
    }

    /**
     * Limpa tokens expirados da blacklist (executar periodicamente)
     */
    public void cleanExpiredTokens() {
        tokenBlacklist.removeIf(token -> {
            try {
                Date expiration = getExpirationDateFromToken(token);
                return expiration.before(new Date());
            } catch (Exception e) {
                return true; // Remove tokens inválidos
            }
        });

        log.debug("Limpeza de tokens expirados concluída. Tamanho da blacklist: {}", tokenBlacklist.size());
    }

    /**
     * Retorna tempo de validade do token em segundos
     */
    public long getTokenValidity() {
        return jwtExpirationMs / 1000;
    }

    /**
     * Verifica se token vai expirar em breve
     */
    public boolean isTokenExpiringSoon(String token, long minutesThreshold) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            long minutesUntilExpiry = (expiration.getTime() - System.currentTimeMillis()) / (60 * 1000);
            return minutesUntilExpiry <= minutesThreshold;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Extrai roles do usuário
     */
    private List<String> getRoles(Usuario usuario) {
        List<String> roles = new ArrayList<>();
        roles.add("ROLE_" + usuario.getTipo().name());

        if (usuario.isAdmin()) {
            roles.add("ROLE_ADMIN");
        } else {
            roles.add("ROLE_USER");
        }

        return roles;
    }

    /**
     * Cria authorities do Spring Security a partir do token
     */
    public List<GrantedAuthority> getAuthorities(String token) {
        List<String> roles = getAuthoritiesFromToken(token);
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
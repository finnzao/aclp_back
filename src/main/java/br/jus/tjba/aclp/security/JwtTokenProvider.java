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
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final TokenBlacklistService blacklistService;

    @Value("${aclp.jwt.expiration-ms:3600000}")
    private long jwtExpirationMs;

    @Value("${aclp.jwt.issuer:ACLP-TJBA}")
    private String jwtIssuer;

    public JwtTokenProvider(
            @Value("${aclp.jwt.secret:my-super-secret-jwt-key-with-at-least-256-bits-for-hmac-sha-algorithm-security}")
            String secret,
            TokenBlacklistService blacklistService) {

        if (secret.length() < 32) {
            throw new IllegalStateException(
                    String.format("JWT secret tem apenas %d caracteres. Mínimo: 32", secret.length())
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.blacklistService = blacklistService;
        log.info("JwtTokenProvider inicializado com chave de {} caracteres", secret.length());
    }

    public String generateToken(Usuario usuario) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", usuario.getId());
        claims.put("email", usuario.getEmail());
        claims.put("nome", usuario.getNome());
        claims.put("tipo", usuario.getTipo().name());
        claims.put("roles", getRoles(usuario));
        claims.put("sessionId", UUID.randomUUID().toString());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(usuario.getEmail())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .setIssuer(jwtIssuer)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(Authentication authentication) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", authentication.getName());
        claims.put("roles", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(authentication.getName())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .setIssuer(jwtIssuer)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            if (blacklistService.isBlacklisted(token)) {
                return false;
            }
            Jwts.parser()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception ex) {
            log.error("Token inválido: {}", ex.getMessage());
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Object userId = getClaims(token).get("userId");
        return userId instanceof Integer ? ((Integer) userId).longValue() : (Long) userId;
    }

    public String getNomeFromToken(String token) {
        return getClaims(token).get("nome", String.class);
    }

    public String getTipoFromToken(String token) {
        return getClaims(token).get("tipo", String.class);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaims(token).getExpiration();
    }

    public Date getIssuedAtFromToken(String token) {
        return getClaims(token).getIssuedAt();
    }

    public List<String> getAuthoritiesFromToken(String token) {
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) getClaims(token).get("roles");
        return roles != null ? roles : Collections.emptyList();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public void invalidateToken(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            blacklistService.blacklist(token, expiration.getTime());
        } catch (ExpiredJwtException e) {
            // Token já expirado, não precisa adicionar à blacklist
        } catch (Exception e) {
            log.error("Erro ao invalidar token: {}", e.getMessage());
        }
    }

    public boolean isTokenBlacklisted(String token) {
        return blacklistService.isBlacklisted(token);
    }

    public long getTokenValidity() {
        return jwtExpirationMs / 1000;
    }

    public long getTokenRemainingTime(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return (expiration.getTime() - System.currentTimeMillis()) / (60 * 1000);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean validateTokenForUser(String token, String username) {
        try {
            return username.equals(getUsernameFromToken(token)) && validateToken(token);
        } catch (Exception e) {
            return false;
        }
    }

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

    public List<GrantedAuthority> getAuthorities(String token) {
        return getAuthoritiesFromToken(token).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    public String getSessionIdFromToken(String token) {
        return getClaims(token).get("sessionId", String.class);
    }

    public void blacklistUserTokens(String userEmail) {
        log.info("Invalidando todos os tokens do usuário: {}", userEmail);
        // TODO: Implementar com Redis usando padrão de chave user:email:*
    }

    public boolean isTokenExpiringSoon(String token, long minutesThreshold) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            long minutesUntilExpiry = (expiration.getTime() - System.currentTimeMillis()) / (60 * 1000);
            return minutesUntilExpiry <= minutesThreshold;
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isTokenFresh(String token, long minutesThreshold) {
        try {
            Date issuedAt = getIssuedAtFromToken(token);
            long minutesSinceIssued = (System.currentTimeMillis() - issuedAt.getTime()) / (60 * 1000);
            return minutesSinceIssued <= minutesThreshold;
        } catch (Exception e) {
            return false;
        }
    }

    public String getTokenInfo(String token) {
        try {
            Claims claims = getClaims(token);
            return String.format(
                    "Token Info: Subject=%s, Issued=%s, Expires=%s, Issuer=%s",
                    claims.getSubject(),
                    claims.getIssuedAt(),
                    claims.getExpiration(),
                    claims.getIssuer()
            );
        } catch (Exception e) {
            return "Token inválido ou malformado";
        }
    }
}
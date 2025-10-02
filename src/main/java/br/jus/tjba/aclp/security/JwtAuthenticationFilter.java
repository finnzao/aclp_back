package br.jus.tjba.aclp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de autenticacao JWT
 * Processa o token JWT em cada requisicao e configura o contexto de seguranca
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (jwt != null && jwtTokenProvider.validateToken(jwt)) {
                String username = jwtTokenProvider.getUsernameFromToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Autenticacao JWT processada: {}", username);
            }

        } catch (Exception e) {
            log.error("Erro ao processar JWT: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extrai o token JWT do header Authorization
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }

    /**
     * Define quais rotas NAO devem passar por este filtro
     * Apenas rotas completamente publicas que nao precisam de JWT
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.equals("/api/auth/login") ||
                path.equals("/api/auth/refresh") ||
                path.equals("/api/auth/forgot-password") ||
                path.equals("/api/auth/reset-password") ||
                path.equals("/api/auth/health") ||
                path.equals("/api/auth/check-setup") ||
                path.equals("/api/auth/validate") ||
                path.startsWith("/api/setup/") ||
                path.startsWith("/api/demo/") ||
                path.startsWith("/api/verificacao/") ||
                path.startsWith("/api/usuarios/convites/validar/") ||
                path.equals("/api/usuarios/convites/ativar") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/h2-console") ||
                path.startsWith("/actuator/health") ||
                path.equals("/");
    }
}
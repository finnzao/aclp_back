package br.jus.tjba.aclp.security;

import br.jus.tjba.aclp.util.ClientIpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting por IP nas rotas de autenticação (força bruta / enumeração / DoS de e-mail).
 *
 * Responde 429 com Retry-After. Roda ANTES do JwtAuthenticationFilter para que a
 * requisição barrada não consuma banco nem CPU de validação de token.
 *
 * Antes desta classe o limite vivia em AuthService.checkRateLimiting(), que:
 *   - só cobria /login (forgot-password e reset-password ficavam abertos);
 *   - lançava AuthenticationException, virando 401 no controller — nunca 429;
 *   - fazia um COUNT em login_attempts a cada tentativa de login.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** Rotas sensíveis: credenciais ou envio de e-mail disparado por anônimo. */
    private static final Set<String> ROTAS_PROTEGIDAS = Set.of(
            "/api/auth/login",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/email-verification/solicitar-codigo",
            "/api/email-verification/reenviar-codigo"
    );

    // ponytail: janela em memória — o limite é por instância. Com múltiplas réplicas
    // o teto efetivo é (limite × réplicas); migrar para o Redis já configurado se escalar.
    private final Map<String, Deque<Instant>> tentativasPorIp = new ConcurrentHashMap<>();

    @Value("${aclp.security.rate-limit.max-requests:10}")
    private int maxRequisicoes;

    @Value("${aclp.security.rate-limit.window-seconds:60}")
    private long janelaSegundos;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ROTAS_PROTEGIDAS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String ip = ClientIpUtil.extrair(request);

        if (excedeuLimite(ip)) {
            log.warn("Rate limit excedido - IP: {}, Rota: {}", ip, request.getRequestURI());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(janelaSegundos));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Muitas tentativas. Aguarde antes de tentar novamente.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Janela deslizante: descarta os registros fora da janela e conta o que sobrou.
     * Sincronizado no deque porque duas requisições do mesmo IP podem chegar em paralelo.
     */
    private boolean excedeuLimite(String ip) {
        Instant agora = Instant.now();
        Instant limiteJanela = agora.minusSeconds(janelaSegundos);

        Deque<Instant> registros = tentativasPorIp.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (registros) {
            while (!registros.isEmpty() && registros.peekFirst().isBefore(limiteJanela)) {
                registros.pollFirst();
            }

            if (registros.size() >= maxRequisicoes) {
                return true;
            }

            registros.addLast(agora);
            return false;
        }
    }

    /**
     * Remove IPs ociosos. Sem isto o mapa cresceria sem limite sob ataque distribuído,
     * virando o próprio vetor de DoS (exaustão de heap).
     */
    public void limparIpsOciosos() {
        Instant limiteJanela = Instant.now().minusSeconds(janelaSegundos);

        tentativasPorIp.entrySet().removeIf(entry -> {
            Deque<Instant> registros = entry.getValue();
            synchronized (registros) {
                return registros.isEmpty() || registros.peekLast().isBefore(limiteJanela);
            }
        });
    }

    /** Usado pelo agendador de limpeza para logar o tamanho do mapa. */
    public int getIpsMonitorados() {
        return tentativasPorIp.size();
    }
}

package br.jus.tjba.aclp.config;

import br.jus.tjba.aclp.service.SetupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Interceptor que força o setup quando necessário
 * Bloqueia TODAS as rotas exceto as do setup quando o sistema não foi configurado
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SetupInterceptor implements HandlerInterceptor {

    private final SetupService setupService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        log.debug("Setup Interceptor - URI: {}, Method: {}", requestURI, method);

        // Verificar se setup é necessário
        boolean setupRequired = setupService.isSetupRequired();

        if (!setupRequired) {
            // Setup já foi concluído, permitir acesso normal
            log.debug("Setup concluído, permitindo acesso normal");
            return true;
        }

        // Setup é necessário - verificar se está acessando rotas permitidas
        if (isSetupRoute(requestURI)) {
            log.debug("Acesso permitido à rota de setup: {}", requestURI);
            return true;
        }

        if (isPublicRoute(requestURI)) {
            log.debug("Acesso permitido à rota pública: {}", requestURI);
            return true;
        }

        // Qualquer outra rota é bloqueada - redirecionar para setup
        log.info("Setup requerido - bloqueando acesso a {} e redirecionando", requestURI);
        handleSetupRedirect(request, response);
        return false;
    }

    /**
     * Verifica se é uma rota relacionada ao setup
     */
    private boolean isSetupRoute(String uri) {
        return uri.startsWith("/api/setup/") ||
                uri.equals("/setup") ||
                uri.startsWith("/setup/") ||
                uri.equals("/setup.html") ||
                uri.startsWith("/setup");
    }

    /**
     * Verifica se é uma rota pública que deve sempre estar disponível
     */
    private boolean isPublicRoute(String uri) {
        return uri.startsWith("/css/") ||
                uri.startsWith("/js/") ||
                uri.startsWith("/images/") ||
                uri.startsWith("/static/") ||
                uri.startsWith("/webjars/") ||
                uri.equals("/favicon.ico") ||
                uri.equals("/") ||
                uri.equals("/index.html") ||
                uri.startsWith("/actuator/health") ||
                uri.equals("/error");
    }

    /**
     * Trata o redirecionamento para o setup
     */
    private void handleSetupRedirect(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String requestURI = request.getRequestURI();

        // Se for uma requisição AJAX/API, retornar JSON
        if (isApiRequest(request)) {
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED); // 428
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            String jsonResponse = """
                {
                    "status": "setup_required",
                    "message": "Sistema requer configuração inicial",
                    "setupUrl": "/setup",
                    "code": "SETUP_REQUIRED"
                }
                """;

            response.getWriter().write(jsonResponse);
            log.info("Retornando JSON de setup requerido para requisição API: {}", requestURI);
            return;
        }

        // Para requisições normais, redirecionar para página de setup
        String setupUrl = "/setup";
        log.info("Redirecionando {} para setup: {}", requestURI, setupUrl);
        response.sendRedirect(setupUrl);
    }

    /**
     * Verifica se é uma requisição AJAX/API
     */
    private boolean isApiRequest(HttpServletRequest request) {
        String requestedWith = request.getHeader("X-Requested-With");
        String contentType = request.getHeader("Content-Type");
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();

        return "XMLHttpRequest".equals(requestedWith) ||
                (contentType != null && contentType.contains("application/json")) ||
                (accept != null && accept.contains("application/json")) ||
                uri.startsWith("/api/");
    }
}
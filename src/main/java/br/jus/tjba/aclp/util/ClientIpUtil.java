package br.jus.tjba.aclp.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extração do IP real do cliente atrás do nginx.
 */
public final class ClientIpUtil {

    private ClientIpUtil() {}

    public static String extrair(HttpServletRequest request) {
        if (request == null) return "unknown";

        // nginx.conf faz `proxy_set_header X-Real-IP $remote_addr` — valor definido
        // pelo proxy, não repassado do cliente.
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        // Sem X-Real-IP: último elemento do XFF é o salto anexado pelo proxy.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] saltos = forwarded.split(",");
            String ultimo = saltos[saltos.length - 1].trim();
            if (!ultimo.isEmpty()) {
                return ultimo;
            }
        }

        return request.getRemoteAddr();
    }
}

package br.jus.tjba.aclp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${aclp.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${aclp.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    private final Environment environment;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> patterns = new ArrayList<>();

        // Origens das variáveis de ambiente (sempre incluídas)
        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                .forEach(patterns::add);

        // Adicionar frontend URL se diferente
        if (!patterns.contains(frontendUrl)) {
            patterns.add(frontendUrl);
        }

        // localhost apenas em dev/default (não em prod)
        if (isDevProfile()) {
            patterns.add("http://localhost:*");
            patterns.add("http://127.0.0.1:*");
        }

        configuration.setAllowedOriginPatterns(patterns);

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
        ));

        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "Accept", "Origin",
                "Access-Control-Request-Method", "Access-Control-Request-Headers",
                "X-Requested-With", "X-CSRF-TOKEN"
        ));

        configuration.setExposedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Total-Count",
                "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public CorsFilter corsFilter() {
        return new CorsFilter(corsConfigurationSource());
    }

    private boolean isDevProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return true; // Sem profile = dev
        }
        return Arrays.stream(activeProfiles)
                .anyMatch(p -> p.equals("dev") || p.equals("default"));
    }
}
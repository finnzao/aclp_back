package br.jus.tjba.aclp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.Collections;

/**
 * Configuração CORS PERMISSIVA para desenvolvimento
 */
@Configuration
@Profile({"dev", "test"})
@Slf4j
public class CorsConfigDev {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter() {
        log.info("🌐 Configurando CORS PERMISSIVO - Modo Desenvolvimento");

        CorsConfiguration config = new CorsConfiguration();

        // Permitir TODAS as origens
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        config.setAllowCredentials(true);

        // Permitir TODOS os métodos
        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));

        // Permitir TODOS os headers
        config.setAllowedHeaders(Arrays.asList("*"));

        // Expor headers
        config.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Disposition",
                "Content-Type"
        ));

        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        log.info("✅ CORS configurado (modo dev):");
        log.info("   - Origins: * (TODAS)");
        log.info("   - Methods: * (TODOS)");
        log.info("   - Headers: * (TODOS)");

        return new CorsFilter(source);
    }
}
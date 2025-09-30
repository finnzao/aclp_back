package br.jus.tjba.aclp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;

/**
 * Configuração de Segurança para DESENVOLVIMENTO
 * TODAS AS ROTAS SÃO PÚBLICAS - SEM AUTENTICAÇÃO
 *
 * ⚠️ ATIVE APENAS EM DESENVOLVIMENTO/TESTES ⚠️
 * ⛔ NUNCA USE EM PRODUÇÃO ⛔
 */
@Configuration
@EnableWebSecurity
@Profile({"dev", "test"})
@Slf4j
public class SecurityConfigDev {

    @PostConstruct
    public void init() {
        log.warn("╔════════════════════════════════════════════════════════════════╗");
        log.warn("║                                                                ║");
        log.warn("║  ⚠️  MODO DESENVOLVIMENTO ATIVO ⚠️                            ║");
        log.warn("║                                                                ║");
        log.warn("║  🔓 TODAS AS ROTAS ESTÃO PÚBLICAS                             ║");
        log.warn("║  🔓 CSRF DESABILITADO                                         ║");
        log.warn("║  🔓 CORS PERMISSIVO                                           ║");
        log.warn("║  🔓 SEM AUTENTICAÇÃO JWT                                      ║");
        log.warn("║                                                                ║");
        log.warn("║  ⛔ NUNCA USE ESTA CONFIGURAÇÃO EM PRODUÇÃO ⛔               ║");
        log.warn("║                                                                ║");
        log.warn("╚════════════════════════════════════════════════════════════════╝");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("🔓 Configurando Security Filter Chain - MODO DESENVOLVIMENTO");

        http
                // Desabilitar CSRF
                .csrf(csrf -> csrf.disable())

                // Configurar CORS permissivo
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // PERMITIR TODAS AS REQUISIÇÕES
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/**").permitAll()
                        .anyRequest().permitAll()
                )

                // Stateless
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Desabilitar form login
                .formLogin(form -> form.disable())

                // Desabilitar http basic
                .httpBasic(basic -> basic.disable())

                // Desabilitar logout
                .logout(logout -> logout.disable())

                // Desabilitar frames protection (para H2)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.disable())
                );

        log.info("✅ Security configurado (dev):");
        log.info("   - CSRF: DESABILITADO");
        log.info("   - CORS: PERMISSIVO");
        log.info("   - Autenticação: NÃO REQUERIDA");
        log.info("   - Todas as rotas: PÚBLICAS");

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        log.info("🌐 Configurando CORS PERMISSIVO");

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Disposition",
                "Content-Type"
        ));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        log.info("📦 PasswordEncoder (BCrypt) criado para desenvolvimento");
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        log.info("🔑 AuthenticationManager criado para desenvolvimento");
        return config.getAuthenticationManager();
    }
}
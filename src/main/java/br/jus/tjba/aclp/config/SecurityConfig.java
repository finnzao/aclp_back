package br.jus.tjba.aclp.config;

import br.jus.tjba.aclp.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuração de Segurança Web
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Desabilitar CSRF (API REST stateless)
                .csrf(AbstractHttpConfigurer::disable)

                // Configurar CORS (usa CorsConfig.java)
                .cors(cors -> {})

                // Sessão stateless (JWT)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Configurar autorização de requisições
                .authorizeHttpRequests(authz -> authz
                        // ==================== OPTIONS - PERMITIR TUDO (CORS PREFLIGHT) ====================
                        // Requisições OPTIONS devem passar sem autenticação para CORS funcionar
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ==================== ENDPOINTS PÚBLICOS ====================

                        // Autenticação (sem token)
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/forgot-password").permitAll()
                        .requestMatchers("/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/auth/health").permitAll()
                        .requestMatchers("/api/auth/check-setup").permitAll()
                        .requestMatchers("/api/auth/validate").permitAll()

                        // ==================== ENDPOINTS DE PERFIL (AUTENTICADOS) ====================

                        // Qualquer usuário autenticado pode acessar seu perfil
                        .requestMatchers("/api/auth/perfil").authenticated()
                        .requestMatchers("/api/auth/perfil/**").authenticated()

                        // ==================== SETUP E DEMO ====================

                        .requestMatchers("/api/setup/**").permitAll()
                        .requestMatchers("/api/demo/**").permitAll()
                        .requestMatchers("/api/usuarios/convites/validar/**").permitAll()
                        .requestMatchers("/api/usuarios/convites/ativar").permitAll()

                        // ==================== DOCUMENTAÇÃO ====================

                        // Swagger UI
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .requestMatchers("/swagger-resources/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()

                        // ==================== DESENVOLVIMENTO ====================

                        // H2 Console (apenas desenvolvimento)
                        .requestMatchers("/h2-console/**").permitAll()

                        // Health checks (Actuator)
                        .requestMatchers("/actuator/health/**").permitAll()

                        // ==================== FRONTEND ====================

                        .requestMatchers("/", "/index.html", "/setup/**").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/static/**").permitAll()

                        // ==================== ROTAS ADMINISTRATIVAS ====================

                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/api/usuarios/convites").hasRole("ADMIN")

                        // ==================== QUALQUER OUTRA REQUISIÇÃO ====================

                        // Qualquer outra requisição precisa autenticação
                        .anyRequest().authenticated()
                )

                // Configurar provider de autenticação
                .authenticationProvider(authenticationProvider())

                // Adicionar filtro JWT antes do filtro de autenticação padrão
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Configuração de headers de segurança
                .headers(headers -> headers
                        // Permitir frames da mesma origem (para H2 Console em dev)
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        // Configurações de segurança adicionais
                        .contentTypeOptions(contentType -> {})
                        .xssProtection(xss -> {})
                        .cacheControl(cache -> {})
                );

        return http.build();
    }

    /**
     * Bean do provider de autenticação
     * Usa UserDetailsService e PasswordEncoder
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    /**
     * Bean do AuthenticationManager
     * Usado para autenticação programática (login)
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
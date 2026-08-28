package br.jus.tjba.aclp.config;

import br.jus.tjba.aclp.security.JwtAuthenticationFilter;
import br.jus.tjba.aclp.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF ativo. A autenticação vive em cookie httpOnly, e cookie o browser
                // envia sozinho em requisição cross-site — SameSite=Lax barra o grosso,
                // mas é uma única linha de defesa e depende do browser respeitá-la.
                //
                // Padrão double-submit: o token vai num cookie SEM httpOnly (o JS precisa
                // lê-lo) e volta no header X-XSRF-TOKEN. Um site atacante consegue disparar
                // a requisição, mas não consegue LER o cookie de outro domínio para copiar
                // o valor no header — é isso que o distingue de uma requisição legítima.
                //
                // /api/auth/login e /refresh ficam de fora: são anônimos e não há sessão a
                // proteger ainda; exigir token ali só quebraria o primeiro acesso.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                        .ignoringRequestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/setup/**",
                                "/api/usuarios/convites/validar/**",
                                "/api/usuarios/convites/ativar",
                                "/api/email-verification/**"
                        )
                )
                .cors(cors -> {})
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Autenticação (público)
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/forgot-password").permitAll()
                        .requestMatchers("/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/auth/health").permitAll()
                        .requestMatchers("/api/auth/check-setup").permitAll()
                        .requestMatchers("/api/auth/validate").permitAll()

                        // Perfil (autenticado)
                        .requestMatchers("/api/auth/perfil").authenticated()
                        .requestMatchers("/api/auth/perfil/**").authenticated()

                        // Setup (público, uso único)
                        .requestMatchers("/api/setup/status").permitAll()
                        .requestMatchers("/api/setup/admin").permitAll()
                        .requestMatchers("/api/setup/health").permitAll()
                        .requestMatchers("/api/setup/audit").permitAll()

                        // Convites (público apenas validar/ativar)
                        .requestMatchers("/api/usuarios/convites/validar/**").permitAll()
                        .requestMatchers("/api/usuarios/convites/ativar").permitAll()

                        // Documentação
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .requestMatchers("/swagger-resources/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()

                        // Health checks
                        .requestMatchers("/actuator/health/**").permitAll()

                        // Frontend estático
                        .requestMatchers("/", "/index.html", "/setup/**").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/static/**").permitAll()

                        // Administrativo
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/api/usuarios/convites").hasRole("ADMIN")

                        // Tudo mais: autenticado (inclui novos endpoints de performance)
                        // GET  /api/custodiados?page=&size=   (paginação server-side)
                        // GET  /api/custodiados/exportar       (exportação com filtros)
                        // POST /api/processos/batch            (busca em lote)
                        // GET  /api/comparecimentos/todos      (com filtros server-side)
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                // Rate limit primeiro: requisição barrada não chega a validar token.
                // Os dois ancoram no mesmo filtro padrão — a ordem entre eles é a ordem
                // destas chamadas (JwtAuthenticationFilter é custom e não tem ordem registrada,
                // então não serve de âncora).
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(contentType -> {})
                        .xssProtection(xss -> {})
                        .cacheControl(cache -> {})
                        // A API só devolve JSON: nada de script, frame ou form pode ser
                        // carregado a partir de uma resposta dela.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        // HSTS: só tem efeito sobre HTTPS; em HTTP o browser ignora.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                );

        return http.build();
    }

    @Bean
    @Order(1)
    @Profile("dev")
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/h2-console/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                );
        return http.build();
    }

    /**
     * Todo bean do tipo Filter é auto-registrado pelo Boot no chain do servlet — além
     * de já estar no chain do Spring Security. Sem desligar isso, cada filtro roda DUAS
     * vezes por requisição: o JWT era decodificado e validado em dobro (CPU desperdiçada
     * em toda chamada autenticada) e o rate limit contaria 2 por requisição, cortando o
     * teto pela metade.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> desabilitarRegistroJwt(
            JwtAuthenticationFilter filtro) {
        FilterRegistrationBean<JwtAuthenticationFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> desabilitarRegistroRateLimit(
            RateLimitFilter filtro) {
        FilterRegistrationBean<RateLimitFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    /**
     * No Spring Security 6 o token CSRF é resolvido de forma preguiçosa: se ninguém o
     * lê durante a requisição, o cookie XSRF-TOKEN nunca é escrito — e o frontend não
     * teria o que mandar de volta no header, quebrando todo POST/PUT/DELETE.
     * `setCsrfRequestAttributeName(null)` desliga esse adiamento e faz o token ser
     * resolvido (e o cookie emitido) em toda requisição.
     */
    private CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

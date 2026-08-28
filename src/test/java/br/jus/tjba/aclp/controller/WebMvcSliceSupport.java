package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.security.JwtTokenProvider;
import br.jus.tjba.aclp.service.SetupService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Base dos testes @WebMvcTest.
 *
 * O slice @WebMvcTest não carrega só o controller: ele inclui, por definição, os
 * beans de Filter, WebMvcConfigurer e HandlerInterceptor da aplicação. Neste projeto
 * isso arrasta uma cadeia inteira de colaboradores:
 *
 *   JwtAuthenticationFilter (@Component Filter) → JwtTokenProvider + UserDetailsService
 *   WebConfig (WebMvcConfigurer) → SetupInterceptor → SetupService
 *
 * Sem esses mocks o contexto do slice nem chega a subir, e todos os testes da classe
 * falham antes de executar uma linha. Centralizado aqui para que uma dependência nova
 * na cadeia seja resolvida em um lugar só, e não nos quatro testes de controller.
 *
 * RateLimitFilter também entra no slice, mas só depende de @Value — não precisa de mock.
 */
public abstract class WebMvcSliceSupport {

    @MockBean
    protected SetupService setupService;

    @MockBean
    protected JwtTokenProvider jwtTokenProvider;

    @MockBean
    protected UserDetailsService userDetailsService;
}

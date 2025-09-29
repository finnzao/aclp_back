package br.jus.tjba.aclp.security;

import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implementação customizada do UserDetailsService do Spring Security
 * Carrega usuários do banco de dados e converte para UserDetails
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Carregando usuário: {}", email);

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Usuário não encontrado: {}", email);
                    return new UsernameNotFoundException("Usuário não encontrado: " + email);
                });

        log.debug("Usuário encontrado: {} - Ativo: {} - Tipo: {}",
                usuario.getEmail(), usuario.getAtivo(), usuario.getTipo());

        return User.builder()
                .username(usuario.getEmail())
                .password(usuario.getSenha())
                .disabled(!usuario.getAtivo())
                .accountExpired(false)
                .accountLocked(!usuario.podeLogar())
                .credentialsExpired(usuario.senhaExpirada())
                .authorities(getAuthorities(usuario))
                .build();
    }

    /**
     * Retorna as authorities (permissões) do usuário
     */
    private Collection<? extends GrantedAuthority> getAuthorities(Usuario usuario) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // Adicionar role baseada no tipo de usuário
        authorities.add(new SimpleGrantedAuthority("ROLE_" + usuario.getTipo().name()));

        // Adicionar role simplificada
        if (usuario.isAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        log.debug("Authorities para {}: {}", usuario.getEmail(), authorities);

        return authorities;
    }

    /**
     * Carrega usuário por ID (método auxiliar)
     */
    public Usuario loadUserById(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado com ID: " + id));
    }
}
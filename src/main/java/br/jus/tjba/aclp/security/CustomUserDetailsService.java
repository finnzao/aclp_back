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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Carregando usuário: {}", email);

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado com email: " + email));

        if (!usuario.getAtivo()) {
            log.warn("Tentativa de login com conta desativada: {}", email);
            throw new UsernameNotFoundException("Conta desativada");
        }

        Collection<? extends GrantedAuthority> authorities = getAuthorities(usuario);

        log.debug("Usuário carregado: {}, Authorities: {}", email, authorities);

        return User.builder()
                .username(usuario.getEmail())
                .password(usuario.getSenha())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(usuario.getBloqueadoAte() != null &&
                        usuario.getBloqueadoAte().isAfter(java.time.LocalDateTime.now()))
                .credentialsExpired(usuario.senhaExpirada())
                .disabled(!usuario.getAtivo())
                .build();
    }

    private Collection<? extends GrantedAuthority> getAuthorities(Usuario usuario) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        String roleName = "ROLE_" + usuario.getTipo().name();
        authorities.add(new SimpleGrantedAuthority(roleName));

        if (usuario.isAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        log.debug("Authorities geradas para {}: {}", usuario.getEmail(), authorities);

        return authorities;
    }
}
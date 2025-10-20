package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.AlterarSenhaDTO;
import br.jus.tjba.aclp.dto.AtualizarPerfilDTO;
import br.jus.tjba.aclp.dto.AtualizarUsuarioDTO;
import br.jus.tjba.aclp.dto.UsuarioDTO;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<Usuario> findAll() {
        return usuarioRepository.findByAtivoTrue();
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> findById(Long id) {
        return usuarioRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> findByEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    @Transactional
    public Usuario save(UsuarioDTO dto) {
        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email já está em uso");
        }

        Usuario usuario = Usuario.builder()
                .nome(dto.getNome())
                .email(dto.getEmail())
                .senha(passwordEncoder.encode(dto.getSenha()))
                .tipo(dto.getTipo() != null ? dto.getTipo() : TipoUsuario.USUARIO)
                .departamento(dto.getDepartamento())
                .comarca(dto.getComarca())
                .cargo(dto.getCargo())
                .ativo(true)
                .build();

        return usuarioRepository.save(usuario);
    }

    /**
     * Atualização administrativa de usuário (PARCIAL)
     * Apenas campos não nulos são atualizados
     */
    @Transactional
    public Usuario update(Long id, AtualizarUsuarioDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        // Atualiza apenas campos não nulos
        if (dto.getNome() != null) {
            usuario.setNome(dto.getNome());
        }

        if (dto.getEmail() != null) {
            if (!usuario.getEmail().equals(dto.getEmail()) &&
                    usuarioRepository.existsByEmail(dto.getEmail())) {
                throw new IllegalArgumentException("Email já está em uso");
            }
            usuario.setEmail(dto.getEmail());
        }

        if (dto.getSenha() != null && !dto.getSenha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(dto.getSenha()));
        }

        if (dto.getTipo() != null) {
            usuario.setTipo(dto.getTipo());
        }

        if (dto.getDepartamento() != null) {
            usuario.setDepartamento(dto.getDepartamento());
        }

        if (dto.getComarca() != null) {
            usuario.setComarca(dto.getComarca());
        }

        if (dto.getCargo() != null) {
            usuario.setCargo(dto.getCargo());
        }

        if (dto.getAtivo() != null) {
            usuario.setAtivo(dto.getAtivo());
        }

        if (dto.getAvatar() != null) {
            usuario.setAvatar(dto.getAvatar());
        }

        return usuarioRepository.save(usuario);
    }

    /**
     * Atualiza perfil do próprio usuário (sem permissões admin)
     */
    @Transactional
    public Usuario atualizarPerfil(String email, AtualizarPerfilDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        usuario.setNome(dto.getNome());

        if (dto.getDepartamento() != null) {
            usuario.setDepartamento(dto.getDepartamento());
        }

        if (dto.getComarca() != null) {
            usuario.setComarca(dto.getComarca());
        }

        if (dto.getCargo() != null) {
            usuario.setCargo(dto.getCargo());
        }

        if (dto.getAvatar() != null) {
            usuario.setAvatar(dto.getAvatar());
        }

        log.info("Perfil atualizado para usuário: {}", email);
        return usuarioRepository.save(usuario);
    }

    /**
     * Altera senha do próprio usuário
     */
    @Transactional
    public void alterarSenha(String email, AlterarSenhaDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        // Valida senha atual
        if (!passwordEncoder.matches(dto.getSenhaAtual(), usuario.getSenha())) {
            throw new IllegalArgumentException("Senha atual incorreta");
        }

        // Valida confirmação
        if (!dto.getNovaSenha().equals(dto.getConfirmarSenha())) {
            throw new IllegalArgumentException("Nova senha e confirmação não conferem");
        }

        // Atualiza senha
        usuario.setSenha(passwordEncoder.encode(dto.getNovaSenha()));
        usuario.setDeveTrocarSenha(false);

        log.info("Senha alterada para usuário: {}", email);
        usuarioRepository.save(usuario);
    }

    /**
     * Desativa própria conta (soft delete)
     */
    @Transactional
    public void desativarPropriaConta(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (usuario.isAdmin()) {
            long adminCount = usuarioRepository.countByTipo(TipoUsuario.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalArgumentException(
                        "Não é possível desativar o último administrador do sistema"
                );
            }
        }

        usuario.setAtivo(false);
        log.info("Conta desativada pelo próprio usuário: {}", email);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public void delete(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        usuario.setAtivo(false);
        usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    public List<Usuario> findByTipo(TipoUsuario tipo) {
        return usuarioRepository.findByTipo(tipo);
    }
}
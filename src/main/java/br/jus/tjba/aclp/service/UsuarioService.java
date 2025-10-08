package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.UsuarioDTO;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
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

    @Transactional
    public Usuario update(Long id, UsuarioDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        // Verificar se email já existe em outro usuário
        if (!usuario.getEmail().equals(dto.getEmail()) &&
                usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email já está em uso");
        }

        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        if (dto.getSenha() != null && !dto.getSenha().isEmpty()) {
            usuario.setSenha(passwordEncoder.encode(dto.getSenha()));
        }
        usuario.setTipo(dto.getTipo());
        usuario.setDepartamento(dto.getDepartamento());
        usuario.setComarca(dto.getComarca());
        usuario.setCargo(dto.getCargo());

        return usuarioRepository.save(usuario);
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
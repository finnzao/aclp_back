package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.UsuarioDTO;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.service.UsuarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsuarioController.class)
@DisplayName("Testes do Controller de Usuários")
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuarioService usuarioService;

    @Autowired
    private ObjectMapper objectMapper;

    private Usuario usuario;
    private UsuarioDTO usuarioDTO;

    @BeforeEach
    void setUp() {
        // Preparar usuário de teste
        usuario = Usuario.builder()
                .id(1L)
                .nome("Maria Santos")
                .email("maria.santos@tjba.jus.br")
                .senha("senhaEncriptada")
                .tipo(TipoUsuario.USUARIO)
                .departamento("Vara Criminal")
                .ativo(true)
                .criadoEm(LocalDateTime.now())
                .build();

        // Preparar DTO
        usuarioDTO = UsuarioDTO.builder()
                .nome("Maria Santos")
                .email("maria.santos@tjba.jus.br")
                .senha("Senha@123")
                .tipo(TipoUsuario.USUARIO)
                .departamento("Vara Criminal")
                .ativo(true)
                .build();
    }

    // ========== TESTES DE LISTAGEM ==========

    @Test
    @DisplayName("Deve listar todos os usuários ativos")
    void testFindAll() throws Exception {
        List<Usuario> usuarios = Arrays.asList(usuario);
        when(usuarioService.findAll()).thenReturn(usuarios);

        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuários listados com sucesso")))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].nome", is("Maria Santos")))
                .andExpect(jsonPath("$.data[0].email", is("maria.santos@tjba.jus.br")));
    }

    @Test
    @DisplayName("Deve buscar usuário por ID")
    void testFindById() throws Exception {
        when(usuarioService.findById(1L)).thenReturn(Optional.of(usuario));

        mockMvc.perform(get("/api/usuarios/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuário encontrado com sucesso")))
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.nome", is("Maria Santos")));
    }

    @Test
    @DisplayName("Deve retornar 404 quando usuário não encontrado")
    void testFindByIdNotFound() throws Exception {
        when(usuarioService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/usuarios/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("não encontrado")));
    }

    // ========== TESTES DE CRIAÇÃO ==========

    @Test
    @DisplayName("Deve criar novo usuário")
    void testSave() throws Exception {
        when(usuarioService.save(any(UsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuário cadastrado com sucesso")))
                .andExpect(jsonPath("$.data.nome", is("Maria Santos")));
    }

    @Test
    @DisplayName("Deve retornar erro ao criar usuário com email duplicado")
    void testSaveDuplicateEmail() throws Exception {
        when(usuarioService.save(any(UsuarioDTO.class)))
                .thenThrow(new IllegalArgumentException("Email já está em uso"));

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Email já está em uso")));
    }

    @Test
    @DisplayName("Deve criar usuário administrador")
    void testSaveAdmin() throws Exception {
        usuarioDTO.setTipo(TipoUsuario.ADMIN);
        usuario.setTipo(TipoUsuario.ADMIN);

        when(usuarioService.save(any(UsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.tipo", is("ADMIN")));
    }

    // ========== TESTES DE ATUALIZAÇÃO ==========

    @Test
    @DisplayName("Deve atualizar usuário")
    void testUpdate() throws Exception {
        usuarioDTO.setNome("Maria Santos Silva");
        usuario.setNome("Maria Santos Silva");

        when(usuarioService.update(eq(1L), any(UsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(put("/api/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuário atualizado com sucesso")))
                .andExpect(jsonPath("$.data.nome", is("Maria Santos Silva")));
    }

    @Test
    @DisplayName("Deve retornar erro ao atualizar email para um já existente")
    void testUpdateDuplicateEmail() throws Exception {
        when(usuarioService.update(eq(1L), any(UsuarioDTO.class)))
                .thenThrow(new IllegalArgumentException("Email já está em uso"));

        mockMvc.perform(put("/api/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Email já está em uso")));
    }

    // ========== TESTES DE EXCLUSÃO ==========

    @Test
    @DisplayName("Deve desativar usuário")
    void testDelete() throws Exception {
        doNothing().when(usuarioService).delete(1L);

        mockMvc.perform(delete("/api/usuarios/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuário desativado com sucesso")));
    }

    @Test
    @DisplayName("Deve retornar erro ao desativar usuário inexistente")
    void testDeleteNotFound() throws Exception {
        doThrow(new IllegalArgumentException("Usuário não encontrado"))
                .when(usuarioService).delete(999L);

        mockMvc.perform(delete("/api/usuarios/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("não encontrado")));
    }

    // ========== TESTES DE BUSCA POR TIPO ==========

    @Test
    @DisplayName("Deve buscar usuários por tipo ADMIN")
    void testFindByTipoAdmin() throws Exception {
        usuario.setTipo(TipoUsuario.ADMIN);
        List<Usuario> admins = Arrays.asList(usuario);

        when(usuarioService.findByTipo(TipoUsuario.ADMIN)).thenReturn(admins);

        mockMvc.perform(get("/api/usuarios/tipo/ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuários encontrados com sucesso")))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tipo", is("ADMIN")));
    }

    @Test
    @DisplayName("Deve buscar usuários por tipo USUARIO")
    void testFindByTipoUsuario() throws Exception {
        List<Usuario> usuarios = Arrays.asList(usuario);

        when(usuarioService.findByTipo(TipoUsuario.USUARIO)).thenReturn(usuarios);

        mockMvc.perform(get("/api/usuarios/tipo/USUARIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tipo", is("USUARIO")));
    }

    @Test
    @DisplayName("Deve retornar erro ao buscar com tipo inválido")
    void testFindByTipoInvalid() throws Exception {
        mockMvc.perform(get("/api/usuarios/tipo/INVALIDO"))
                .andExpect(status().isBadRequest());
    }

    // ========== TESTE DE OPTIONS (CORS) ==========

    @Test
    @DisplayName("Deve responder ao OPTIONS request")
    void testOptions() throws Exception {
        mockMvc.perform(options("/api/usuarios"))
                .andExpect(status().isOk());
    }

    // ========== TESTES DE VALIDAÇÃO ==========

    @Test
    @DisplayName("Deve retornar erro ao criar usuário sem nome")
    void testSaveWithoutName() throws Exception {
        usuarioDTO.setNome(null);

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar erro ao criar usuário com email inválido")
    void testSaveWithInvalidEmail() throws Exception {
        usuarioDTO.setEmail("email-invalido");

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar erro ao criar usuário sem senha")
    void testSaveWithoutPassword() throws Exception {
        usuarioDTO.setSenha(null);

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isBadRequest());
    }
}
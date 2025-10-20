package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.AtualizarUsuarioDTO;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
    private AtualizarUsuarioDTO atualizarUsuarioDTO;

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
                .comarca("Salvador")
                .cargo("Analista")
                .ativo(true)
                .criadoEm(LocalDateTime.now())
                .build();

        // Preparar DTO de criação
        usuarioDTO = UsuarioDTO.builder()
                .nome("Maria Santos")
                .email("maria.santos@tjba.jus.br")
                .senha("Senha@123")
                .tipo(TipoUsuario.USUARIO)
                .departamento("Vara Criminal")
                .comarca("Salvador")
                .cargo("Analista")
                .ativo(true)
                .build();

        // Preparar DTO de atualização (campos opcionais)
        atualizarUsuarioDTO = AtualizarUsuarioDTO.builder()
                .nome("Maria Santos Silva")
                .departamento("Vara Civil")
                .build();
    }

    // ========== TESTES DE LISTAGEM ==========

    @Test
    @DisplayName("Deve listar todos os usuários ativos")
    @WithMockUser(roles = "ADMIN")
    void testFindAll() throws Exception {
        List<Usuario> usuarios = Arrays.asList(usuario);
        when(usuarioService.findAll()).thenReturn(usuarios);

        mockMvc.perform(get("/api/usuarios")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuários listados com sucesso")))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].nome", is("Maria Santos")))
                .andExpect(jsonPath("$.data[0].email", is("maria.santos@tjba.jus.br")));
    }

    @Test
    @DisplayName("Deve negar acesso ao listar sem permissão ADMIN")
    @WithMockUser(roles = "USUARIO")
    void testFindAllForbidden() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Deve buscar usuário por ID")
    @WithMockUser(roles = "ADMIN")
    void testFindById() throws Exception {
        when(usuarioService.findById(1L)).thenReturn(Optional.of(usuario));

        mockMvc.perform(get("/api/usuarios/1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuário encontrado com sucesso")))
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.nome", is("Maria Santos")));
    }

    @Test
    @DisplayName("Deve retornar 404 quando usuário não encontrado")
    @WithMockUser(roles = "ADMIN")
    void testFindByIdNotFound() throws Exception {
        when(usuarioService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/usuarios/999")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("não encontrado")));
    }

    // ========== TESTES DE CRIAÇÃO ==========

    @Test
    @DisplayName("Deve criar novo usuário")
    @WithMockUser(roles = "ADMIN")
    void testSave() throws Exception {
        when(usuarioService.save(any(UsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuário cadastrado com sucesso")))
                .andExpect(jsonPath("$.data.nome", is("Maria Santos")));
    }

    @Test
    @DisplayName("Deve retornar erro ao criar usuário com email duplicado")
    @WithMockUser(roles = "ADMIN")
    void testSaveDuplicateEmail() throws Exception {
        when(usuarioService.save(any(UsuarioDTO.class)))
                .thenThrow(new IllegalArgumentException("Email já está em uso"));

        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Email já está em uso")));
    }

    @Test
    @DisplayName("Deve criar usuário administrador")
    @WithMockUser(roles = "ADMIN")
    void testSaveAdmin() throws Exception {
        usuarioDTO.setTipo(TipoUsuario.ADMIN);
        usuario.setTipo(TipoUsuario.ADMIN);

        when(usuarioService.save(any(UsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.tipo", is("ADMIN")));
    }

    @Test
    @DisplayName("Deve negar criação de usuário sem permissão ADMIN")
    @WithMockUser(roles = "USUARIO")
    void testSaveForbidden() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isForbidden());
    }

    // ========== TESTES DE ATUALIZAÇÃO (PARCIAL) ==========

    @Test
    @DisplayName("Deve atualizar usuário com atualização parcial")
    @WithMockUser(roles = "ADMIN")
    void testUpdateParcial() throws Exception {
        usuario.setNome("Maria Santos Silva");
        usuario.setDepartamento("Vara Civil");

        when(usuarioService.update(eq(1L), any(AtualizarUsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(put("/api/usuarios/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(atualizarUsuarioDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuário atualizado com sucesso")))
                .andExpect(jsonPath("$.data.nome", is("Maria Santos Silva")))
                .andExpect(jsonPath("$.data.departamento", is("Vara Civil")));
    }

    @Test
    @DisplayName("Deve atualizar apenas nome do usuário")
    @WithMockUser(roles = "ADMIN")
    void testUpdateApenasNome() throws Exception {
        AtualizarUsuarioDTO dtoApenasNome = AtualizarUsuarioDTO.builder()
                .nome("João Silva")
                .build();

        usuario.setNome("João Silva");

        when(usuarioService.update(eq(1L), any(AtualizarUsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(put("/api/usuarios/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoApenasNome)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.nome", is("João Silva")));
    }

    @Test
    @DisplayName("Deve atualizar tipo do usuário para ADMIN")
    @WithMockUser(roles = "ADMIN")
    void testUpdateTipo() throws Exception {
        AtualizarUsuarioDTO dtoTipo = AtualizarUsuarioDTO.builder()
                .tipo(TipoUsuario.ADMIN)
                .build();

        usuario.setTipo(TipoUsuario.ADMIN);

        when(usuarioService.update(eq(1L), any(AtualizarUsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(put("/api/usuarios/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoTipo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.tipo", is("ADMIN")));
    }

    @Test
    @DisplayName("Deve atualizar status ativo do usuário")
    @WithMockUser(roles = "ADMIN")
    void testUpdateAtivo() throws Exception {
        AtualizarUsuarioDTO dtoAtivo = AtualizarUsuarioDTO.builder()
                .ativo(false)
                .build();

        usuario.setAtivo(false);

        when(usuarioService.update(eq(1L), any(AtualizarUsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(put("/api/usuarios/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoAtivo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.ativo", is(false)));
    }

    @Test
    @DisplayName("Deve retornar erro ao atualizar email para um já existente")
    @WithMockUser(roles = "ADMIN")
    void testUpdateDuplicateEmail() throws Exception {
        AtualizarUsuarioDTO dtoEmail = AtualizarUsuarioDTO.builder()
                .email("outro.email@tjba.jus.br")
                .build();

        when(usuarioService.update(eq(1L), any(AtualizarUsuarioDTO.class)))
                .thenThrow(new IllegalArgumentException("Email já está em uso"));

        mockMvc.perform(put("/api/usuarios/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoEmail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Email já está em uso")));
    }

    @Test
    @DisplayName("Deve negar atualização sem permissão ADMIN")
    @WithMockUser(roles = "USUARIO")
    void testUpdateForbidden() throws Exception {
        mockMvc.perform(put("/api/usuarios/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(atualizarUsuarioDTO)))
                .andExpect(status().isForbidden());
    }

    // ========== TESTES DE EXCLUSÃO ==========

    @Test
    @DisplayName("Deve desativar usuário")
    @WithMockUser(roles = "ADMIN")
    void testDelete() throws Exception {
        doNothing().when(usuarioService).delete(1L);

        mockMvc.perform(delete("/api/usuarios/1")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuário desativado com sucesso")));
    }

    @Test
    @DisplayName("Deve retornar erro ao desativar usuário inexistente")
    @WithMockUser(roles = "ADMIN")
    void testDeleteNotFound() throws Exception {
        doThrow(new IllegalArgumentException("Usuário não encontrado"))
                .when(usuarioService).delete(999L);

        mockMvc.perform(delete("/api/usuarios/999")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("não encontrado")));
    }

    @Test
    @DisplayName("Deve negar exclusão sem permissão ADMIN")
    @WithMockUser(roles = "USUARIO")
    void testDeleteForbidden() throws Exception {
        mockMvc.perform(delete("/api/usuarios/1")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ========== TESTES DE BUSCA POR TIPO ==========

    @Test
    @DisplayName("Deve buscar usuários por tipo ADMIN")
    @WithMockUser(roles = "ADMIN")
    void testFindByTipoAdmin() throws Exception {
        usuario.setTipo(TipoUsuario.ADMIN);
        List<Usuario> admins = Arrays.asList(usuario);

        when(usuarioService.findByTipo(TipoUsuario.ADMIN)).thenReturn(admins);

        mockMvc.perform(get("/api/usuarios/tipo/ADMIN")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Usuários encontrados com sucesso")))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tipo", is("ADMIN")));
    }

    @Test
    @DisplayName("Deve buscar usuários por tipo USUARIO")
    @WithMockUser(roles = "ADMIN")
    void testFindByTipoUsuario() throws Exception {
        List<Usuario> usuarios = Arrays.asList(usuario);

        when(usuarioService.findByTipo(TipoUsuario.USUARIO)).thenReturn(usuarios);

        mockMvc.perform(get("/api/usuarios/tipo/USUARIO")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tipo", is("USUARIO")));
    }

    @Test
    @DisplayName("Deve retornar erro ao buscar com tipo inválido")
    @WithMockUser(roles = "ADMIN")
    void testFindByTipoInvalid() throws Exception {
        mockMvc.perform(get("/api/usuarios/tipo/INVALIDO")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // ========== TESTE DE OPTIONS (CORS) ==========

    @Test
    @DisplayName("Deve responder ao OPTIONS request")
    void testOptions() throws Exception {
        mockMvc.perform(options("/api/usuarios"))
                .andExpect(status().isOk());
    }

    // ========== TESTES DE VALIDAÇÃO (CRIAÇÃO) ==========

    @Test
    @DisplayName("Deve retornar erro ao criar usuário sem nome")
    @WithMockUser(roles = "ADMIN")
    void testSaveWithoutName() throws Exception {
        usuarioDTO.setNome(null);

        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar erro ao criar usuário com email inválido")
    @WithMockUser(roles = "ADMIN")
    void testSaveWithInvalidEmail() throws Exception {
        usuarioDTO.setEmail("email-invalido");

        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar erro ao criar usuário sem senha")
    @WithMockUser(roles = "ADMIN")
    void testSaveWithoutPassword() throws Exception {
        usuarioDTO.setSenha(null);

        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar erro ao criar usuário sem tipo")
    @WithMockUser(roles = "ADMIN")
    void testSaveWithoutTipo() throws Exception {
        usuarioDTO.setTipo(null);

        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuarioDTO)))
                .andExpect(status().isBadRequest());
    }

    // ========== TESTES DE VALIDAÇÃO (ATUALIZAÇÃO) ==========

    @Test
    @DisplayName("Deve aceitar atualização com email válido")
    @WithMockUser(roles = "ADMIN")
    void testUpdateWithValidEmail() throws Exception {
        AtualizarUsuarioDTO dtoEmail = AtualizarUsuarioDTO.builder()
                .email("novo.email@tjba.jus.br")
                .build();

        usuario.setEmail("novo.email@tjba.jus.br");

        when(usuarioService.update(eq(1L), any(AtualizarUsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(put("/api/usuarios/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("Deve rejeitar atualização com email inválido")
    @WithMockUser(roles = "ADMIN")
    void testUpdateWithInvalidEmail() throws Exception {
        AtualizarUsuarioDTO dtoEmail = AtualizarUsuarioDTO.builder()
                .email("email-invalido")
                .build();

        mockMvc.perform(put("/api/usuarios/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoEmail)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve aceitar atualização sem campos obrigatórios")
    @WithMockUser(roles = "ADMIN")
    void testUpdateSemCamposObrigatorios() throws Exception {
        AtualizarUsuarioDTO dtoVazio = AtualizarUsuarioDTO.builder()
                .build();

        when(usuarioService.update(eq(1L), any(AtualizarUsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(put("/api/usuarios/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoVazio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    // ========== TESTES DE SEGURANÇA ==========

    @Test
    @DisplayName("Deve negar acesso sem autenticação")
    void testAccessWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Deve aceitar CSRF token válido")
    @WithMockUser(roles = "ADMIN")
    void testCsrfToken() throws Exception {
        when(usuarioService.findAll()).thenReturn(Arrays.asList(usuario));

        mockMvc.perform(get("/api/usuarios")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // ========== TESTE DE MÚLTIPLOS CAMPOS ==========

    @Test
    @DisplayName("Deve atualizar múltiplos campos simultaneamente")
    @WithMockUser(roles = "ADMIN")
    void testUpdateMultiplosCampos() throws Exception {
        AtualizarUsuarioDTO dtoCompleto = AtualizarUsuarioDTO.builder()
                .nome("José Santos")
                .email("jose.santos@tjba.jus.br")
                .departamento("Vara Cível")
                .comarca("Feira de Santana")
                .cargo("Coordenador")
                .build();

        usuario.setNome("José Santos");
        usuario.setEmail("jose.santos@tjba.jus.br");
        usuario.setDepartamento("Vara Cível");
        usuario.setComarca("Feira de Santana");
        usuario.setCargo("Coordenador");

        when(usuarioService.update(eq(1L), any(AtualizarUsuarioDTO.class))).thenReturn(usuario);

        mockMvc.perform(put("/api/usuarios/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoCompleto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.nome", is("José Santos")))
                .andExpect(jsonPath("$.data.email", is("jose.santos@tjba.jus.br")))
                .andExpect(jsonPath("$.data.departamento", is("Vara Cível")))
                .andExpect(jsonPath("$.data.comarca", is("Feira de Santana")))
                .andExpect(jsonPath("$.data.cargo", is("Coordenador")));
    }
}
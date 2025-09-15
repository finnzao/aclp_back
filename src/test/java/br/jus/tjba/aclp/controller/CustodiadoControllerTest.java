package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.CustodiadoDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.service.CustodiadoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustodiadoController.class)
@DisplayName("Testes do Controller de Custodiados")
class CustodiadoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustodiadoService custodiadoService;

    @Autowired
    private ObjectMapper objectMapper;

    private Custodiado custodiado;
    private CustodiadoDTO custodiadoDTO;

    @BeforeEach
    void setUp() {
        // Preparar custodiado de teste
        custodiado = Custodiado.builder()
                .id(1L)
                .nome("João da Silva")
                .cpf("123.456.789-00")
                .rg("1234567")
                .contato("(71) 99999-9999")
                .processo("0000001-00.2024.8.05.0001")
                .vara("1ª Vara Criminal")
                .comarca("Salvador")
                .dataDecisao(LocalDate.of(2024, 1, 15))
                .periodicidade(30)
                .dataComparecimentoInicial(LocalDate.of(2024, 2, 1))
                .status(StatusComparecimento.EM_CONFORMIDADE)
                .ultimoComparecimento(LocalDate.of(2024, 9, 1))
                .proximoComparecimento(LocalDate.of(2024, 10, 1))
                .observacoes("Custodiado em conformidade")
                .build();

        // Preparar DTO
        custodiadoDTO = CustodiadoDTO.builder()
                .nome("João da Silva")
                .cpf("123.456.789-00")
                .rg("1234567")
                .contato("(71) 99999-9999")
                .processo("0000001-00.2024.8.05.0001")
                .vara("1ª Vara Criminal")
                .comarca("Salvador")
                .dataDecisao(LocalDate.of(2024, 1, 15))
                .periodicidade(30)
                .dataComparecimentoInicial(LocalDate.of(2024, 2, 1))
                .observacoes("Custodiado em conformidade")
                .cep("40070-110")
                .logradouro("Avenida Sete de Setembro")
                .numero("1234")
                .bairro("Centro")
                .cidade("Salvador")
                .estado("BA")
                .build();
    }

    // ========== TESTES DE LISTAGEM ==========

    @Test
    @DisplayName("Deve listar todos os custodiados")
    void testFindAll() throws Exception {
        List<Custodiado> custodiados = Arrays.asList(custodiado);
        when(custodiadoService.findAll()).thenReturn(custodiados);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/custodiados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Custodiados listados com sucesso")))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].nome", is("João da Silva")))
                .andExpect(jsonPath("$.data[0].processo", is("0000001-00.2024.8.05.0001")));
    }

    @Test
    @DisplayName("Deve buscar custodiado por ID")
    void testFindById() throws Exception {
        when(custodiadoService.findById(1L)).thenReturn(Optional.of(custodiado));
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/custodiados/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Custodiado encontrado com sucesso")))
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.nome", is("João da Silva")));
    }

    @Test
    @DisplayName("Deve retornar 404 quando custodiado não encontrado")
    void testFindByIdNotFound() throws Exception {
        when(custodiadoService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/custodiados/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("não encontrado")));
    }

    @Test
    @DisplayName("Deve buscar custodiados por processo")
    void testFindByProcesso() throws Exception {
        List<Custodiado> custodiados = Arrays.asList(custodiado);
        when(custodiadoService.findByProcesso("0000001-00.2024.8.05.0001")).thenReturn(custodiados);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/custodiados/processo/0000001-00.2024.8.05.0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].processo", is("0000001-00.2024.8.05.0001")));
    }

    // ========== TESTES DE CRIAÇÃO ==========

    @Test
    @DisplayName("Deve criar novo custodiado")
    void testSave() throws Exception {
        when(custodiadoService.save(any(CustodiadoDTO.class))).thenReturn(custodiado);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(post("/api/custodiados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custodiadoDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Custodiado cadastrado com sucesso")))
                .andExpect(jsonPath("$.data.nome", is("João da Silva")));
    }

    @Test
    @DisplayName("Deve retornar erro ao criar custodiado com dados inválidos")
    void testSaveInvalid() throws Exception {
        CustodiadoDTO invalidDTO = new CustodiadoDTO();
        // DTO sem campos obrigatórios

        mockMvc.perform(post("/api/custodiados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar erro ao criar custodiado com CPF duplicado")
    void testSaveDuplicateCPF() throws Exception {
        when(custodiadoService.save(any(CustodiadoDTO.class)))
                .thenThrow(new IllegalArgumentException("CPF já está cadastrado no sistema"));

        mockMvc.perform(post("/api/custodiados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custodiadoDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("CPF já está cadastrado")));
    }

    // ========== TESTES DE ATUALIZAÇÃO ==========

    @Test
    @DisplayName("Deve atualizar custodiado")
    void testUpdate() throws Exception {
        when(custodiadoService.update(eq(1L), any(CustodiadoDTO.class))).thenReturn(custodiado);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(put("/api/custodiados/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custodiadoDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Custodiado atualizado com sucesso")));
    }

    // ========== TESTES DE EXCLUSÃO ==========

    @Test
    @DisplayName("Deve excluir custodiado")
    void testDelete() throws Exception {
        doNothing().when(custodiadoService).delete(1L);

        mockMvc.perform(delete("/api/custodiados/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Custodiado excluído com sucesso")));
    }

    // ========== TESTES DE BUSCA POR STATUS ==========

    @Test
    @DisplayName("Deve buscar custodiados por status")
    void testFindByStatus() throws Exception {
        List<Custodiado> custodiados = Arrays.asList(custodiado);
        when(custodiadoService.findByStatus(StatusComparecimento.EM_CONFORMIDADE)).thenReturn(custodiados);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/custodiados/status/EM_CONFORMIDADE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status", is("EM_CONFORMIDADE")));
    }

    @Test
    @DisplayName("Deve buscar custodiados com comparecimento hoje")
    void testFindComparecimentosHoje() throws Exception {
        List<Custodiado> custodiados = Arrays.asList(custodiado);
        when(custodiadoService.findComparecimentosHoje()).thenReturn(custodiados);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/custodiados/comparecimentos/hoje"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Deve buscar custodiados inadimplentes")
    void testFindInadimplentes() throws Exception {
        custodiado.setStatus(StatusComparecimento.INADIMPLENTE);
        List<Custodiado> custodiados = Arrays.asList(custodiado);
        when(custodiadoService.findInadimplentes()).thenReturn(custodiados);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/custodiados/inadimplentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status", is("INADIMPLENTE")));
    }

    // ========== TESTES DE BUSCA ==========

    @Test
    @DisplayName("Deve buscar custodiados por termo")
    void testBuscar() throws Exception {
        List<Custodiado> custodiados = Arrays.asList(custodiado);
        when(custodiadoService.buscarPorNomeOuProcesso("João")).thenReturn(custodiados);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/custodiados/buscar")
                        .param("termo", "João"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].nome", containsString("João")));
    }

    @Test
    @DisplayName("Deve retornar erro ao buscar com termo inválido")
    void testBuscarTermoInvalido() throws Exception {
        when(custodiadoService.buscarPorNomeOuProcesso("a"))
                .thenThrow(new IllegalArgumentException("Termo de busca deve ter pelo menos 2 caracteres"));

        mockMvc.perform(get("/api/custodiados/buscar")
                        .param("termo", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("2 caracteres")));
    }

    // ========== TESTES DE CONTAGEM ==========

    @Test
    @DisplayName("Deve contar custodiados por processo")
    void testCountByProcesso() throws Exception {
        List<Custodiado> custodiados = Arrays.asList(custodiado, custodiado);
        when(custodiadoService.findByProcesso("0000001-00.2024.8.05.0001")).thenReturn(custodiados);

        mockMvc.perform(get("/api/custodiados/processo/0000001-00.2024.8.05.0001/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", is(2)))
                .andExpect(jsonPath("$.message", containsString("2 custodiado(s)")));
    }

    // ========== TESTE DE OPTIONS (CORS) ==========

    @Test
    @DisplayName("Deve responder ao OPTIONS request")
    void testOptions() throws Exception {
        mockMvc.perform(options("/api/custodiados"))
                .andExpect(status().isOk());
    }
}
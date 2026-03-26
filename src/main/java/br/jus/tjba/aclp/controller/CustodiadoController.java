package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.*;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.service.CustodiadoService;
import br.jus.tjba.aclp.service.HistoricoEnderecoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/custodiados")
@RequiredArgsConstructor
@Tag(name = "Custodiados", description = "API para gerenciamento de custodiados em liberdade provisória")
@Slf4j
public class CustodiadoController {

    private final CustodiadoService custodiadoService;
    private final HistoricoEnderecoService historicoEnderecoService;

    @lombok.Data
    @lombok.Builder
    public static class EnderecoDetalhadoDTO {
        private Long id;
        private String cep;
        private String logradouro;
        private String numero;
        private String complemento;
        private String bairro;
        private String cidade;
        private String estado;
        private String nomeEstado;
        private String regiaoEstado;
        private LocalDate dataInicio;
        private LocalDate dataFim;
        private Boolean ativo;
        private String motivoAlteracao;
        private String validadoPor;
        private String enderecoCompleto;
        private String enderecoResumido;
        private Long diasResidencia;
        private String periodoResidencia;
        private LocalDateTime criadoEm;
        private LocalDateTime atualizadoEm;
    }

    @lombok.Data
    @lombok.Builder
    public static class CustodiadoResponseDTO {
        private String id;
        private String nome;
        private String cpf;
        private String rg;
        private String contato;
        private boolean contatoPendente;
        private String processo;
        private String vara;
        private String comarca;
        private LocalDate dataDecisao;
        private Integer periodicidade;
        private String periodicidadeDescricao;
        private LocalDate dataComparecimentoInicial;
        private StatusComparecimento status;
        private LocalDate ultimoComparecimento;
        private LocalDate proximoComparecimento;
        private Long diasAtraso;
        private String observacoes;
        private EnderecoDetalhadoDTO endereco;
        private LocalDateTime criadoEm;
        private LocalDateTime atualizadoEm;
        private String identificacao;
        private boolean inadimplente;
        private boolean comparecimentoHoje;

        public static CustodiadoResponseDTO fromEntity(Custodiado custodiado,
                                                        HistoricoEnderecoService enderecoService) {
            EnderecoDetalhadoDTO enderecoDetalhado = null;
            try {
                Optional<HistoricoEnderecoDTO> enderecoOpt =
                        enderecoService.buscarEnderecoAtivo(custodiado.getId());
                if (enderecoOpt.isPresent()) {
                    HistoricoEnderecoDTO enderecoDTO = enderecoOpt.get();
                    enderecoDetalhado = EnderecoDetalhadoDTO.builder()
                            .id(enderecoDTO.getId()).cep(enderecoDTO.getCep())
                            .logradouro(enderecoDTO.getLogradouro()).numero(enderecoDTO.getNumero())
                            .complemento(enderecoDTO.getComplemento()).bairro(enderecoDTO.getBairro())
                            .cidade(enderecoDTO.getCidade()).estado(enderecoDTO.getEstado())
                            .nomeEstado(enderecoDTO.getNomeEstado()).regiaoEstado(enderecoDTO.getRegiaoEstado())
                            .dataInicio(enderecoDTO.getDataInicio()).dataFim(enderecoDTO.getDataFim())
                            .ativo(enderecoDTO.getEnderecoAtivo()).motivoAlteracao(enderecoDTO.getMotivoAlteracao())
                            .validadoPor(enderecoDTO.getValidadoPor())
                            .enderecoCompleto(enderecoDTO.getEnderecoCompleto())
                            .enderecoResumido(enderecoDTO.getEnderecoResumido())
                            .diasResidencia(enderecoDTO.getDiasResidencia())
                            .periodoResidencia(enderecoDTO.getPeriodoResidencia())
                            .criadoEm(enderecoDTO.getCriadoEm()).atualizadoEm(enderecoDTO.getAtualizadoEm())
                            .build();
                }
            } catch (Exception e) {
                log.warn("Erro ao buscar endereço do custodiado publicId {}: {}", custodiado.getPublicId(), e.getMessage());
            }

            return CustodiadoResponseDTO.builder()
                    .id(custodiado.getPublicId() != null ? custodiado.getPublicId().toString() : null)
                    .nome(custodiado.getNome()).cpf(custodiado.getCpf()).rg(custodiado.getRg())
                    .contato(custodiado.getContato()).contatoPendente(custodiado.isContatoPendente())
                    .processo(custodiado.getProcesso()).vara(custodiado.getVara()).comarca(custodiado.getComarca())
                    .dataDecisao(custodiado.getDataDecisao()).periodicidade(custodiado.getPeriodicidade())
                    .periodicidadeDescricao(custodiado.getPeriodicidadeDescricao())
                    .dataComparecimentoInicial(custodiado.getDataComparecimentoInicial())
                    .status(custodiado.getStatus()).ultimoComparecimento(custodiado.getUltimoComparecimento())
                    .proximoComparecimento(custodiado.getProximoComparecimento())
                    .diasAtraso(custodiado.getDiasAtraso()).observacoes(custodiado.getObservacoes())
                    .endereco(enderecoDetalhado).criadoEm(custodiado.getCriadoEm())
                    .atualizadoEm(custodiado.getAtualizadoEm()).identificacao(custodiado.getIdentificacao())
                    .inadimplente(custodiado.isInadimplente()).comparecimentoHoje(custodiado.isComparecimentoHoje())
                    .build();
        }
    }

    // =====================================================================
    // NOVO: POST /api/custodiados/cadastro-inicial
    // Cria Custodiado + Processo + Endereço + Comparecimento em 1 request
    // =====================================================================

    @PostMapping("/cadastro-inicial")
    @Operation(summary = "Cadastro inicial completo",
            description = """
                    Cria custodiado + processo + endereço + primeiro comparecimento em uma única requisição.
                    
                    Corresponde ao formulário de cadastro com 6 seções:
                    1. Dados Pessoais (nome, contato opcional)
                    2. Documentos (CPF e/ou RG — pelo menos um)
                    3. Dados Processuais (processo, vara, comarca, datas)
                    4. Periodicidade
                    5. Endereço completo
                    6. Observações (opcional)
                    
                    Regras:
                    - Contato é opcional; se não informado, salva como "Pendente"
                    - CPF e RG são individualmente opcionais, mas pelo menos um é obrigatório
                    - Processo é obrigatório e será criado na tabela processos
                    """)
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Cadastro inicial realizado com sucesso — custodiado, processo, endereço e comparecimento criados"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Dados inválidos — verifique os campos obrigatórios"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Conflito — CPF ou RG já cadastrado para custodiado ativo")
    })
    public ResponseEntity<ApiResponse<CadastroInicialResponseDTO>> cadastroInicial(
            @Valid @RequestBody CadastroInicialDTO dto) {

        log.info("POST /cadastro-inicial — Processo: {}, Nome: {}, CPF: {}, RG: {}, Contato: {}",
                dto.getProcesso(), dto.getNome(),
                dto.getCpf() != null && !dto.getCpf().isEmpty() ? "sim" : "não",
                dto.getRg() != null && !dto.getRg().isEmpty() ? "sim" : "não",
                dto.getContato() != null && !dto.getContato().isEmpty() ? "sim" : "Pendente");

        try {
            CadastroInicialResponseDTO response = custodiadoService.cadastroInicial(dto);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.success("Cadastro inicial realizado com sucesso", response));

        } catch (DataIntegrityViolationException e) {
            log.warn("Conflito no cadastro inicial: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(extrairMensagemIntegridade(e)));
        } catch (IllegalArgumentException e) {
            log.warn("Validação falhou no cadastro inicial: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro inesperado no cadastro inicial", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro interno ao realizar cadastro. Tente novamente."));
        }
    }

    // =====================================================================
    // Endpoints existentes (mantidos)
    // =====================================================================

    @GetMapping
    @Operation(summary = "Listar todos os custodiados")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> findAll() {
        try {
            List<CustodiadoListDTO> response = custodiadoService.findAllActive().stream()
                    .map(CustodiadoListDTO::fromEntity).collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Custodiados listados com sucesso", response));
        } catch (Exception e) {
            log.error("Erro ao listar custodiados", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao listar custodiados"));
        }
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Buscar custodiados por status")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> findByStatus(@PathVariable StatusComparecimento status) {
        try {
            List<CustodiadoListDTO> response = custodiadoService.findByStatus(status).stream()
                    .map(CustodiadoListDTO::fromEntity).collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Custodiados encontrados", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Status inválido: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Erro ao buscar"));
        }
    }

    @GetMapping("/comparecimentos/hoje")
    @Operation(summary = "Comparecimentos de hoje")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> findComparecimentosHoje() {
        try {
            List<CustodiadoListDTO> response = custodiadoService.findComparecimentosHoje().stream()
                    .map(CustodiadoListDTO::fromEntity).collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Comparecimentos de hoje", response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Erro ao buscar"));
        }
    }

    @GetMapping("/inadimplentes")
    @Operation(summary = "Custodiados inadimplentes")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> findInadimplentes() {
        try {
            List<CustodiadoListDTO> response = custodiadoService.findInadimplentes().stream()
                    .map(CustodiadoListDTO::fromEntity).collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Inadimplentes listados", response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Erro ao buscar"));
        }
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar custodiados por termo")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> buscar(@RequestParam String termo) {
        try {
            if (termo == null || termo.trim().isEmpty())
                return ResponseEntity.badRequest().body(ApiResponse.error("Termo de busca não pode ser vazio"));
            if (termo.trim().length() < 2)
                return ResponseEntity.badRequest().body(ApiResponse.error("Termo deve ter pelo menos 2 caracteres"));
            List<CustodiadoListDTO> response = custodiadoService.buscarPorNomeOuProcesso(termo).stream()
                    .map(CustodiadoListDTO::fromEntity).collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Busca realizada", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Erro na busca"));
        }
    }

    @GetMapping("/processo/{processo}")
    @Operation(summary = "Buscar custodiados por processo")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> findByProcesso(@PathVariable String processo) {
        try {
            if (processo == null || processo.trim().isEmpty())
                return ResponseEntity.badRequest().body(ApiResponse.error("Processo não pode ser vazio"));
            List<Custodiado> custodiados = custodiadoService.findByProcesso(processo);
            if (custodiados.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Nenhum custodiado encontrado"));
            List<CustodiadoListDTO> response = custodiados.stream().map(CustodiadoListDTO::fromEntity).collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Custodiados encontrados", response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Erro ao buscar"));
        }
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Buscar custodiado por ID (UUID)")
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> findById(@PathVariable String publicId) {
        try {
            return custodiadoService.findByPublicId(publicId)
                    .map(c -> ResponseEntity.ok(ApiResponse.success("Custodiado encontrado",
                            CustodiadoResponseDTO.fromEntity(c, historicoEnderecoService))))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("Custodiado não encontrado: " + publicId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Erro ao buscar"));
        }
    }

    @PostMapping
    @Operation(summary = "Cadastrar custodiado (endpoint legado)",
            description = "Use POST /api/custodiados/cadastro-inicial para cadastro completo com processo")
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> save(@Valid @RequestBody CustodiadoDTO dto) {
        try {
            boolean temCpf = dto.getCpf() != null && !dto.getCpf().trim().isEmpty();
            boolean temRg = dto.getRg() != null && !dto.getRg().trim().isEmpty();
            if (!temCpf && !temRg)
                return ResponseEntity.badRequest().body(ApiResponse.error("Pelo menos CPF ou RG deve ser informado"));
            if (dto.getId() != null) dto.setId(null);

            Custodiado custodiado = custodiadoService.save(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.success("Custodiado cadastrado", CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService)));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(extrairMensagemIntegridade(e)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao cadastrar custodiado", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Erro interno ao cadastrar"));
        }
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Atualizar custodiado por UUID")
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> update(@PathVariable String publicId, @Valid @RequestBody CustodiadoDTO dto) {
        try {
            boolean temCpf = dto.getCpf() != null && !dto.getCpf().trim().isEmpty();
            boolean temRg = dto.getRg() != null && !dto.getRg().trim().isEmpty();
            if (!temCpf && !temRg)
                return ResponseEntity.badRequest().body(ApiResponse.error("Pelo menos CPF ou RG deve ser informado"));

            Custodiado custodiado = custodiadoService.updateByPublicId(publicId, dto);
            return ResponseEntity.ok(ApiResponse.success("Custodiado atualizado",
                    CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService)));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Custodiado não encontrado: " + publicId));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(extrairMensagemIntegridade(e)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao atualizar custodiado publicId: {}", publicId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Erro interno ao atualizar"));
        }
    }

    @DeleteMapping("/{publicId}")
    @Operation(summary = "Excluir custodiado por UUID")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String publicId) {
        try {
            custodiadoService.deleteByPublicId(publicId);
            return ResponseEntity.ok(ApiResponse.success("Custodiado excluído com sucesso"));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Custodiado não encontrado: " + publicId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Erro ao excluir"));
        }
    }

    @GetMapping("/processo/{processo}/count")
    @Operation(summary = "Contar custodiados por processo")
    public ResponseEntity<ApiResponse<Long>> countByProcesso(@PathVariable String processo) {
        try {
            long count = custodiadoService.findByProcesso(processo).size();
            return ResponseEntity.ok(ApiResponse.success(String.format("Processo tem %d custodiado(s)", count), count));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Erro ao contar"));
        }
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() { return ResponseEntity.ok().build(); }

    // =====================================================================
    // Utilitários
    // =====================================================================

    private String extrairMensagemIntegridade(DataIntegrityViolationException e) {
        String msg = e.getMessage();
        if (msg != null) {
            if (msg.contains("cpf") || msg.contains("uk_custodiado_cpf")) return "CPF já cadastrado no sistema";
            if (msg.contains("rg") || msg.contains("uk_custodiado_rg")) return "RG já cadastrado no sistema";
            if (msg.contains("not null")) return "Campo obrigatório não foi preenchido";
        }
        return "Violação de integridade dos dados";
    }
}

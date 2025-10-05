package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.CustodiadoDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoEndereco;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    /**
     * DTO interno para dados detalhados do endereço
     */
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

    /**
     * DTO melhorado para resposta do custodiado com endereço detalhado
     */
    @lombok.Data
    @lombok.Builder
    public static class CustodiadoResponseDTO {
        private Long id;
        private String nome;
        private String cpf;
        private String rg;
        private String contato;
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

        // Endereço detalhado como objeto estruturado
        private EnderecoDetalhadoDTO endereco;

        // Metadados
        private LocalDateTime criadoEm;
        private LocalDateTime atualizadoEm;
        private String identificacao;
        private boolean inadimplente;
        private boolean comparecimentoHoje;

        /**
         * Converte entidade para DTO com endereço detalhado
         */
        public static CustodiadoResponseDTO fromEntity(Custodiado custodiado, HistoricoEnderecoService enderecoService) {
            // Buscar endereço ativo do custodiado
            EnderecoDetalhadoDTO enderecoDetalhado = null;

            try {
                Optional<br.jus.tjba.aclp.dto.HistoricoEnderecoDTO> enderecoOpt =
                        enderecoService.buscarEnderecoAtivo(custodiado.getId());

                if (enderecoOpt.isPresent()) {
                    br.jus.tjba.aclp.dto.HistoricoEnderecoDTO enderecoDTO = enderecoOpt.get();
                    enderecoDetalhado = EnderecoDetalhadoDTO.builder()
                            .id(enderecoDTO.getId())
                            .cep(enderecoDTO.getCep())
                            .logradouro(enderecoDTO.getLogradouro())
                            .numero(enderecoDTO.getNumero())
                            .complemento(enderecoDTO.getComplemento())
                            .bairro(enderecoDTO.getBairro())
                            .cidade(enderecoDTO.getCidade())
                            .estado(enderecoDTO.getEstado())
                            .nomeEstado(enderecoDTO.getNomeEstado())
                            .regiaoEstado(enderecoDTO.getRegiaoEstado())
                            .dataInicio(enderecoDTO.getDataInicio())
                            .dataFim(enderecoDTO.getDataFim())
                            .ativo(enderecoDTO.getEnderecoAtivo())
                            .motivoAlteracao(enderecoDTO.getMotivoAlteracao())
                            .validadoPor(enderecoDTO.getValidadoPor())
                            .enderecoCompleto(enderecoDTO.getEnderecoCompleto())
                            .enderecoResumido(enderecoDTO.getEnderecoResumido())
                            .diasResidencia(enderecoDTO.getDiasResidencia())
                            .periodoResidencia(enderecoDTO.getPeriodoResidencia())
                            .criadoEm(enderecoDTO.getCriadoEm())
                            .atualizadoEm(enderecoDTO.getAtualizadoEm())
                            .build();
                }
            } catch (Exception e) {
                log.warn("Erro ao buscar endereço do custodiado ID {}: {}", custodiado.getId(), e.getMessage());
            }

            return CustodiadoResponseDTO.builder()
                    .id(custodiado.getId())
                    .nome(custodiado.getNome())
                    .cpf(custodiado.getCpf())
                    .rg(custodiado.getRg())
                    .contato(custodiado.getContato())
                    .processo(custodiado.getProcesso())
                    .vara(custodiado.getVara())
                    .comarca(custodiado.getComarca())
                    .dataDecisao(custodiado.getDataDecisao())
                    .periodicidade(custodiado.getPeriodicidade())
                    .periodicidadeDescricao(custodiado.getPeriodicidadeDescricao())
                    .dataComparecimentoInicial(custodiado.getDataComparecimentoInicial())
                    .status(custodiado.getStatus())
                    .ultimoComparecimento(custodiado.getUltimoComparecimento())
                    .proximoComparecimento(custodiado.getProximoComparecimento())
                    .diasAtraso(custodiado.getDiasAtraso())
                    .observacoes(custodiado.getObservacoes())
                    // Endereço detalhado
                    .endereco(enderecoDetalhado)
                    // Metadados
                    .criadoEm(custodiado.getCriadoEm())
                    .atualizadoEm(custodiado.getAtualizadoEm())
                    .identificacao(custodiado.getIdentificacao())
                    .inadimplente(custodiado.isInadimplente())
                    .comparecimentoHoje(custodiado.isComparecimentoHoje())
                    .build();
        }
    }

    /**
     * Método auxiliar para tratar erros de validação
     */
    private String extrairMensagemValidacao(Exception e) {
        String mensagem = e.getMessage();

        // Tratar erros específicos de formatação
        if (mensagem != null) {
            if (mensagem.contains("processo") || mensagem.contains("Processo")) {
                return "Formato do número do processo é inválido. Use o formato correto (ex: 0000000-00.0000.0.00.0000)";
            }
            if (mensagem.contains("cpf") || mensagem.contains("CPF")) {
                return "CPF inválido. Verifique o formato (000.000.000-00) ou se o número é válido";
            }
            if (mensagem.contains("rg") || mensagem.contains("RG")) {
                return "RG inválido. Verifique o formato";
            }
            if (mensagem.contains("periodicidade")) {
                return "Periodicidade deve ser um número positivo (dias)";
            }
            if (mensagem.contains("data")) {
                return "Formato de data inválido. Use o formato YYYY-MM-DD";
            }
            if (mensagem.contains("nome") && mensagem.contains("tamanho")) {
                return "Nome deve ter entre 2 e 100 caracteres";
            }
            if (mensagem.contains("contato")) {
                return "Formato do contato inválido. Use formato de telefone válido";
            }
        }

        return mensagem;
    }

    /**
     * Método auxiliar para tratar erros de integridade de dados
     */
    private String extrairMensagemIntegridade(DataIntegrityViolationException e) {
        String mensagem = e.getMessage();

        if (mensagem != null) {
            if (mensagem.contains("cpf") || mensagem.contains("uk_custodiado_cpf")) {
                return "CPF já cadastrado no sistema. Verifique se o custodiado já existe";
            }
            if (mensagem.contains("rg") || mensagem.contains("uk_custodiado_rg")) {
                return "RG já cadastrado no sistema. Verifique se o custodiado já existe";
            }
            if (mensagem.contains("processo") && mensagem.contains("cpf")) {
                return "Já existe um custodiado com este CPF neste processo";
            }
            if (mensagem.contains("not null") || mensagem.contains("cannot be null")) {
                if (mensagem.contains("nome")) return "Nome é obrigatório";
                if (mensagem.contains("cpf")) return "CPF é obrigatório";
                if (mensagem.contains("processo")) return "Número do processo é obrigatório";
                return "Campo obrigatório não foi preenchido";
            }
        }

        return "Violação de integridade dos dados. Verifique se não há duplicação de informações";
    }

    @GetMapping
    @Operation(summary = "Listar todos os custodiados",
            description = "Retorna uma lista com todos os custodiados cadastrados com endereços detalhados")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Lista de custodiados retornada com sucesso")
    })
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findAll() {
        log.info("Listando todos os custodiados com endereços detalhados");

        try {
            List<Custodiado> custodiados = custodiadoService.findAll();

            List<CustodiadoResponseDTO> response = custodiados.stream()
                    .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success("Custodiados listados com sucesso", response)
            );
        } catch (Exception e) {
            log.error("Erro ao listar custodiados", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Erro ao listar custodiados. Tente novamente")
            );
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar custodiado por ID",
            description = "Retorna um custodiado específico pelo seu ID com endereço detalhado")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Custodiado encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Custodiado não encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "ID inválido")
    })
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> findById(
            @Parameter(description = "ID do custodiado") @PathVariable Long id) {
        log.info("Buscando custodiado por ID: {} com endereço detalhado", id);

        try {
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("ID deve ser um número positivo válido")
                );
            }

            return custodiadoService.findById(id)
                    .map(custodiado -> ResponseEntity.ok(
                            ApiResponse.success("Custodiado encontrado com sucesso",
                                    CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService))
                    ))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                            ApiResponse.error("Custodiado não encontrado com ID: " + id)
                    ));
        } catch (Exception e) {
            log.error("Erro ao buscar custodiado por ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Erro ao buscar custodiado. Tente novamente")
            );
        }
    }

    @GetMapping("/processo/{processo}")
    @Operation(summary = "Buscar custodiados por processo",
            description = "Retorna todos os custodiados de um processo com endereços detalhados")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Custodiados encontrados"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Nenhum custodiado encontrado para este processo"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Número do processo inválido")
    })
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findByProcesso(
            @Parameter(description = "Número do processo") @PathVariable String processo) {
        log.info("Buscando custodiados por processo: {} com endereços detalhados", processo);

        try {
            if (processo == null || processo.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Número do processo não pode ser vazio")
                );
            }

            List<Custodiado> custodiados = custodiadoService.findByProcesso(processo);

            if (custodiados.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ApiResponse.error("Nenhum custodiado encontrado com processo: " + processo)
                );
            }

            List<CustodiadoResponseDTO> response = custodiados.stream()
                    .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success("Custodiados encontrados com sucesso", response)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Número do processo com formato inválido: " + e.getMessage())
            );
        } catch (Exception e) {
            log.error("Erro ao buscar custodiados por processo: {}", processo, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Erro ao buscar custodiados por processo. Tente novamente")
            );
        }
    }

    @PostMapping
    @Operation(summary = "Cadastrar novo custodiado",
            description = "Cadastra um novo custodiado no sistema. O endereço será incluído no retorno")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Custodiado cadastrado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Conflito - CPF ou RG já cadastrado")
    })
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> save(@Valid @RequestBody CustodiadoDTO dto) {
        log.info("Cadastrando novo custodiado - Processo: {}, Nome: {}", dto.getProcesso(), dto.getNome());

        try {
            // Validações básicas antes de processar
            if (dto.getNome() == null || dto.getNome().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Nome é obrigatório")
                );
            }

            if (dto.getCpf() == null || dto.getCpf().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("CPF é obrigatório")
                );
            }

            if (dto.getProcesso() == null || dto.getProcesso().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Número do processo é obrigatório")
                );
            }

            if (dto.getId() != null) {
                log.warn("ID {} recebido na criação será ignorado - ID é auto-increment", dto.getId());
                dto.setId(null);
            }

            if (dto.getDataComparecimentoInicial() == null) {
                log.info("Data de comparecimento inicial não fornecida - será usada data atual");
            } else {
                log.info("Data de comparecimento inicial fornecida: {}", dto.getDataComparecimentoInicial());
            }

            Custodiado custodiado = custodiadoService.save(dto);
            log.info("Custodiado cadastrado com sucesso. ID gerado: {}", custodiado.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.success("Custodiado cadastrado com sucesso",
                            CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService))
            );
        } catch (DataIntegrityViolationException e) {
            log.warn("Erro de integridade ao cadastrar custodiado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiResponse.error(extrairMensagemIntegridade(e))
            );
        } catch (IllegalArgumentException e) {
            log.warn("Erro de validação ao cadastrar custodiado: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(extrairMensagemValidacao(e))
            );
        } catch (DateTimeParseException e) {
            log.warn("Erro de formato de data ao cadastrar custodiado: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Formato de data inválido. Use o formato YYYY-MM-DD")
            );
        } catch (Exception e) {
            log.error("Erro inesperado ao cadastrar custodiado", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Erro interno ao cadastrar custodiado. Verifique os dados e tente novamente")
            );
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar custodiado",
            description = "Atualiza os dados de um custodiado existente")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Custodiado atualizado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Custodiado não encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "Conflito - CPF ou RG já cadastrado")
    })
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> update(
            @PathVariable Long id, @Valid @RequestBody CustodiadoDTO dto) {
        log.info("Atualizando custodiado ID: {}", id);

        try {
            // Validações básicas
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("ID deve ser um número positivo válido")
                );
            }

            if (dto.getNome() == null || dto.getNome().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Nome é obrigatório")
                );
            }

            if (dto.getCpf() == null || dto.getCpf().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("CPF é obrigatório")
                );
            }

            if (dto.getProcesso() == null || dto.getProcesso().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Número do processo é obrigatório")
                );
            }

            Custodiado custodiado = custodiadoService.update(id, dto);
            log.info("Custodiado atualizado com sucesso. ID: {}", custodiado.getId());

            return ResponseEntity.ok(
                    ApiResponse.success("Custodiado atualizado com sucesso",
                            CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService))
            );
        } catch (EntityNotFoundException e) {
            log.warn("Custodiado não encontrado para atualização. ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Custodiado não encontrado com ID: " + id)
            );
        } catch (DataIntegrityViolationException e) {
            log.warn("Erro de integridade ao atualizar custodiado ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiResponse.error(extrairMensagemIntegridade(e))
            );
        } catch (IllegalArgumentException e) {
            log.warn("Erro de validação ao atualizar custodiado ID {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(extrairMensagemValidacao(e))
            );
        } catch (DateTimeParseException e) {
            log.warn("Erro de formato de data ao atualizar custodiado ID {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Formato de data inválido. Use o formato YYYY-MM-DD")
            );
        } catch (Exception e) {
            log.error("Erro inesperado ao atualizar custodiado ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Erro interno ao atualizar custodiado. Verifique os dados e tente novamente")
            );
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir custodiado", description = "Remove um custodiado do sistema")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Custodiado excluído com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "ID inválido ou operação não permitida"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Custodiado não encontrado")
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        log.info("Excluindo custodiado ID: {}", id);

        try {
            if (id == null || id <= 0) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("ID deve ser um número positivo válido")
                );
            }

            custodiadoService.delete(id);
            log.info("Custodiado excluído com sucesso. ID: {}", id);

            return ResponseEntity.ok(
                    ApiResponse.success("Custodiado excluído com sucesso")
            );
        } catch (EntityNotFoundException e) {
            log.warn("Custodiado não encontrado para exclusão. ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Custodiado não encontrado com ID: " + id)
            );
        } catch (DataIntegrityViolationException e) {
            log.warn("Erro de integridade ao excluir custodiado ID {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Não é possível excluir este custodiado pois possui registros relacionados (comparecimentos, endereços, etc.)")
            );
        } catch (IllegalArgumentException e) {
            log.warn("Erro de validação ao excluir custodiado ID {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        } catch (Exception e) {
            log.error("Erro inesperado ao excluir custodiado ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Erro interno ao excluir custodiado. Tente novamente")
            );
        }
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Buscar custodiados por status",
            description = "Retorna custodiados filtrados por status de comparecimento com endereços detalhados")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Lista de custodiados retornada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Status inválido")
    })
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findByStatus(
            @Parameter(description = "Status do comparecimento") @PathVariable StatusComparecimento status) {
        log.info("Buscando custodiados por status: {}", status);

        try {
            List<Custodiado> custodiados = custodiadoService.findByStatus(status);

            List<CustodiadoResponseDTO> response = custodiados.stream()
                    .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success("Custodiados encontrados com sucesso", response)
            );
        } catch (IllegalArgumentException e) {
            // Criar lista com os códigos dos valores válidos do enum
            String valoresValidos = String.join(", ",
                    StatusComparecimento.EM_CONFORMIDADE.getCode(),
                    StatusComparecimento.INADIMPLENTE.getCode());

            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Status inválido. Use um dos valores: " + valoresValidos)
            );
        } catch (Exception e) {
            log.error("Erro ao buscar custodiados por status: {}", status, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Erro ao buscar custodiados por status. Tente novamente")
            );
        }
    }

    @GetMapping("/comparecimentos/hoje")
    @Operation(summary = "Comparecimentos de hoje",
            description = "Retorna custodiados que devem comparecer hoje com endereços detalhados")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Lista de custodiados retornada com sucesso")
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findComparecimentosHoje() {
        log.info("Buscando custodiados com comparecimento hoje");

        try {
            List<Custodiado> custodiados = custodiadoService.findComparecimentosHoje();

            List<CustodiadoResponseDTO> response = custodiados.stream()
                    .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success("Custodiados com comparecimento hoje listados com sucesso", response)
            );
        } catch (Exception e) {
            log.error("Erro ao buscar comparecimentos de hoje", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Erro ao buscar comparecimentos de hoje. Tente novamente")
            );
        }
    }

    @GetMapping("/inadimplentes")
    @Operation(summary = "Custodiados inadimplentes",
            description = "Retorna custodiados inadimplentes com endereços detalhados")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "Lista de custodiados retornada com sucesso")
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findInadimplentes() {
        log.info("Buscando custodiados inadimplentes");

        try {
            List<Custodiado> custodiados = custodiadoService.findInadimplentes();

            List<CustodiadoResponseDTO> response = custodiados.stream()
                    .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success("Custodiados inadimplentes listados com sucesso", response)
            );
        } catch (Exception e) {
            log.error("Erro ao buscar custodiados inadimplentes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Erro ao buscar custodiados inadimplentes. Tente novamente")
            );
        }
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar custodiados",
            description = "Busca custodiados por nome ou número do processo com endereços detalhados")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Lista de custodiados retornada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Termo de busca inválido")
    })
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> buscar(
            @Parameter(description = "Termo de busca") @RequestParam String termo) {
        log.info("Buscando custodiados por termo: {}", termo);

        try {
            if (termo == null || termo.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Termo de busca não pode ser vazio")
                );
            }

            if (termo.trim().length() < 2) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Termo de busca deve ter pelo menos 2 caracteres")
                );
            }

            List<Custodiado> custodiados = custodiadoService.buscarPorNomeOuProcesso(termo);

            List<CustodiadoResponseDTO> response = custodiados.stream()
                    .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success("Busca realizada com sucesso", response)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Termo de busca inválido: " + e.getMessage())
            );
        } catch (Exception e) {
            log.error("Erro ao buscar custodiados por termo: {}", termo, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Erro ao realizar busca. Tente novamente")
            );
        }
    }

    @GetMapping("/processo/{processo}/count")
    @Operation(summary = "Contar custodiados por processo",
            description = "Retorna a quantidade de custodiados em um processo específico")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Contagem retornada com sucesso")
    })
    public ResponseEntity<ApiResponse<Long>> countByProcesso(
            @Parameter(description = "Número do processo") @PathVariable String processo) {
        log.info("Contando custodiados no processo: {}", processo);

        try {
            if (processo == null || processo.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Número do processo não pode ser vazio")
                );
            }

            List<Custodiado> custodiados = custodiadoService.findByProcesso(processo);
            long count = custodiados.size();

            return ResponseEntity.ok(
                    ApiResponse.success(String.format("Processo %s tem %d custodiado(s)", processo, count), count)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Número do processo com formato inválido: " + e.getMessage())
            );
        } catch (Exception e) {
            log.error("Erro ao contar custodiados por processo: {}", processo, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.error("Erro ao contar custodiados. Tente novamente")
            );
        }
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.CustodiadoDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.model.enums.StatusCustodiado;
import br.jus.tjba.aclp.service.CustodiadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/custodiados")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600,
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@Tag(name = "Custodiados", description = "API para gerenciamento de custodiados em liberdade provisória")
@Slf4j
public class CustodiadoController {

    private final CustodiadoService custodiadoService;

    /**
     * DTO para resposta do custodiado sem referências circulares
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
        private StatusCustodiado statusCustodiado; // NOVO CAMPO
        private LocalDate ultimoComparecimento;
        private LocalDate proximoComparecimento;
        private Long diasAtraso;
        private String observacoes;

        // Dados de endereço (sem carregar relacionamentos)
        private String enderecoCompleto;
        private String enderecoResumido;
        private String cidadeEstado;

        // Metadados
        private LocalDateTime criadoEm;
        private LocalDateTime atualizadoEm;
        private String identificacao;
        private boolean inadimplente;
        private boolean comparecimentoHoje;
        private boolean ativo; // NOVO CAMPO

        /**
         * Converte entidade para DTO
         */
        public static CustodiadoResponseDTO fromEntity(Custodiado custodiado, CustodiadoService service) {
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
                    .statusCustodiado(custodiado.getStatusCustodiado()) // NOVO
                    .ultimoComparecimento(custodiado.getUltimoComparecimento())
                    .proximoComparecimento(custodiado.getProximoComparecimento())
                    .diasAtraso(custodiado.getDiasAtraso())
                    .observacoes(custodiado.getObservacoes())
                    // Buscar endereço usando service (sem lazy loading)
                    .enderecoCompleto(service.getEnderecoCompleto(custodiado.getId()))
                    .enderecoResumido(service.getEnderecoResumido(custodiado.getId()))
                    .cidadeEstado(service.getCidadeEstado(custodiado.getId()))
                    // Metadados
                    .criadoEm(custodiado.getCriadoEm())
                    .atualizadoEm(custodiado.getAtualizadoEm())
                    .identificacao(custodiado.getIdentificacao())
                    .inadimplente(custodiado.isInadimplente())
                    .comparecimentoHoje(custodiado.isComparecimentoHoje())
                    .ativo(custodiado.isAtivo()) // NOVO
                    .build();
        }
    }

    @GetMapping
    @Operation(summary = "Listar custodiados ativos",
            description = "Retorna uma lista apenas com custodiados ATIVOS")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de custodiados ativos retornada com sucesso")
    })
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findAll() {
        log.info("Listando custodiados ativos");
        List<Custodiado> custodiados = custodiadoService.findAllAtivos(); // MUDANÇA: Busca apenas ATIVOS

        List<CustodiadoResponseDTO> response = custodiados.stream()
                .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.success("Custodiados ativos listados com sucesso", response)
        );
    }

    @GetMapping("/todos")
    @Operation(summary = "Listar todos os custodiados",
            description = "Retorna uma lista com TODOS os custodiados (ATIVOS e ARQUIVADOS)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista completa de custodiados retornada com sucesso")
    })
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findAllCustodiados() {
        log.info("Listando todos os custodiados (ativos e arquivados)");
        List<Custodiado> custodiados = custodiadoService.findAll(); // Busca TODOS

        List<CustodiadoResponseDTO> response = custodiados.stream()
                .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.success("Todos os custodiados listados com sucesso", response)
        );
    }

    @GetMapping("/arquivados")
    @Operation(summary = "Listar custodiados arquivados",
            description = "Retorna uma lista apenas com custodiados ARQUIVADOS")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de custodiados arquivados retornada com sucesso")
    })
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findArquivados() {
        log.info("Listando custodiados arquivados");
        List<Custodiado> custodiados = custodiadoService.findArquivados();

        List<CustodiadoResponseDTO> response = custodiados.stream()
                .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.success("Custodiados arquivados listados com sucesso", response)
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar custodiado por ID", description = "Retorna um custodiado específico pelo seu ID")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custodiado encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Custodiado não encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID inválido")
    })
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> findById(@Parameter(description = "ID do custodiado") @PathVariable Long id) {
        log.info("Buscando custodiado por ID: {}", id);

        return custodiadoService.findById(id)
                .map(custodiado -> ResponseEntity.ok(
                        ApiResponse.success("Custodiado encontrado com sucesso",
                                CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
                ))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        ApiResponse.error("Custodiado não encontrado com ID: " + id)
                ));
    }

    @GetMapping("/processo/{processo}")
    @Operation(summary = "Buscar custodiados por processo",
            description = "Retorna todos os custodiados de um processo (pode haver múltiplos réus no mesmo processo)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custodiados encontrados"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Nenhum custodiado encontrado para este processo"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Número do processo inválido")
    })
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findByProcesso(@Parameter(description = "Número do processo") @PathVariable String processo) {
        log.info("Buscando custodiados por processo: {}", processo);

        List<Custodiado> custodiados = custodiadoService.findByProcesso(processo);

        if (custodiados.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error("Nenhum custodiado encontrado com processo: " + processo)
            );
        }

        List<CustodiadoResponseDTO> response = custodiados.stream()
                .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.success("Custodiados encontrados com sucesso", response)
        );
    }

    @PostMapping
    @Operation(summary = "Cadastrar novo custodiado", description = "Cadastra um novo custodiado no sistema (sempre como ATIVO)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Custodiado cadastrado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflito - CPF ou RG já cadastrado para custodiado ATIVO")
    })
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> save(@Valid @RequestBody CustodiadoDTO dto) {
        log.info("Cadastrando novo custodiado - Processo: {}, Nome: {}", dto.getProcesso(), dto.getNome());

        try {
            Custodiado custodiado = custodiadoService.save(dto);
            log.info("Custodiado cadastrado com sucesso. ID: {}, Status: ATIVO", custodiado.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.success("Custodiado cadastrado com sucesso",
                            CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar custodiado", description = "Atualiza os dados de um custodiado existente")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custodiado atualizado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Custodiado não encontrado"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflito - CPF ou RG já cadastrado")
    })
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> update(@PathVariable Long id, @Valid @RequestBody CustodiadoDTO dto) {
        log.info("Atualizando custodiado ID: {}", id);

        try {
            Custodiado custodiado = custodiadoService.update(id, dto);
            log.info("Custodiado atualizado com sucesso. ID: {}", custodiado.getId());
            return ResponseEntity.ok(
                    ApiResponse.success("Custodiado atualizado com sucesso",
                            CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error(e.getMessage())
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Arquivar custodiado",
            description = "Arquiva um custodiado (muda status para ARQUIVADO, não exclui do banco)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custodiado arquivado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID inválido"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Custodiado não encontrado")
    })
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        log.info("Arquivando custodiado ID: {}", id);

        try {
            custodiadoService.arquivar(id); // MUDANÇA: Chama método arquivar ao invés de delete
            log.info("Custodiado arquivado com sucesso. ID: {}", id);
            return ResponseEntity.ok(
                    ApiResponse.success("Custodiado arquivado com sucesso")
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error(e.getMessage())
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @PutMapping("/{id}/reativar")
    @Operation(summary = "Reativar custodiado arquivado",
            description = "Reativa um custodiado arquivado (muda status para ATIVO)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Custodiado reativado com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "ID inválido ou custodiado já ativo"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Custodiado não encontrado")
    })
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> reativar(@PathVariable Long id) {
        log.info("Reativando custodiado ID: {}", id);

        try {
            Custodiado custodiado = custodiadoService.reativar(id);
            log.info("Custodiado reativado com sucesso. ID: {}", id);
            return ResponseEntity.ok(
                    ApiResponse.success("Custodiado reativado com sucesso",
                            CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
            );
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.error(e.getMessage())
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Buscar custodiados por status", description = "Retorna custodiados filtrados por status de comparecimento (apenas ATIVOS)")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de custodiados retornada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Status inválido")
    })
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findByStatus(@Parameter(description = "Status do comparecimento") @PathVariable StatusComparecimento status) {
        log.info("Buscando custodiados ativos por status: {}", status);

        try {
            List<Custodiado> custodiados = custodiadoService.findByStatusAtivos(status); // MUDANÇA: Busca apenas ATIVOS

            List<CustodiadoResponseDTO> response = custodiados.stream()
                    .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success("Custodiados encontrados com sucesso", response)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @GetMapping("/comparecimentos/hoje")
    @Operation(summary = "Comparecimentos de hoje", description = "Retorna custodiados ATIVOS que devem comparecer hoje")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de custodiados retornada com sucesso")
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findComparecimentosHoje() {
        log.info("Buscando custodiados com comparecimento hoje");
        List<Custodiado> custodiados = custodiadoService.findComparecimentosHoje();

        List<CustodiadoResponseDTO> response = custodiados.stream()
                .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.success("Custodiados com comparecimento hoje listados com sucesso", response)
        );
    }

    @GetMapping("/inadimplentes")
    @Operation(summary = "Custodiados inadimplentes",
            description = "Retorna custodiados ATIVOS que estão inadimplentes (atrasados) com o comparecimento")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de custodiados retornada com sucesso")
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> findInadimplentes() {
        log.info("Buscando custodiados inadimplentes");
        List<Custodiado> custodiados = custodiadoService.findInadimplentes();

        List<CustodiadoResponseDTO> response = custodiados.stream()
                .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.success("Custodiados inadimplentes listados com sucesso", response)
        );
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar custodiados", description = "Busca custodiados ATIVOS por nome ou número do processo")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lista de custodiados retornada com sucesso"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Termo de busca inválido")
    })
    public ResponseEntity<ApiResponse<List<CustodiadoResponseDTO>>> buscar(@Parameter(description = "Termo de busca") @RequestParam String termo) {
        log.info("Buscando custodiados ativos por termo: {}", termo);

        try {
            List<Custodiado> custodiados = custodiadoService.buscarPorNomeOuProcessoAtivos(termo); // MUDANÇA: Busca apenas ATIVOS

            List<CustodiadoResponseDTO> response = custodiados.stream()
                    .map(custodiado -> CustodiadoResponseDTO.fromEntity(custodiado, custodiadoService))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(
                    ApiResponse.success("Busca realizada com sucesso", response)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(e.getMessage())
            );
        }
    }

    @GetMapping("/processo/{processo}/count")
    @Operation(summary = "Contar custodiados por processo",
            description = "Retorna a quantidade de custodiados ATIVOS em um processo específico")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Contagem retornada com sucesso")
    })
    public ResponseEntity<ApiResponse<Long>> countByProcesso(@Parameter(description = "Número do processo") @PathVariable String processo) {
        log.info("Contando custodiados ativos no processo: {}", processo);

        List<Custodiado> custodiados = custodiadoService.findByProcessoAtivos(processo); // MUDANÇA: Conta apenas ATIVOS
        long count = custodiados.size();

        return ResponseEntity.ok(
                ApiResponse.success(String.format("Processo %s tem %d custodiado(s) ativo(s)", processo, count), count)
        );
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
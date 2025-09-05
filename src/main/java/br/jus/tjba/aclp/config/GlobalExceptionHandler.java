package br.jus.tjba.aclp.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // === CLASSE DE RESPOSTA INTERNA ===
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorResponse {
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime timestamp;
        private Integer status;
        private String error;
        private String code;
        private String message;
        private String details;
        private String path;
        private Map<String, String> fieldErrors;
        private List<String> suggestions;
    }

    // === EXCEÇÕES DE NEGÓCIO ===

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Argumento inválido: {}", ex.getMessage());

        String message = ex.getMessage();
        String code = "VALIDATION_ERROR";
        List<String> suggestions = new ArrayList<>();

        // Personalizar baseado no tipo de erro
        if (message.contains("CPF") && message.contains("já")) {
            code = "CPF_DUPLICADO";
            suggestions.add("Verifique se o CPF foi digitado corretamente");
            suggestions.add("Consulte se esta pessoa já está cadastrada");
        } else if (message.contains("RG") && message.contains("já")) {
            code = "RG_DUPLICADO";
            suggestions.add("Verifique se o RG foi digitado corretamente");
            suggestions.add("Consulte se esta pessoa já está cadastrada");
        } else if (message.contains("Email") && message.contains("uso")) {
            code = "EMAIL_DUPLICADO";
            suggestions.add("Use um email diferente");
            suggestions.add("Verifique se você já possui conta com este email");
        } else if (message.contains("não encontrad")) {
            code = "RECURSO_NAO_ENCONTRADO";
            suggestions.add("Verifique se o ID está correto");
            suggestions.add("Use o endpoint de busca para localizar o recurso");
        } else if (message.contains("ID deve ser")) {
            code = "ID_INVALIDO";
            suggestions.add("Use um número inteiro positivo");
            suggestions.add("Verifique se o ID existe no sistema");
        } else if (message.contains("obrigatório")) {
            code = "CAMPO_OBRIGATORIO";
            suggestions.add("Preencha todos os campos obrigatórios");
            suggestions.add("Consulte a documentação da API");
        } else if (message.contains("formato") || message.contains("válido")) {
            code = "FORMATO_INVALIDO";
            suggestions.add("Verifique o formato dos dados");
            suggestions.add("Consulte exemplos na documentação");
        } else if (message.contains("não é possível excluir")) {
            code = "OPERACAO_NAO_PERMITIDA";
            suggestions.add("Remova as dependências primeiro");
            suggestions.add("Consulte a documentação sobre exclusões");
        } else if (message.contains("termo") || message.contains("caracteres")) {
            code = "TERMO_BUSCA_INVALIDO";
            suggestions.add("Use pelo menos 2 caracteres na busca");
            suggestions.add("Tente termos mais específicos");
        } else if (message.contains("Status") || message.contains("status")) {
            code = "STATUS_INVALIDO";
            suggestions.add("Use: EM_CONFORMIDADE ou INADIMPLENTE");
            suggestions.add("Verifique se o status foi escrito corretamente");
        }
        // === NOVOS TRATAMENTOS PARA ENDEREÇO ===
        else if (message.contains("Estado") && message.contains("inválido")) {
            code = "ESTADO_INVALIDO";
            suggestions.add("Use uma sigla válida de estado brasileiro (ex: BA, SP, RJ)");
            suggestions.add("Estados válidos: AC, AL, AP, AM, BA, CE, DF, ES, GO, MA, MT, MS, MG, PA, PB, PR, PE, PI, RJ, RN, RS, RO, RR, SC, SP, SE, TO");
            suggestions.add("Verifique se a sigla foi escrita corretamente");
        } else if (message.contains("CEP") && (message.contains("formato") || message.contains("inválido"))) {
            code = "CEP_INVALIDO";
            suggestions.add("Use o formato 00000-000 ou apenas números");
            suggestions.add("Verifique se o CEP possui 8 dígitos");
            suggestions.add("Exemplo: 40070-110 ou 40070110");
        } else if (message.contains("endereço") && message.contains("obrigatório")) {
            code = "ENDERECO_OBRIGATORIO";
            suggestions.add("Preencha todos os campos obrigatórios do endereço");
            suggestions.add("Campos obrigatórios: CEP, logradouro, bairro, cidade e estado");
            suggestions.add("O número é opcional, mas recomendado");
        } else if (message.contains("logradouro") || message.contains("Logradouro")) {
            code = "LOGRADOURO_INVALIDO";
            suggestions.add("O logradouro deve ter entre 5 e 200 caracteres");
            suggestions.add("Informe o nome completo da rua/avenida");
            suggestions.add("Exemplo: Avenida Sete de Setembro");
        } else if (message.contains("bairro") || message.contains("Bairro")) {
            code = "BAIRRO_INVALIDO";
            suggestions.add("O bairro deve ter entre 2 e 100 caracteres");
            suggestions.add("Informe o nome completo do bairro");
            suggestions.add("Exemplo: Centro, Pituba, Barra");
        } else if (message.contains("cidade") || message.contains("Cidade")) {
            code = "CIDADE_INVALIDA";
            suggestions.add("A cidade deve ter entre 2 e 100 caracteres");
            suggestions.add("Informe o nome completo da cidade");
            suggestions.add("Exemplo: Salvador, São Paulo, Rio de Janeiro");
        } else {
            suggestions.add("Verifique os dados informados");
            suggestions.add("Consulte a documentação da API");
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .code(code)
                .message(message)
                .path(request.getRequestURI())
                .suggestions(suggestions)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    // === EXCEÇÕES DE VALIDAÇÃO ===

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Erros de validação: {}", ex.getMessage());

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String field = error.getField();
            String message = error.getDefaultMessage();
            fieldErrors.put(field, message != null ? message : "Campo inválido");
        });

        List<String> suggestions = new ArrayList<>();

        // Sugestões específicas baseadas nos campos com erro
        if (fieldErrors.containsKey("cep")) {
            suggestions.add("CEP deve ter o formato 00000-000");
        }
        if (fieldErrors.containsKey("estado")) {
            suggestions.add("Estado deve ser uma sigla válida (ex: BA, SP, RJ)");
        }
        if (fieldErrors.containsKey("logradouro")) {
            suggestions.add("Logradouro deve ter entre 5 e 200 caracteres");
        }
        if (fieldErrors.containsKey("bairro")) {
            suggestions.add("Bairro deve ter entre 2 e 100 caracteres");
        }
        if (fieldErrors.containsKey("cidade")) {
            suggestions.add("Cidade deve ter entre 2 e 100 caracteres");
        }
        if (fieldErrors.containsKey("cpf")) {
            suggestions.add("CPF deve ter o formato 000.000.000-00");
        }
        if (fieldErrors.containsKey("processo")) {
            suggestions.add("Processo deve ter o formato 0000000-00.0000.0.00.0000");
        }
        if (fieldErrors.containsKey("contato")) {
            suggestions.add("Contato deve ter formato válido de telefone");
        }

        // Sugestões gerais
        suggestions.add("Corrija os campos destacados");
        suggestions.add("Verifique os formatos esperados");
        suggestions.add("Campos obrigatórios não podem ficar vazios");

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .code("CAMPOS_INVALIDOS")
                .message(String.format("Foram encontrados erros em %d campo(s)", fieldErrors.size()))
                .details("Corrija os campos destacados e tente novamente")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .suggestions(suggestions)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Violação de constraints: {}", ex.getMessage());

        String message = "Dados não atendem às regras de validação";
        StringBuilder details = new StringBuilder();
        List<String> suggestions = new ArrayList<>();

        ex.getConstraintViolations().forEach(violation -> {
            if (details.length() > 0) details.append("; ");
            details.append(violation.getPropertyPath()).append(": ").append(violation.getMessage());

            // Adicionar sugestões específicas baseadas na violação
            String violationMessage = violation.getMessage().toLowerCase();
            if (violationMessage.contains("estado")) {
                suggestions.add("Use uma sigla válida de estado brasileiro");
            } else if (violationMessage.contains("cep")) {
                suggestions.add("Use o formato 00000-000 para o CEP");
            }
        });

        if (suggestions.isEmpty()) {
            suggestions.add("Corrija os dados conforme as regras especificadas");
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Constraint Violation")
                .code("REGRAS_VIOLADAS")
                .message(message)
                .details(details.toString())
                .path(request.getRequestURI())
                .suggestions(suggestions)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    // === EXCEÇÕES DE PARÂMETROS ===

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Tipo de parâmetro inválido: {} = {}", ex.getName(), ex.getValue());

        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconhecido";
        String message = String.format("Parâmetro '%s' deve ser do tipo %s", ex.getName(), expectedType);

        List<String> suggestions = new ArrayList<>();

        // Sugestões específicas por tipo
        if ("Long".equals(expectedType) || "Integer".equals(expectedType)) {
            suggestions.add("Use apenas números inteiros (exemplo: 123)");
        } else if ("StatusComparecimento".equals(expectedType)) {
            suggestions.add("Use: EM_CONFORMIDADE ou INADIMPLENTE");
        } else if ("TipoUsuario".equals(expectedType)) {
            suggestions.add("Use: ADMIN ou USUARIO");
        } else if ("EstadoBrasil".equals(expectedType)) {
            suggestions.add("Use uma sigla válida de estado brasileiro (ex: BA, SP, RJ)");
            suggestions.add("Estados válidos: AC, AL, AP, AM, BA, CE, DF, ES, GO, MA, MT, MS, MG, PA, PB, PR, PE, PI, RJ, RN, RS, RO, RR, SC, SP, SE, TO");
        } else {
            suggestions.add(String.format("Use um valor válido do tipo %s", expectedType));
        }
        suggestions.add("Consulte a documentação da API para exemplos");

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid Parameter Type")
                .code("TIPO_PARAMETRO_INVALIDO")
                .message(message)
                .details(String.format("Valor fornecido: '%s'", ex.getValue()))
                .path(request.getRequestURI())
                .suggestions(suggestions)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Parâmetro obrigatório ausente: {}", ex.getParameterName());

        String message = String.format("Parâmetro obrigatório '%s' está ausente", ex.getParameterName());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Missing Parameter")
                .code("PARAMETRO_AUSENTE")
                .message(message)
                .details(String.format("Tipo esperado: %s", ex.getParameterType()))
                .path(request.getRequestURI())
                .suggestions(Arrays.asList(
                        String.format("Adicione o parâmetro '%s' à requisição", ex.getParameterName()),
                        "Verifique a documentação da API"
                ))
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("JSON malformado: {}", ex.getMessage());

        String message = "JSON da requisição está malformado";
        String details = "Verifique a sintaxe do JSON";
        List<String> suggestions = new ArrayList<>();

        // Personalizar mensagem baseada no erro
        String exMessage = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";

        if (exMessage.contains("jsonparseexception")) {
            details = "Erro na sintaxe do JSON (chaves, vírgulas, aspas)";
            suggestions.add("Valide a sintaxe do JSON");
            suggestions.add("Verifique se todas as chaves e aspas estão corretas");
        } else if (exMessage.contains("jsonmappingexception") || exMessage.contains("enum")) {
            details = "Valor inválido para campo enum";
            suggestions.add("Para status use: EM_CONFORMIDADE ou INADIMPLENTE");
            suggestions.add("Para tipo de usuário use: ADMIN ou USUARIO");
            suggestions.add("Para estado use siglas válidas: BA, SP, RJ, etc.");
        } else if (exMessage.contains("localdatetime") || exMessage.contains("localdate")) {
            details = "Formato de data inválido";
            suggestions.add("Use o formato: yyyy-MM-dd para datas");
            suggestions.add("Use o formato: yyyy-MM-ddTHH:mm:ss para data/hora");
        } else if (exMessage.contains("estado") || exMessage.contains("cep") || exMessage.contains("endereco")) {
            details = "Erro nos dados de endereço";
            suggestions.add("Verifique se o estado é uma sigla válida (ex: BA)");
            suggestions.add("Verifique se o CEP tem o formato 00000-000");
            suggestions.add("Certifique-se de que todos os campos obrigatórios do endereço estão preenchidos");
        } else {
            suggestions.add("Use um validador JSON online");
            suggestions.add("Verifique se os tipos de dados estão corretos");
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Malformed JSON")
                .code("JSON_INVALIDO")
                .message(message)
                .details(details)
                .path(request.getRequestURI())
                .suggestions(suggestions)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

// === EXCEÇÕES DE RECURSOS ===

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Endpoint não encontrado: {}", request.getRequestURI());

        String message = "Endpoint não encontrado";
        String details = String.format("A URL '%s' não existe neste servidor", request.getRequestURI());

        List<String> suggestions = Arrays.asList(
                "Verifique se a URL está correta",
                "Consulte /swagger-ui.html para ver endpoints disponíveis",
                "Verifique se está usando o método HTTP correto"
        );

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .code("ENDPOINT_NAO_ENCONTRADO")
                .message(message)
                .details(details)
                .path(request.getRequestURI())
                .suggestions(suggestions)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("Entidade não encontrada: {}", ex.getMessage());

        String message = ex.getMessage();
        String code = "RECURSO_NAO_ENCONTRADO";
        List<String> suggestions = new ArrayList<>();

        // Personalizar sugestões baseado no tipo de entidade
        if (message.toLowerCase().contains("pessoa")) {
            code = "PESSOA_NAO_ENCONTRADA";
            suggestions.add("Verifique se o ID da pessoa está correto");
            suggestions.add("Use o endpoint de busca para localizar pessoas");
        } else if (message.toLowerCase().contains("usuário")) {
            code = "USUARIO_NAO_ENCONTRADO";
            suggestions.add("Verifique se o ID do usuário está correto");
            suggestions.add("Use o endpoint de busca para localizar usuários");
        } else if (message.toLowerCase().contains("endereço")) {
            code = "ENDERECO_NAO_ENCONTRADO";
            suggestions.add("Verifique se o endereço existe");
            suggestions.add("Tente buscar pelo CEP primeiro");
        } else {
            suggestions.add("Verifique se o ID está correto");
            suggestions.add("Use o endpoint de busca para localizar o recurso");
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .code(code)
                .message(message)
                .details("O recurso solicitado não existe no sistema")
                .path(request.getRequestURI())
                .suggestions(suggestions)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // === EXCEÇÕES DE INTEGRIDADE ===

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Erro de integridade: {}", ex.getMessage());

        String message = "Erro de integridade dos dados";
        String details = "Violação de restrições do banco de dados";
        String code = "INTEGRIDADE_VIOLADA";
        List<String> suggestions = new ArrayList<>();

        String rootCause = ex.getRootCause() != null ? ex.getRootCause().getMessage().toLowerCase() : "";
        String fullMessage = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";

        // Verificar erros de NOT NULL constraints
        if (rootCause.contains("not-null") || rootCause.contains("null value") ||
                rootCause.contains("viola a restrição de não-nulo") || rootCause.contains("violates not-null constraint")) {

            message = "Campo obrigatório não foi preenchido corretamente";
            details = "Um campo obrigatório do sistema não foi definido";
            code = "CAMPO_OBRIGATORIO_NULO";
            suggestions.add("Este é um erro interno do sistema");
            suggestions.add("Contate o suporte técnico informando esta mensagem");
            suggestions.add("Tente novamente em alguns instantes");

            // Log adicional para debug
            log.error("Erro de NOT NULL constraint detectado. Causa raiz: {}", rootCause);

        } else if (rootCause.contains("cpf") || rootCause.contains("uk_pessoa_cpf")) {
            message = "CPF já está cadastrado no sistema";
            details = "Não é possível cadastrar duas pessoas com o mesmo CPF";
            code = "CPF_DUPLICADO";
            suggestions.add("Use um CPF diferente");
            suggestions.add("Verifique se a pessoa já está cadastrada");
        } else if (rootCause.contains("rg") || rootCause.contains("uk_pessoa_rg")) {
            message = "RG já está cadastrado no sistema";
            details = "Não é possível cadastrar duas pessoas com o mesmo RG";
            code = "RG_DUPLICADO";
            suggestions.add("Use um RG diferente");
            suggestions.add("Verifique se a pessoa já está cadastrada");
        } else if (rootCause.contains("processo") || rootCause.contains("uk_pessoa_processo")) {
            message = "Processo já está cadastrado no sistema";
            details = "Não é possível cadastrar duas pessoas com o mesmo número de processo";
            code = "PROCESSO_DUPLICADO";
            suggestions.add("Verifique se o processo foi digitado corretamente");
            suggestions.add("Consulte se este processo já está cadastrado");
        } else if (rootCause.contains("email")) {
            message = "Email já está em uso";
            details = "Cada usuário deve ter um email único";
            code = "EMAIL_DUPLICADO";
            suggestions.add("Use um email diferente");
            suggestions.add("Verifique se você já possui conta com este email");
        } else if (rootCause.contains("foreign key") || rootCause.contains("violates foreign key constraint")) {
            message = "Operação não permitida";
            details = "Existem registros dependentes que impedem esta operação";
            code = "OPERACAO_BLOQUEADA";
            suggestions.add("Remova as dependências primeiro");
            suggestions.add("Verifique se há registros relacionados");
        }
        // === NOVOS TRATAMENTOS PARA ENDEREÇO ===
        else if (rootCause.contains("endereco") || rootCause.contains("address")) {
            message = "Erro de integridade no endereço";
            details = "Violação de restrições relacionadas ao endereço";
            code = "ENDERECO_INTEGRIDADE_VIOLADA";
            suggestions.add("Verifique se todos os campos obrigatórios do endereço estão preenchidos");
            suggestions.add("Certifique-se de que o estado é uma sigla válida");
            suggestions.add("Verifique se o CEP tem formato correto");
        } else if (rootCause.contains("cep")) {
            message = "Erro relacionado ao CEP";
            details = "CEP pode estar duplicado ou em formato inválido";
            code = "CEP_INTEGRIDADE_VIOLADA";
            suggestions.add("Verifique se o CEP tem o formato 00000-000");
            suggestions.add("Certifique-se de que o CEP é válido");
        } else {
            // Erro genérico de integridade
            suggestions.add("Verifique se não há dados duplicados");
            suggestions.add("Consulte a documentação da API");
            suggestions.add("Se o erro persistir, contate o suporte");

            // Log para debug de erros não mapeados
            log.warn("Erro de integridade não mapeado. Causa raiz: {}", rootCause);
        }

        // Determinar status HTTP baseado no tipo de erro
        HttpStatus httpStatus = code.equals("CAMPO_OBRIGATORIO_NULO") ?
                HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.CONFLICT;

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(httpStatus.value())
                .error(httpStatus.getReasonPhrase())
                .code(code)
                .message(message)
                .details(details)
                .path(request.getRequestURI())
                .suggestions(suggestions)
                .build();

        return ResponseEntity.status(httpStatus).body(error);
    }

    // === EXCEÇÃO GENÉRICA ===

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Erro não tratado: ", ex);

        // Não expor detalhes internos em produção
        String message = "Ocorreu um erro interno no servidor";
        String details = "Nossa equipe técnica foi notificada";

        // Em desenvolvimento, pode mostrar mais detalhes
        if (isDevEnvironment()) {
            details = ex.getMessage() != null ? ex.getMessage() : "Erro interno não identificado";
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .code("ERRO_INTERNO")
                .message(message)
                .details(details)
                .path(request.getRequestURI())
                .suggestions(Arrays.asList(
                        "Tente novamente em alguns instantes",
                        "Se o problema persistir, contate o suporte",
                        "Verifique se todos os dados estão corretos"
                ))
                .build();

        return ResponseEntity.internalServerError().body(error);
    }

    // === MÉTODOS UTILITÁRIOS ===

    private boolean isDevEnvironment() {
        // Pode verificar profiles ativos ou variáveis de ambiente
        return true; // Para desenvolvimento - ajuste conforme necessário
    }
}
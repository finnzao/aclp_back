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
import org.springframework.validation.FieldError;
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

        List<String> suggestions = Arrays.asList(
                "Corrija os campos destacados",
                "Verifique os formatos esperados",
                "Campos obrigatórios não podem ficar vazios"
        );

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

        ex.getConstraintViolations().forEach(violation -> {
            if (details.length() > 0) details.append("; ");
            details.append(violation.getPropertyPath()).append(": ").append(violation.getMessage());
        });

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Constraint Violation")
                .code("REGRAS_VIOLADAS")
                .message(message)
                .details(details.toString())
                .path(request.getRequestURI())
                .suggestions(Arrays.asList("Corrija os dados conforme as regras especificadas"))
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

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .code("RECURSO_NAO_ENCONTRADO")
                .message(ex.getMessage())
                .details("O recurso solicitado não existe no sistema")
                .path(request.getRequestURI())
                .suggestions(Arrays.asList(
                        "Verifique se o ID está correto",
                        "Use o endpoint de busca para localizar o recurso"
                ))
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

        if (rootCause.contains("cpf") || rootCause.contains("uk_pessoa_cpf")) {
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
        } else if (rootCause.contains("email")) {
            message = "Email já está em uso";
            details = "Cada usuário deve ter um email único";
            code = "EMAIL_DUPLICADO";
            suggestions.add("Use um email diferente");
        } else if (rootCause.contains("foreign key")) {
            message = "Operação não permitida";
            details = "Existem registros dependentes";
            code = "OPERACAO_BLOQUEADA";
            suggestions.add("Remova as dependências primeiro");
        } else {
            suggestions.add("Verifique se não há dados duplicados");
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .code(code)
                .message(message)
                .details(details)
                .path(request.getRequestURI())
                .suggestions(suggestions)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
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
            details = ex.getMessage();
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
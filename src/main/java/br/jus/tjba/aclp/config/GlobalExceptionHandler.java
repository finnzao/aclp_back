package br.jus.tjba.aclp.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        } else if (message.contains("Tipo de validação inválido")) {
            code = "TIPO_VALIDACAO_INVALIDO";
            suggestions.add("Use valores em minúsculas: presencial, online ou cadastro_inicial");
            suggestions.add("Não use valores em maiúsculas como PRESENCIAL ou ONLINE");
            suggestions.add("Verifique a ortografia do tipo de validação");
        } else if (message.contains("Status") || message.contains("status")) {
            code = "STATUS_INVALIDO";
            suggestions.add("Use: EM_CONFORMIDADE ou INADIMPLENTE");
            suggestions.add("Verifique se o status foi escrito corretamente");
        } else if (message.contains("Estado") && message.contains("inválido")) {
            code = "ESTADO_INVALIDO";
            suggestions.add("Use uma sigla válida de estado brasileiro (ex: BA, SP, RJ)");
            suggestions.add("Estados válidos: AC, AL, AP, AM, BA, CE, DF, ES, GO, MA, MT, MS, MG, PA, PB, PR, PE, PI, RJ, RN, RS, RO, RR, SC, SP, SE, TO");
            suggestions.add("Verifique se a sigla foi escrita corretamente em MAIÚSCULAS");
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("JSON malformado: {}", ex.getMessage());

        String message = "JSON da requisição está malformado";
        String details = "Verifique a sintaxe do JSON";
        String code = "JSON_INVALIDO";
        List<String> suggestions = new ArrayList<>();

        // Personalizar mensagem baseada no erro
        String exMessage = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        String rootCause = ex.getRootCause() != null ? ex.getRootCause().getMessage() : "";

        if (exMessage.contains("jsonparseexception")) {
            details = "Erro na sintaxe do JSON (chaves, vírgulas, aspas)";
            suggestions.add("Valide a sintaxe do JSON");
            suggestions.add("Verifique se todas as chaves e aspas estão corretas");
        } else if (exMessage.contains("tipovalidacao") || rootCause.contains("TipoValidacao")) {
            code = "TIPO_VALIDACAO_INVALIDO";
            details = "Valor inválido para tipo de validação";
            suggestions.add("Para tipo de validação use (em minúsculas): presencial, online ou cadastro_inicial");
            suggestions.add("NÃO use valores em maiúsculas como PRESENCIAL ou ONLINE");
            suggestions.add("Exemplo correto: \"tipoValidacao\": \"presencial\"");
        } else if (exMessage.contains("statuscomparecimento") || rootCause.contains("StatusComparecimento")) {
            code = "STATUS_INVALIDO";
            details = "Valor inválido para status de comparecimento";
            suggestions.add("Para status use: EM_CONFORMIDADE ou INADIMPLENTE");
            suggestions.add("Use exatamente como mostrado (em maiúsculas com underscore)");
        } else if (exMessage.contains("tipousuario") || rootCause.contains("TipoUsuario")) {
            code = "TIPO_USUARIO_INVALIDO";
            details = "Valor inválido para tipo de usuário";
            suggestions.add("Para tipo de usuário use: ADMIN ou USUARIO");
            suggestions.add("Use exatamente como mostrado (em maiúsculas)");
        } else if (exMessage.contains("estadobrasil") || exMessage.contains("estado") || rootCause.contains("EstadoBrasil")) {
            code = "ESTADO_INVALIDO";
            details = "Valor inválido para estado";
            suggestions.add("Use siglas válidas de estados brasileiros em MAIÚSCULAS");
            suggestions.add("Exemplos: BA, SP, RJ, MG, RS");
            suggestions.add("Estados válidos: AC, AL, AP, AM, BA, CE, DF, ES, GO, MA, MT, MS, MG, PA, PB, PR, PE, PI, RJ, RN, RS, RO, RR, SC, SP, SE, TO");
        } else if (exMessage.contains("jsonmappingexception") || exMessage.contains("enum")) {
            details = "Valor inválido para campo enum";
            suggestions.add("Para tipo de validação use: presencial, online ou cadastro_inicial (minúsculas)");
            suggestions.add("Para status use: EM_CONFORMIDADE ou INADIMPLENTE (maiúsculas)");
            suggestions.add("Para tipo de usuário use: ADMIN ou USUARIO (maiúsculas)");
            suggestions.add("Para estado use siglas válidas: BA, SP, RJ, etc. (maiúsculas)");
        } else if (exMessage.contains("localdatetime") || exMessage.contains("localdate")) {
            details = "Formato de data inválido";
            suggestions.add("Use o formato: yyyy-MM-dd para datas (ex: 2025-01-15)");
            suggestions.add("Use o formato: yyyy-MM-ddTHH:mm:ss para data/hora");
            suggestions.add("Para horaComparecimento use: HH:mm:ss (ex: 14:30:00)");
        } else if (exMessage.contains("localtime")) {
            details = "Formato de hora inválido";
            suggestions.add("Use o formato: HH:mm:ss para hora (ex: 14:30:00)");
            suggestions.add("Ou use formato de string: \"14:30:00\"");
        } else {
            suggestions.add("Use um validador JSON online");
            suggestions.add("Verifique se os tipos de dados estão corretos");
            suggestions.add("Verifique os valores dos campos enum");
        }

        // Log adicional para debug
        if (log.isDebugEnabled()) {
            log.debug("Causa raiz do erro JSON: {}", rootCause);
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Malformed JSON")
                .code(code)
                .message(message)
                .details(details)
                .path(request.getRequestURI())
                .suggestions(suggestions)
                .build();

        return ResponseEntity.badRequest().body(error);
    }

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
        if (fieldErrors.containsKey("tipoValidacao")) {
            suggestions.add("Tipo de validação deve ser: presencial, online ou cadastro_inicial (minúsculas)");
        }
        if (fieldErrors.containsKey("estado")) {
            suggestions.add("Estado deve ser uma sigla válida em MAIÚSCULAS (ex: BA, SP, RJ)");
        }
        if (fieldErrors.containsKey("dataComparecimento")) {
            suggestions.add("Data deve estar no formato: yyyy-MM-dd (ex: 2025-01-15)");
        }
        if (fieldErrors.containsKey("horaComparecimento")) {
            suggestions.add("Hora deve estar no formato: HH:mm:ss (ex: 14:30:00)");
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

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("Entidade não encontrada: {}", ex.getMessage());

        String message = ex.getMessage();
        String code = "RECURSO_NAO_ENCONTRADO";
        List<String> suggestions = new ArrayList<>();

        // Personalizar sugestões baseado no tipo de entidade
        if (message.toLowerCase().contains("custodiado")) {
            code = "CUSTODIADO_NAO_ENCONTRADO";
            suggestions.add("Verifique se o ID do custodiado está correto");
            suggestions.add("Use o endpoint GET /api/custodiados para listar custodiados disponíveis");
            suggestions.add("Certifique-se de que o custodiado existe antes de registrar comparecimento");
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Erro não tratado: ", ex);

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

    private boolean isDevEnvironment() {
        String profile = System.getProperty("spring.profiles.active", "dev");
        return profile.contains("dev") || profile.contains("test");
    }
}
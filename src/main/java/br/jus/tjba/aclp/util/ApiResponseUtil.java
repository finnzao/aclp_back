package br.jus.tjba.aclp.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Classe utilitária para criar responses padronizadas na API
 */
public class ApiResponseUtil {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * Cria response de sucesso com dados
     */
    public static <T> ResponseEntity<Map<String, Object>> success(T data, String message) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data,
                "message", message,
                "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        ));
    }

    /**
     * Cria response de sucesso sem dados
     */
    public static ResponseEntity<Map<String, Object>> success(String message) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message,
                "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        ));
    }

    /**
     * Cria response de erro com status HTTP personalizado
     */
    public static ResponseEntity<Map<String, Object>> error(HttpStatus status, String error) {
        return ResponseEntity.status(status)
                .body(Map.of(
                        "success", false,
                        "error", error,
                        "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
                ));
    }

    /**
     * Cria response de erro bad request (400)
     */
    public static ResponseEntity<Map<String, Object>> badRequest(String error) {
        return error(HttpStatus.BAD_REQUEST, error);
    }

    /**
     * Cria response de erro not found (404)
     */
    public static ResponseEntity<Map<String, Object>> notFound(String error) {
        return error(HttpStatus.NOT_FOUND, error);
    }

    /**
     * Cria response de erro interno (500)
     */
    public static ResponseEntity<Map<String, Object>> internalServerError(String error) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, error);
    }

    /**
     * Cria response de erro com dados adicionais
     */
    public static <T> ResponseEntity<Map<String, Object>> errorWithData(HttpStatus status, String error, T data) {
        return ResponseEntity.status(status)
                .body(Map.of(
                        "success", false,
                        "error", error,
                        "data", data,
                        "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
                ));
    }

    /**
     * Cria response de sucesso para operações de criação (201)
     */
    public static <T> ResponseEntity<Map<String, Object>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "success", true,
                        "data", data,
                        "message", message,
                        "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
                ));
    }

    /**
     * Cria response de sucesso para operações de exclusão (204 com body)
     */
    public static ResponseEntity<Map<String, Object>> deleted(String message) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message,
                "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        ));
    }
}
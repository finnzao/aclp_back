package br.jus.tjba.aclp.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class ApiResponseUtil {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static <T> ResponseEntity<Map<String, Object>> success(T data, String message) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data,
                "message", message,
                "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        ));
    }

    public static ResponseEntity<Map<String, Object>> success(String message) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message,
                "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        ));
    }

    public static ResponseEntity<Map<String, Object>> error(HttpStatus status, String error) {
        return ResponseEntity.status(status)
                .body(Map.of(
                        "success", false,
                        "error", error,
                        "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
                ));
    }

    public static ResponseEntity<Map<String, Object>> badRequest(String error) {
        return error(HttpStatus.BAD_REQUEST, error);
    }

    public static ResponseEntity<Map<String, Object>> notFound(String error) {
        return error(HttpStatus.NOT_FOUND, error);
    }

    public static ResponseEntity<Map<String, Object>> internalServerError(String error) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, error);
    }

    public static <T> ResponseEntity<Map<String, Object>> errorWithData(HttpStatus status, String error, T data) {
        return ResponseEntity.status(status)
                .body(Map.of(
                        "success", false,
                        "error", error,
                        "data", data,
                        "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
                ));
    }

    public static <T> ResponseEntity<Map<String, Object>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "success", true,
                        "data", data,
                        "message", message,
                        "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
                ));
    }

    public static ResponseEntity<Map<String, Object>> deleted(String message) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message,
                "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER)
        ));
    }
}
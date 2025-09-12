package br.jus.tjba.aclp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO padronizado para todas as respostas da API
 * @param <T> Tipo do objeto de dados retornado
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    /**
     * Cria uma resposta de sucesso
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Cria uma resposta de sucesso sem dados
     */
    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .data(null)
                .build();
    }

    /**
     * Cria uma resposta de erro
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .build();
    }

    /**
     * Cria uma resposta de erro com dados adicionais
     */
    public static <T> ApiResponse<T> error(String message, T errorData) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(errorData)
                .build();
    }
}
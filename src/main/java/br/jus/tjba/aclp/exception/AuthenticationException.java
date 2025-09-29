package br.jus.tjba.aclp.exception;

/**
 * Exceção para erros de autenticação
 * Lançada quando credenciais são inválidas ou autenticação falha
 */
public class AuthenticationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
package br.jus.tjba.aclp.exception;

/**
 * Exceção lançada quando um token JWT é inválido
 *
 * Essa exceção é disparada quando:
 * - Token JWT está expirado
 * - Token JWT está malformado
 * - Token JWT foi revogado (blacklist)
 * - Token JWT tem assinatura inválida
 * - Refresh token está expirado ou já foi usado
 *
 * @author Sistema ACLP - TJBA
 */
public class InvalidTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Construtor padrão sem argumentos
     */
    public InvalidTokenException() {
        super("Token inválido");
    }

    /**
     * Construtor com mensagem
     * @param message mensagem descritiva do erro
     */
    public InvalidTokenException(String message) {
        super(message);
    }

    /**
     * Construtor com mensagem e causa
     * @param message mensagem descritiva do erro
     * @param cause causa raiz da exceção
     */
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Construtor apenas com causa
     * @param cause causa raiz da exceção
     */
    public InvalidTokenException(Throwable cause) {
        super(cause);
    }
}
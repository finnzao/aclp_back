package br.jus.tjba.aclp.exception;

/**
 * Exceção lançada quando uma conta de usuário está bloqueada
 *
 * Essa exceção é disparada quando:
 * - Usuário excede número máximo de tentativas de login
 * - Conta foi bloqueada administrativamente
 * - Conta está suspensa temporariamente
 *
 * @author Sistema ACLP - TJBA
 */
public class AccountLockedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Construtor com mensagem
     * @param message mensagem descritiva do erro
     */
    public AccountLockedException(String message) {
        super(message);
    }

    /**
     * Construtor com mensagem e causa
     * @param message mensagem descritiva do erro
     * @param cause causa raiz da exceção
     */
    public AccountLockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
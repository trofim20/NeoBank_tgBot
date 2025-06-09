package org.example.bot.handler.exception;

/**
 * Исключение, выбрасываемое при истечении срока действия токена.
 */
public class TokenExpiredException extends RuntimeException {

    /**
     * Создает исключение с сообщением.
     *
     * @param message сообщение об ошибке.
     */
    public TokenExpiredException(String message) {
        super(message);
    }
}

package org.example.bot.handler.exception;

/**
 * Исключение, выбрасываемое при невалидной сумме.
 */
public class AmountValidationException extends Exception {

    /**
     * Создает исключение с сообщением.
     *
     * @param message сообщение об ошибке.
     */
    public AmountValidationException(String message) {
        super(message);
    }
}

package org.example.bot.fiegn;

import org.springframework.cloud.openfeign.FeignClient;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Feign клиент для взаимодействия с Telegram API NeoFlex.
 */
@FeignClient(
        name = "neoflex-telegram-api",
        url = "${feign.bff-api.url}",
        configuration = FeignConfiguration.class
)
public interface NeoFlexTelegramAPI {

    /**
     * Получает временный токен.
     *
     * @param chatId ID чата.
     * @param chatType тип чата.
     * @param userId ID пользователя.
     * @param firstName имя пользователя.
     * @param lastName фамилия пользователя.
     * @param username имя пользователя в Telegram.
     * @param botUsername имя бота.
     * @param botId ID бота.
     * @param hash хэш для проверки.
     * @param authDate дата авторизации.
     * @return ResponseEntity с временным токеном.
     */
    @GetMapping("/v1/token")
    ResponseEntity<String> getTempToken(
            @RequestParam("chat_id") Long chatId,
            @RequestParam("chat_type") String chatType,
            @RequestParam("user_id") Long userId,
            @RequestParam("first_name") String firstName,
            @RequestParam("last_name") String lastName,
            @RequestParam("username") String username,
            @RequestParam("bot_username") String botUsername,
            @RequestParam("bot_id") String botId,
            @RequestParam("hash") String hash,
            @RequestParam("auth_date") Long authDate
    );

    /**
     * Получает токен авторизации.
     *
     * @param chatId ID чата.
     * @param chatType тип чата.
     * @param userId ID пользователя.
     * @param firstName имя пользователя.
     * @param lastName фамилия пользователя.
     * @param username имя пользователя в Telegram.
     * @param botUsername имя бота.
     * @param botId ID бота.
     * @param hash хэш для проверки.
     * @param authDate дата авторизации.
     * @return ResponseEntity с токеном авторизации.
     */
    @GetMapping("/v1/auth")
    ResponseEntity<String> getAuth(
            @RequestParam("chat_id") Long chatId,
            @RequestParam("chat_type") String chatType,
            @RequestParam("user_id") Long userId,
            @RequestParam("first_name") String firstName,
            @RequestParam("last_name") String lastName,
            @RequestParam("username") String username,
            @RequestParam("bot_username") String botUsername,
            @RequestParam("bot_id") String botId,
            @RequestParam("hash") String hash,
            @RequestParam("auth_date") Long authDate
    );

    /**
     * Получает список счетов пользователя.
     *
     * @param token токен авторизации.
     * @return Строка с данными счетов.
     */
    @GetMapping("/v1/accounts")
    String getAccounts(@RequestHeader("Authorization") String token);

    /**
     * Получает список продуктов по типу.
     *
     * @param token токен авторизации.
     * @param productType тип продукта.
     * @return Строка с данными продуктов.
     */
    @GetMapping("/v1/products")
    String getProducts(
            @RequestHeader("Authorization") String token,
            @RequestParam("productType") String productType
    );

    /**
     * Получает список депозитов.
     *
     * @param token токен авторизации.
     * @param requestBody тело запроса.
     * @return Строка с данными депозитов.
     */
    @PostMapping("/v1/deposits")
    String getDeposits(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> requestBody
    );

    /**
     * Получает список кредитов.
     *
     * @param token токен авторизации.
     * @param requestBody тело запроса.
     * @return Строка с данными кредитов.
     */
    @PostMapping("/v1/credits")
    String getCredits(@RequestHeader("Authorization") String token,
                      @RequestBody Map<String, Object> requestBody
    );

    /**
     * Получает список валют.
     *
     * @param token токен авторизации.
     * @return Строка с данными валют.
     */
    @GetMapping("/v1/currencies")
    String getCurrencies(@RequestHeader("Authorization") String token);

    /**
     * Получает график платежей по кредиту.
     *
     * @param token токен авторизации.
     * @param creditId ID кредита.
     * @return Строка с графиком платежей.
     */
    @GetMapping("/v1/credit/{creditId}/paymentPlan")
    String getPaymentPlan(@RequestHeader("Authorization") String token,
                          @PathVariable("creditId") String creditId
    );

    /**
     * Создает новый счет.
     *
     * @param token токен авторизации.
     * @param requestBody тело запроса.
     * @return Строка с данными созданного счета.
     */
    @PostMapping("/v1/accounts/account")
    String createAccount(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> requestBody
    );

    /**
     * Создает новый депозит.
     *
     * @param token токен авторизации.
     * @param requestBody тело запроса.
     * @return Строка с данными созданного депозита.
     */
    @PostMapping("/v1/deposit")
    String createDeposit(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> requestBody
    );

    /**
     * Создает новый кредит.
     *
     * @param token токен авторизации.
     * @param requestBody тело запроса.
     * @return Строка с данными созданного кредита.
     */
    @PostMapping("/v1/credit")
    String createCredit(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> requestBody
    );

    /**
     * Закрывает депозит.
     *
     * @param token токен авторизации.
     * @param requestBody тело запроса.
     * @return Строка с результатом операции.
     */
    @PutMapping("/v1/deposit/close-deposit")
    String closeDeposit(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> requestBody
    );

    /**
     * Погашает кредит.
     *
     * @param token токен авторизации.
     * @param requestBody тело запроса.
     * @return Строка с результатом операции.
     */
    @PutMapping("/v1/credit/repayment")
    String closeCredit(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> requestBody
    );

    /**
     * Закрывает счет.
     *
     * @param token токен авторизации.
     * @param requestBody тело запроса.
     * @return Строка с результатом операции.
     */
    @PutMapping("/v1/accounts/close-account")
    String closeAccount(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> requestBody
    );

    /**
     * Совершает перевод.
     *
     * @param token токен авторизации.
     * @param requestBody тело запроса.
     * @return Строка с результатом операции.
     */
    @PostMapping("/v1/transfers")
    String transfer(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> requestBody
    );

    /**
     * Получает историю переводов.
     *
     * @param token токен авторизации.
     * @param requestBody тело запроса.
     * @return Строка с историей переводов.
     */
    @PostMapping("/v1/transfers/history")
    String transferHistory(@RequestHeader("Authorization") String token,
                           @RequestBody Map<String, Object> requestBody);
}

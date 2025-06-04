package org.example.bot.service;

import lombok.RequiredArgsConstructor;
import org.example.bot.config.BotConfig;

import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;

import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.web.util.UriComponentsBuilder;

import org.apache.commons.codec.digest.HmacAlgorithms;

/**
 * Сервис для обработки авторизации пользователей через Neobank API.
 * Отвечает за генерацию URL для авторизации, хранение токенов и проверку статуса авторизации.
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String AUTH_URL = "https://msa-bff-telegram-neobank.neoflex.ru/v1/auth";

    private final BotConfig botConfig;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final Map<Long, String> userTokens = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> botVerified = new ConcurrentHashMap<>();
    private final Map<Long, String> userHashes = new ConcurrentHashMap<>();


    /**
     * Верифицирует бота перед авторизацией
     */
    public AuthResponse verifyBot(Long chatId, User user) {
        if (botVerified.getOrDefault(chatId, false)) {
            return new AuthResponse(null, "Бот уже верифицирован", false);
        }

        try {
            long authDate = System.currentTimeMillis();
            String hash = createSignature(chatId, user);

            ResponseEntity<String> response = neoFlexTelegramAPI.getTempToken(
                    chatId,
                    "private",
                    user.getId(),
                    user.getFirstName(),
                    "",
                    user.getUserName(),
                    "TestNeoBank_bot",
                    "7678902604",
                    hash,
                    authDate
            );

            if (response.getStatusCode().is2xxSuccessful() ||
                    (response.getStatusCode().is4xxClientError() &&
                            response.getBody() != null &&
                            response.getBody().contains("/token"))) {

                botVerified.put(chatId, true);
                return new AuthResponse(null, null, false);
            }

            return new AuthResponse(null,
                    "Ошибка верификации: " + response.getStatusCode() + " - " + response.getBody(),
                    false);

        } catch (Exception e) {

            if (e.getMessage() != null && e.getMessage().contains("/token")) {
                botVerified.put(chatId, true);
                return new AuthResponse(null, null, false);
            }
            return new AuthResponse(null, "Ошибка верификации бота", false);
        }
    }

    /**
     * Обрабатывает запрос на авторизацию пользователя.
     *
     * @param chatId идентификатор чата пользователя в Telegram
     * @param user данные пользователя из Telegram
     * @return AuthResponse с URL для авторизации или сообщением об ошибке
     */
    public AuthResponse handleAuthRequest(Long chatId, User user) {
        try {
            if (!botVerified.getOrDefault(chatId, false)) {
                return new AuthResponse(null, "Сначала выполните верификацию бота", false);
            }

            long authDate = System.currentTimeMillis();
            String hash = createSignature(chatId, user);


            String authUrl = String.format("%s?chat_id=%d&chat_type=private&user_id=%d&first_name=%s&last_name=%s&username=%s&bot_username=%s&bot_id=%s&hash=%s&auth_date=%d",
                    AUTH_URL,
                    chatId,
                    user.getId(),
                    URLEncoder.encode(user.getFirstName(), StandardCharsets.UTF_8),
                    "",
                    user.getUserName() != null ? user.getUserName() : "",
                    "TestNeoBank_bot",
                    "7678902604",
                    hash,
                    authDate);

            return new AuthResponse(authUrl, null, false);
        } catch (Exception e) {
            return new AuthResponse(null, "Ошибка сервиса авторизации: " + e.getMessage(), false);
        }
    }

    public String fetchUserToken(Long chatId, User user) throws Exception {
        if (!botVerified.getOrDefault(chatId, false)) {
            throw new IllegalStateException("Сначала выполните верификацию бота через /start");
        }

        long authDate = System.currentTimeMillis();
        String hash = createSignature(chatId,user);
        userHashes.put(chatId, hash);

        ResponseEntity<String> response = neoFlexTelegramAPI.getTempToken(
                chatId,
                "private",
                user.getId(),
                user.getFirstName(),
                user.getLastName() != null ? user.getLastName() : "",
                user.getUserName() != null ? user.getUserName() : "",
                "TestNeoBank_bot",
                "7678902604",
                hash,
                authDate
        );

        JSONObject json = new JSONObject(response.getBody());
        String token = json.getString("access_token");

        saveUserToken(chatId, token);
        return token;
    }

    /**
     * Формирует строку данных для подписи в едином формате
     */
    private String buildSignatureDate(Long chatId, User user) {
        return String.format(
                "bot_id=7678902604\n" +
                        "bot_username=%s\n" +
                        "chat_id=%d\n" +
                        "chat_type=private\n" +
                        "first_name=%s\n" +
                        "last_name=%s\n" +
                        "user_id=%d\n" +
                        "username=%s",
                "TestNeoBank_bot",
                chatId,
                user.getFirstName(),
                user.getLastName() != null ? user.getLastName() : "",
                user.getId(),
                user.getUserName() != null ? user.getUserName() : ""
        );
    }

    /**
     * Создает подпись для запроса
     */
    private String createSignature(Long chatId, User user) throws Exception {
        String secretKey = sha256(botConfig.token());
        String data = buildSignatureDate(chatId,user);
        return hmacSha256(secretKey, data);
    }

    public void forceVerifyBot(Long chatId) {
        botVerified.put(chatId, true);
    }

    /**
     * Генерирует SHA-256 хеш из входной строки.
     *
     * @param input входная строка для хеширования
     * @return хеш в HEX-формате
     * @throws Exception если алгоритм хеширования недоступен
     */
    private String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    /**
     * Генерирует HMAC-SHA256 подпись для данных.
     *
     * @param key секретный ключ для подписи
     * @param data данные для подписи
     * @return подпись в HEX-формате
     */
    private String hmacSha256(String key, String data) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, key.getBytes(StandardCharsets.UTF_8))
                .hmacHex(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Конвертирует массив байт в HEX-строку.
     *
     * @param bytes массив байт для конвертации
     * @return HEX-строка
     */
    private static String bytesToHex(byte[] bytes) {
        Formatter formatter = new Formatter();
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    /**
     * Сохраняет токен авторизации для указанного чата.
     *
     * @param chatId идентификатор чата
     * @param token токен авторизации
     */
    public void saveUserToken(Long chatId, String token) {
        userTokens.put(chatId, token);
    }

    /**
     * Проверяет, авторизован ли пользователь в указанном чате.
     *
     * @param chatId идентификатор чата
     * @return true если пользователь авторизован, иначе false
     */
    public boolean isAuthorized(Long chatId) {
        return userTokens.containsKey(chatId);
    }

    /**
     * Возвращает токен пользователя
     */
    public String getUserToken(Long chatId) {
        return userTokens.get(chatId);
    }

    /**
     * Результат обработки запроса авторизации.
     *
     * @param authUrl URL для перенаправления на авторизацию (null если ошибка или уже авторизован)
     * @param error сообщение об ошибке (null если успешно)
     * @param isAuthorized флаг, указывающий что пользователь уже авторизован
     */
    public record AuthResponse(String authUrl, String error, boolean isAuthorized) {}
}
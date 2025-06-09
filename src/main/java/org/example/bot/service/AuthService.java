package org.example.bot.service;

import lombok.RequiredArgsConstructor;
import org.example.bot.config.BotConfig;
import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;

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
    private static final String BOT_ID = "7678902604";
    private static final String BOT_USERNAME = "TestNeoBank_bot";
    private static final String CHAT_TYPE = "private";
    private static final String AUTH_URL = "https://msa-bff-telegram-neobank.neoflex.ru/v1/auth";

    private final BotConfig botConfig;
    private final NeoFlexTelegramAPI neoFlexTelegramAPI;
    private final Map<Long, String> userTokens = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> botVerified = new ConcurrentHashMap<>();

    /**
     * Проверяет и верифицирует бота для указанного чата и пользователя.
     *
     * @param chatId идентификатор чата
     * @param user   объект пользователя Telegram
     * @return объект AuthResponse с результатом операции
     */
    public AuthResponse verifyBot(Long chatId, User user) {
        if (isBotVerified(chatId)) {
            return AuthResponse.alreadyVerified();
        }

        try {
            AuthData authData = buildAuthData(chatId, user);
            ResponseEntity<String> response = neoFlexTelegramAPI.getTempToken(
                    authData.chatId(),
                    authData.chatType(),
                    authData.userId(),
                    authData.firstName(),
                    authData.lastName().orElse(""),
                    authData.username().orElse(""),
                    authData.botUsername(),
                    authData.botId(),
                    authData.hash(),
                    authData.authDate()
            );

            if (response.getBody() != null && response.getBody().contains("<!DOCTYPE html>")) {
                return AuthResponse.error("Ошибка аутентификации. Проверьте токен бота");
            }

            markBotAsVerified(chatId);
            return AuthResponse.success();

        } catch (Exception e) {
            return AuthResponse.error("Ошибка верификации бота: " + e.getMessage());
        }
    }

    /**
     * Извлекает сообщение об ошибке из исключения.
     *
     * @param e исключение
     * @return сообщение об ошибке
     */
    private String extractErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) return "Неизвестная ошибка";

        int startIdx = message.indexOf("\"errorDetail\":\"");
        if (startIdx > 0) {
            startIdx += "\"errorDetail\":\"".length();
            int endIdx = message.indexOf("\"", startIdx);
            if (endIdx > startIdx) {
                return message.substring(startIdx, endIdx);
            }
        }
        return message;
    }

    /**
     * Обрабатывает запрос на авторизацию для указанного чата и пользователя.
     *
     * @param chatId идентификатор чата
     * @param user   объект пользователя Telegram
     * @return объект AuthResponse с URL для авторизации или сообщением об ошибке
     */
    public AuthResponse handleAuthRequest(Long chatId, User user) {
        try {
            if (!isBotVerified(chatId)) {
                return AuthResponse.error("Сначала выполните верификацию бота");
            }

            AuthData authData = buildAuthData(chatId, user);
            String authUrl = buildAuthUrl(authData);

            return AuthResponse.withUrl(authUrl);
        } catch (Exception e) {
            return AuthResponse.error("Ошибка сервиса авторизации: " + extractErrorMessage(e));
        }
    }

    /**
     * Получает временный токен пользователя для указанного чата.
     *
     * @param chatId идентификатор чата
     * @param user   объект пользователя Telegram
     * @return строку с токеном пользователя в формате "Bearer {token}"
     * @throws Exception если бот не верифицирован или произошла ошибка при получении токена
     */
    public String fetchUserToken(Long chatId, User user) throws Exception {
        if (!isBotVerified(chatId)) {
            throw new IllegalStateException("Сначала выполните верификацию бота через /start");
        }

        AuthData authData = buildAuthData(chatId, user);
        ResponseEntity<String> response = neoFlexTelegramAPI.getTempToken(
                authData.chatId(),
                authData.chatType(),
                authData.userId(),
                authData.firstName(),
                authData.lastName().orElse(""),
                authData.username().orElse(""),
                authData.botUsername(),
                authData.botId(),
                authData.hash(),
                authData.authDate()
        );

        if (response.getBody() != null && response.getBody().contains("<!DOCTYPE html>")) {
            throw new IllegalStateException("Ошибка аутентификации. Неверный токен бота");
        }

        try {
            JSONObject json = new JSONObject(response.getBody());
            return "Bearer " + json.getString("access_token");
        } catch (JSONException e) {
            throw new IllegalStateException("Неверный формат ответа сервера");
        }
    }

    /**
     * Создает объект AuthData для указанного чата и пользователя.
     *
     * @param chatId идентификатор чата
     * @param user   объект пользователя Telegram
     * @return объект AuthData с данными для авторизации
     * @throws Exception если произошла ошибка при создании подписи
     */
    private AuthData buildAuthData(Long chatId, User user) throws Exception {
        return new AuthData(
                chatId,
                CHAT_TYPE,
                user.getId(),
                user.getFirstName(),
                Optional.ofNullable(user.getLastName()),
                Optional.ofNullable(user.getUserName()),
                BOT_USERNAME,
                BOT_ID,
                createSignature(chatId, user),
                System.currentTimeMillis()
        );
    }

    /**
     * Строит URL для авторизации на основе данных AuthData.
     *
     * @param authData данные для авторизации
     * @return строку с URL для авторизации
     * @throws Exception если произошла ошибка при кодировании параметров
     */
    private String buildAuthUrl(AuthData authData) throws Exception {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(AUTH_URL)
                .queryParam("chat_id", authData.chatId())
                .queryParam("chat_type", authData.chatType())
                .queryParam("user_id", authData.userId())
                .queryParam("first_name", authData.firstName())
                .queryParam("last_name", authData.lastName().orElse(""))
                .queryParam("username", authData.username().orElse(""))
                .queryParam("bot_username", authData.botUsername())
                .queryParam("bot_id", authData.botId())
                .queryParam("hash", authData.hash())
                .queryParam("auth_date", authData.authDate());

        return builder.build().toUriString();
    }

    /**
     * Создает криптографическую подпись для запроса авторизации.
     *
     * @param chatId идентификатор чата
     * @param user   объект пользователя Telegram
     * @return строку с хеш-подписью
     * @throws Exception если произошла ошибка при создании подписи
     */
    private String createSignature(Long chatId, User user) throws Exception {
        String secretKey = sha256(botConfig.token());
        System.out.println(secretKey);
        String data = buildSignatureData(chatId, user);
        System.out.println("Data for hash: " + data);
        String hash = hmacSha256(secretKey, data);
        System.out.println("Generated hash: " + hash);
        return hash;
    }

    /**
     * Формирует строку данных для создания подписи.
     *
     * @param chatId идентификатор чата
     * @param user   объект пользователя Telegram
     * @return строку с данными для подписи
     */
    private String buildSignatureData(Long chatId, User user) {
        StringBuilder sb = new StringBuilder();
        appendField(sb, "bot_id", BOT_ID);
        appendField(sb, "bot_username", BOT_USERNAME);
        appendField(sb, "chat_id", chatId.toString());
        appendField(sb, "chat_type", CHAT_TYPE);
        appendField(sb, "first_name", user.getFirstName());
        appendField(sb, "last_name", user.getLastName() != null ? user.getLastName() : "");
        appendField(sb, "user_id", user.getId().toString());
        if (user.getUserName() != null) {
            appendField(sb, "username", user.getUserName());
        }
        return sb.toString();
    }

    /**
     * Добавляет поле в строку данных для подписи.
     *
     * @param sb         StringBuilder для формирования строки
     * @param fieldName  название поля
     * @param fieldValue значение поля
     */
    private void appendField(StringBuilder sb, String fieldName, String fieldValue) {
        if (sb.length() > 0) {
            sb.append("\n");
        }
        sb.append(fieldName).append("=").append(fieldValue);
    }

    /**
     * Проверяет, верифицирован ли бот для указанного чата.
     *
     * @param chatId идентификатор чата
     * @return true если бот верифицирован, false в противном случае
     */
    private boolean isBotVerified(Long chatId) {
        return botVerified.getOrDefault(chatId, false);
    }

    /**
     * Помечает бота как верифицированного для указанного чата.
     *
     * @param chatId идентификатор чата
     */
    private void markBotAsVerified(Long chatId) {
        botVerified.put(chatId, true);
    }

    /**
     * Проверяет валидность токена.
     *
     * @param token токен для проверки
     * @return true если токен валиден, false в противном случае
     */
    public boolean isTokenValid(String token) {
        try {
            String authToken = token.startsWith("Bearer ") ? token : "Bearer " + token;
            String response = neoFlexTelegramAPI.getAccounts(authToken);
            return !(response.trim().startsWith("<!DOCTYPE") || response.trim().startsWith("<html"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получает валидный токен пользователя, обновляя его при необходимости.
     *
     * @param chatId идентификатор чата
     * @param user объект пользователя Telegram
     * @return валидный токен пользователя
     * @throws Exception если произошла ошибка при получении токена
     */
    public String getValidUserToken(Long chatId, User user) throws Exception {
        String token = getUserToken(chatId);
        if (token == null || !isTokenValid(token)) {
            token = fetchUserToken(chatId, user);
            saveUserToken(chatId, token);
        }
        return token;
    }

    /**
     * Вычисляет SHA-256 хеш строки.
     *
     * @param input входная строка
     * @return хеш строки в шестнадцатеричном формате
     * @throws Exception если произошла ошибка при вычислении хеша
     */
    private String sha256(String input) throws Exception {
        System.out.println("Using bot token: " + botConfig.token()); // Логируем токен
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    /**
     * Вычисляет HMAC-SHA256 подпись для данных с использованием ключа.
     *
     * @param key  секретный ключ
     * @param data данные для подписи
     * @return подпись в шестнадцатеричном формате
     */
    private String hmacSha256(String key, String data) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, key.getBytes(StandardCharsets.UTF_8))
                .hmacHex(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Преобразует массив байтов в шестнадцатеричную строку.
     *
     * @param bytes массив байтов
     * @return шестнадцатеричную строку
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Сохраняет токен пользователя для указанного чата.
     *
     * @param chatId идентификатор чата
     * @param token  токен пользователя
     */
    public void saveUserToken(Long chatId, String token) {
        userTokens.put(chatId, token);
    }

    /**
     * Проверяет, авторизован ли пользователь в указанном чате.
     *
     * @param chatId идентификатор чата
     * @return true если пользователь авторизован, false в противном случае
     */
    public boolean isAuthorized(Long chatId) {
        return userTokens.containsKey(chatId);
    }

    /**
     * Получает токен пользователя для указанного чата.
     *
     * @param chatId идентификатор чата
     * @return токен пользователя или null если токен не найден
     */
    public String getUserToken(Long chatId) {
        return userTokens.get(chatId);
    }

    /**
     * Принудительно помечает бота как верифицированного для указанного чата.
     *
     * @param chatId идентификатор чата
     */
    public void forceVerifyBot(Long chatId) {
        botVerified.put(chatId, true);
    }

    /**
     * Внутренний класс для хранения данных авторизации.
     */
    private record AuthData(
            Long chatId,
            String chatType,
            Long userId,
            String firstName,
            Optional<String> lastName,
            Optional<String> username,
            String botUsername,
            String botId,
            String hash,
            Long authDate
    ) {
    }

    /**
     * Класс для формирования ответов сервиса авторизации.
     */
    public static final class AuthResponse {
        private final String authUrl;
        private final String error;
        private final boolean isAuthorized;

        private AuthResponse(String authUrl, String error, boolean isAuthorized) {
            this.authUrl = authUrl;
            this.error = error;
            this.isAuthorized = isAuthorized;
        }

        /**
         * Создает успешный ответ без дополнительных данных.
         *
         * @return объект AuthResponse
         */
        public static AuthResponse success() {
            return new AuthResponse(null, null, false);
        }

        /**
         * Создает ответ с сообщением о том, что бот уже верифицирован.
         *
         * @return объект AuthResponse
         */
        public static AuthResponse alreadyVerified() {
            return new AuthResponse(null, "Бот уже верифицирован", false);
        }

        /**
         * Создает ответ с URL для авторизации.
         *
         * @param authUrl URL для авторизации
         * @return объект AuthResponse
         */
        public static AuthResponse withUrl(String authUrl) {
            return new AuthResponse(authUrl, null, false);
        }

        /**
         * Создает ответ с сообщением об ошибке.
         *
         * @param message сообщение об ошибке
         * @return объект AuthResponse
         */
        public static AuthResponse error(String message) {
            return new AuthResponse(null, message, false);
        }

        /**
         * Возвращает URL для авторизации.
         *
         * @return URL для авторизации или null
         */
        public String authUrl() {
            return authUrl;
        }

        /**
         * Возвращает сообщение об ошибке.
         *
         * @return сообщение об ошибке или null
         */
        public String error() {
            return error;
        }
    }
}
package org.example.bot.service;

import lombok.RequiredArgsConstructor;
import org.example.bot.config.BotConfig;

import org.example.bot.fiegn.NeoFlexTelegramAPI;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;
import java.util.function.Function;

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
    private final Map<Long, String> userHashes = new ConcurrentHashMap<>();

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

    private String extractErrorDetail(String responseBody) {
        try {
            if (responseBody == null) return "Неизвестная ошибка";
            JSONObject json = new JSONObject(responseBody);
            return json.optString("errorDetail", json.optString("errorTitle", "Неизвестная ошибка"));
        } catch (JSONException e) {
            return responseBody;
        }
    }

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
    //https://msa-bff-telegram-neobank.neoflex.ru/v1/auth?chat_id=1383262366&chat_type=private&user_id=1383262366&first_name=Никита&last_name=&username=nikita_trofimov22&bot_username=TestNeoBank_bot&bot_id=7678902604&hash=60d8200f276bdce5928c478847ace1f0b7f7015f199f86b36709fa0fa2779d57&auth_date=1746084615406

    private String buildAuthUrl(AuthData authData) throws Exception {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(AUTH_URL)
                .queryParam("chat_id", authData.chatId())
                .queryParam("chat_type", authData.chatType())
                .queryParam("user_id", authData.userId())
                .queryParam("first_name", URLEncoder.encode(authData.firstName(), StandardCharsets.UTF_8))
                .queryParam("last_name", authData.lastName().orElse(""))
                .queryParam("username", authData.username().orElse(null))
                .queryParam("bot_username", authData.botUsername())
                .queryParam("bot_id", authData.botId())
                .queryParam("hash", authData.hash())
                .queryParam("auth_date", authData.authDate());

        return builder.build().toUriString();
    }

    private String createSignature(Long chatId, User user) throws Exception {
        String secretKey = sha256(botConfig.token());
        System.out.println(secretKey);
        String data = buildSignatureData(chatId, user);
        System.out.println("Data for hash: " + data);
        String hash = hmacSha256(secretKey, data);
        System.out.println("Generated hash: " + hash);
        return hash;
    }

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

    private void appendField(StringBuilder sb, String fieldName, String fieldValue) {
        if (sb.length() > 0) {
            sb.append("\n");
        }
        sb.append(fieldName).append("=").append(fieldValue);
    }

    private boolean isSuccessfulOrTokenRedirect(ResponseEntity<String> response) {
        return response.getStatusCode().is2xxSuccessful() ||
                (response.getStatusCode().is4xxClientError() &&
                        response.getBody() != null &&
                        response.getBody().contains("/token"));
    }

    private boolean isBotVerified(Long chatId) {
        return botVerified.getOrDefault(chatId, false);
    }

    private void markBotAsVerified(Long chatId) {
        botVerified.put(chatId, true);
    }

    private String sha256(String input) throws Exception {
        System.out.println("Using bot token: " + botConfig.token()); // Логируем токен
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String hmacSha256(String key, String data) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, key.getBytes(StandardCharsets.UTF_8))
                .hmacHex(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void saveUserToken(Long chatId, String token) {
        userTokens.put(chatId, token);
    }

    public boolean isAuthorized(Long chatId) {
        return userTokens.containsKey(chatId);
    }

    public String getUserToken(Long chatId) {
        return userTokens.get(chatId);
    }

    public void forceVerifyBot(Long chatId) {
        botVerified.put(chatId, true);
    }

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
    ) {}

    public static final class AuthResponse {
        private final String authUrl;
        private final String error;
        private final boolean isAuthorized;

        private AuthResponse(String authUrl, String error, boolean isAuthorized) {
            this.authUrl = authUrl;
            this.error = error;
            this.isAuthorized = isAuthorized;
        }

        public static AuthResponse success() {
            return new AuthResponse(null, null, false);
        }

        public static AuthResponse alreadyVerified() {
            return new AuthResponse(null, "Бот уже верифицирован", false);
        }

        public static AuthResponse withUrl(String authUrl) {
            return new AuthResponse(authUrl, null, false);
        }

        public static AuthResponse error(String message) {
            return new AuthResponse(null, message, false);
        }

        public String authUrl() { return authUrl; }
        public String error() { return error; }
        public boolean isAuthorized() { return isAuthorized; }
    }
}
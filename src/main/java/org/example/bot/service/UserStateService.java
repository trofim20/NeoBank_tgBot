package org.example.bot.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для управления состоянием пользователей в чате.
 * Позволяет хранить и управлять текущим состоянием и дополнительными данными пользователей.
 */
@Service
public class UserStateService {
    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<Long, Map<String, Object>> userData = new HashMap<>();

    /**
     * Устанавливает состояние пользователя.
     *
     * @param chatId идентификатор чата
     * @param state новое состояние пользователя
     */
    public void setUserState(Long chatId, String state) {
        userStates.put(chatId, state);
    }

    /**
     * Получает текущее состояние пользователя.
     *
     * @param chatId идентификатор чата
     * @return текущее состояние пользователя или null если не установлено
     */
    public String getUserState(Long chatId) {
        return userStates.get(chatId);
    }

    /**
     * Сохраняет дополнительные данные пользователя.
     *
     * @param chatId идентификатор чата
     * @param key ключ данных
     * @param value значение данных
     */
    public void setUserData(Long chatId, String key, Object value) {
        userData.computeIfAbsent(chatId, k -> new HashMap<>()).put(key, value);
    }

    /**
     * Получает дополнительные данные пользователя.
     *
     * @param chatId идентификатор чата
     * @param key ключ данных
     * @return сохраненное значение или null если не найдено
     */
    public Object getUserData(Long chatId, String key) {
        return userData.getOrDefault(chatId, Map.of()).get(key);
    }

    /**
     * Очищает состояние и дополнительные данные пользователя.
     *
     * @param chatId идентификатор чата
     */
    public void clearUserState(Long chatId) {
        userStates.remove(chatId);
        userData.remove(chatId);
    }
}
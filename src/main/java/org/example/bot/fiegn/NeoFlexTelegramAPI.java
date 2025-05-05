package org.example.bot.fiegn;

import org.springframework.cloud.openfeign.FeignClient;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "neoflex-telegram-api",
        url = "${feign.bff-api.url}",
        configuration = FeignConfiguration.class
)
public interface NeoFlexTelegramAPI {
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

    @GetMapping("/v1/accounts")
    String getAccounts(@RequestHeader("Authorization") String token);
}

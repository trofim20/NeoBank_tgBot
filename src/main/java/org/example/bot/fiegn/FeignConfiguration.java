package org.example.bot.fiegn;

import feign.Client;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * Конфигурационный класс для настройки Feign клиента с SSL.
 */
@Configuration
public class FeignConfiguration {

    /**
     * Создает и настраивает Feign клиент с SSL контекстом.
     *
     * @param sslBundles SSL бандлы для настройки SSL контекста.
     * @return Настроенный Feign клиент.
     * @throws Exception если возникает ошибка при создании SSL контекста.
     */
    @Bean
    public Client feignClient(SslBundles sslBundles) throws Exception {
        SSLContext sslContext = sslBundles.getBundle("bff-client").createSslContext();
        return new Client.Default(
                sslContext.getSocketFactory(),
                HttpsURLConnection.getDefaultHostnameVerifier()
        );
    }
}

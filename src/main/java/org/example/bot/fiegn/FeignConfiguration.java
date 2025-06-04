package org.example.bot.fiegn;

import feign.Client;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

@Configuration
public class FeignConfiguration {

    @Bean
    public Client feignClient(SslBundles sslBundles) throws Exception {
        SSLContext sslContext = sslBundles.getBundle("bff-client").createSslContext();
        return new Client.Default(
                sslContext.getSocketFactory(),
                HttpsURLConnection.getDefaultHostnameVerifier()
        );
    }
}

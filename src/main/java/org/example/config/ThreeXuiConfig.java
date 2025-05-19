package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "3xui")
public class ThreeXuiConfig {
    private String jwtToken;  // Индивидуальный токен моей админки
    private String urlForLink;  // Ссылка для ключа подключения
    private String urlForApi;  // Ссылка для отправки api запроса

    public String getUrlForLink() {
        return urlForLink;
    }

    public void setUrlForLink(String urlForLink) {
        this.urlForLink = urlForLink;
    }

    public String getUrlForApi() {
        return urlForApi;
    }

    public void setUrlForApi(String urlForApi) {
        this.urlForApi = urlForApi;
    }

    // Для отправки запросов
    public String getJwtToken() {
        return jwtToken;
    }

    public void setJwtToken(String jwtToken) {
        this.jwtToken = jwtToken;
    }

}

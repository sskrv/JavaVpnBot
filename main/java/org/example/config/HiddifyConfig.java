package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hiddify")
public class HiddifyConfig {
    private String secretApi;
    private String apiUrl;
    private String adminProxyPath;
    private String userProxyPath;

    // UUID админа
    public String getSecretApi() {
        return secretApi;
    }

    public void setSecretApi(String secretApi) {
        this.secretApi = secretApi;
    }

    // Домен сервера
    public String getApiURL() {
        return apiUrl;
    }

    public void setApiURL(String apiURL) {
        this.apiUrl = apiURL;
    }

    // Для отправки запросов
    public String getAdminProxyPath() {
        return adminProxyPath;
    }

    public void setAdminProxyPath(String adminProxyPath) {
        this.adminProxyPath = adminProxyPath;
    }

    // Для составления ключа
    public String getUserProxyPath() {
        return userProxyPath;
    }

    public void setUserProxyPath(String userProxyPath) {
        this.userProxyPath = userProxyPath;
    }
}

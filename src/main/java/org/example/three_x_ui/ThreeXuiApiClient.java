package org.example.three_x_ui;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.example.config.ThreeXuiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;


@Component
public class ThreeXuiApiClient {
    private static final Logger logger = LoggerFactory.getLogger(ThreeXuiApiClient.class);
    private final OkHttpClient httpClient;
    private final String jwtToken;
    private final Gson gson;
    private final String urlForLink;
    private final String urlForApi;

    public ThreeXuiApiClient(ThreeXuiConfig config) {
        this.urlForLink = config.getUrlForLink();  // Домен моего сервака + ссылка подписки
        this.urlForApi = config.getUrlForApi();  // Домен моего сервака + ссылка подписки
        this.jwtToken = config.getJwtToken();  // Индивидуальный токен моей админки
        this.gson = new Gson();

        this.httpClient = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build();
    }

    /**
     * Создает нового пользователя в системе 3X-UI
     *
     * @param username Логин пользователя (обычно Telegram ID)
     * @param uuid UUID для клиента VPN
     * @return VPN ссылка для подключения или null в случае ошибки
     */
    public String createUser(String username, String uuid) {
        try {
            // Генерируем уникальный subId
            String subId = generateSubId();
            
            // Вычисляем timestamp для даты через 30 дней (в секундах)
            long expiryTime = System.currentTimeMillis() / 1000 + (30L * 24 * 60 * 60);
            
            // Формируем JSON строку строго в соответствии с документацией
            // Обратите внимание на двойное экранирование внутренних кавычек
            String jsonPayload = String.format(
                "{" +
                "\"id\": 1," +
                "\"settings\": \"{\\\"clients\\\": [{\\\"id\\\": \\\"%s\\\"," + 
                "\\\"flow\\\": \\\"xtls-rprx-vision\\\"," +
                "\\\"email\\\": \\\"%s\\\"," +
                "\\\"limitIp\\\": 0," +
                "\\\"totalGB\\\": 0," +
                "\\\"expiryTime\\\": %d," +
                "\\\"enable\\\": true," +
                "\\\"tgId\\\": \\\"\\\"," +
                "\\\"subId\\\": \\\"%s\\\"," +
                "\\\"reset\\\": 0}]}\"" +
                "}", 
                uuid, username, expiryTime, subId
            );
            
            logger.info("Sending addClient request: {}", jsonPayload);
            
            // Используем MediaType.parse("text/plain") согласно документации
            MediaType mediaType = MediaType.parse("text/plain");
            RequestBody body = RequestBody.create(mediaType, jsonPayload);
            
            Request request = new Request.Builder()
                    .url(urlForApi + "/panel/api/inbounds/addClient")
                    .addHeader("Accept", "application/json")
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                logger.info("Response code: {}", response.code());
                
                // Получаем тело ответа один раз
                String responseContent = null;
                ResponseBody responseBody = response.body();
                
                if (responseBody != null) {
                    try {
                        responseContent = responseBody.string();
                        logger.info("Response body: {}", responseContent);
                    } catch (IOException e) {
                        logger.error("Failed to read response body: {}", e.getMessage());
                        return null;
                    }
                }
                
                if (!response.isSuccessful()) {
                    logger.error("Failed to add client. Status code: {}, Error: {}", 
                            response.code(), responseContent != null ? responseContent : "No response body");
                    return null;
                }
                
                // Проверяем содержимое ответа
                if (responseContent != null && !responseContent.isEmpty()) {
                    try {
                        JsonObject responseJson = gson.fromJson(responseContent, JsonObject.class);
                        
                        if (responseJson.has("success") && responseJson.get("success").getAsBoolean()) {
                            // Операция успешна, возвращаем ссылку для подключения 
                            logger.info("Client added successfully, returning connection link");
                            return generateConnectionLink(subId);
                        } else {
                            // Операция неуспешна, логируем ошибку из ответа
                            String errorMsg = responseJson.has("msg") ? responseJson.get("msg").getAsString() : "Unknown error";
                            logger.error("Failed to add client: {}", errorMsg);
                            return null;
                        }
                    } catch (Exception e) {
                        logger.error("Failed to parse response JSON: {}", e.getMessage());
                        return null;
                    }
                } else {
                    logger.error("Empty response body");
                    return null;
                }
            }
        } catch (Exception e) {
            logger.error("Error adding client: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Генерирует ссылку для подключения к VPN на основе subId пользователя
     *
     * @param subId subId пользователя
     * @return Ссылка для подключения
     */
    private String generateConnectionLink(String subId) {
        // Формат: http://домен:порт/путьДляПодписки/ + subId

        return urlForLink + "/" + subId;
    }

    /**
     * Генерирует уникальный subId для пользователя
     * @return Уникальный subId
     */
    private String generateSubId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
        
    }
}
package org.example.hiddify;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import org.example.config.HiddifyConfig;
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

@Component
public class HiddifyApiClient {
    private static final Logger logger = LoggerFactory.getLogger(HiddifyApiClient.class);
    private final OkHttpClient httpClient;
    private final String apiBaseUrl;
    private final String adminProxyPath;
    private final String userProxyPath;
    private final String secretApiKey;
    private final Gson gson;

    public HiddifyApiClient(HiddifyConfig config) {
        this.apiBaseUrl = config.getApiURL();  // Домен моего сервака
        this.adminProxyPath = config.getAdminProxyPath();  // Взято из настроек, нужно для отправки запросов
        this.userProxyPath = config.getUserProxyPath();  // Взято из настроек, нужно для составления ключа
        this.secretApiKey = config.getSecretApi();  // UUID админа панели Hiddify
        this.gson = new Gson();

        this.httpClient = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build();
    }

    /**
     * Создает нового пользователя в системе Hiddify
     *
     * @param userId Telegram ID пользователя
     * @return VPN ключ или null в случае ошибки
     */
    public String createUser(long userId, int gigabytes, int days) {
        try {
            // Текущая дата в формате YYYY-MM-DD
            String currentDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

            // Создаем данные пользователя
            JsonObject userJson = new JsonObject();
            userJson.addProperty("added_by_uuid", (String) null);
            userJson.addProperty("comment", "Created via Telegram Bot");
            userJson.addProperty("current_usage_GB", 0); // 50 GB согласно требованиям
            userJson.addProperty("ed25519_private_key", "string"); // Будет сгенерировано сервером
            userJson.addProperty("ed25519_public_key", "string");  // Будет сгенерировано сервером
            userJson.addProperty("enable", true);
            userJson.addProperty("is_active", true);
            userJson.addProperty("lang", "ru"); // Используем русский язык для пользователей
            userJson.addProperty("last_online", (String) null);
            userJson.addProperty("last_reset_time", (String) null);
            userJson.addProperty("mode", "no_reset"); // Согласно требованиям
            userJson.addProperty("name", "");  // Имя пустое ибо нафиг
            userJson.addProperty("package_days", days); // 30 дней согласно требованиям
            userJson.addProperty("start_date", currentDate);
            userJson.addProperty("telegram_id", userId); // Telegram ID пользователя
            userJson.addProperty("usage_limit_GB", gigabytes); // 100 GB согласно требованиям
            userJson.addProperty("uuid", (String) null); // Будет сгенерировано сервером
            userJson.addProperty("wg_pk", "string"); // Будет сгенерировано сервером
            userJson.addProperty("wg_psk", "string"); // Будет сгенерировано сервером
            userJson.addProperty("wg_pub", "string"); // Будет сгенерировано сервером

            String jsonPayload = gson.toJson(userJson);
            logger.info("Sending user creation request: {}", jsonPayload);

            // Создаем запрос
            RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json"));

            // Формируем полный URL для API
            String fullUrl = apiBaseUrl + adminProxyPath + "/api/v2/admin/user/";
            logger.info("API URL: {}", fullUrl);

            /*
             * Отправка запроса.
             * Всего в запросе 3 заголовка и 1 тело, и всё.
             */
            logger.info("apiBaseUrl: {}", apiBaseUrl);
            logger.info("adminProxyPath: {}", adminProxyPath);
            logger.info("Full API URL: {}", fullUrl);
            Request request = new Request.Builder().url(fullUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("Hiddify-API-Key", secretApiKey)
                    .post(body)
                    .build();


            // Выполняем запрос
            try (Response response = httpClient.newCall(request).execute()) {
                logger.info("Response code: {}", response.code());

                // Обрабатываем ошибку
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No response body";
                    logger.error("Failed to create user. Status code: {}, Error: {}", response.code(), errorBody);
                    return null;
                }

                // Парсим ответ
                if (response.body() != null) {
                    String responseBody = response.body().string();
                    logger.info("Response body: {}", responseBody);

                    JsonObject responseJson = gson.fromJson(responseBody, JsonObject.class);

                    // Проверяем наличие UUID в ответе
                    if (responseJson.has("uuid")) {
                        String uuid = responseJson.get("uuid").getAsString();

                        // Генерируем ссылку подключения
                        return generateConnectionLink(uuid);
                    } else {
                        logger.error("UUID not found in response: {}", responseBody);
                    }
                } else {
                    logger.error("Response body is null");
                }

                return null;
            }
        } catch (IOException e) {
            logger.error("Error creating Hiddify user: {}", e.getMessage(), e);
            return null;
        }
    }


    /**
     * deepseek порекомендовал мне сделать вот так, потом посмотрю что лучше
     * 
    public String createUser(long userId, int gigabytes, int days) {
        try {
            String currentDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

            JsonObject userJson = new JsonObject();
            userJson.addProperty("added_by_uuid", (String) null);
            userJson.addProperty("comment", "Created via Telegram Bot");
            userJson.addProperty("current_usage_GB", 0);
            // ... остальные свойства ...

            String jsonPayload = gson.toJson(userJson);
            logger.info("Sending user creation request: {}", jsonPayload);

            RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json"));
            String fullUrl = apiBaseUrl + adminProxyPath + "/api/v2/admin/user/";

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("Hiddify-API-Key", secretApiKey)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                logger.info("Response code: {}", response.code());

                if (!response.isSuccessful()) {
                    try (ResponseBody errorBody = response.body()) {
                        String errorContent = errorBody != null ? errorBody.string() : "No response body";
                        logger.error("Failed to create user. Status code: {}, Error: {}", 
                                response.code(), errorContent);
                    }
                    return null;
                }

                try (ResponseBody responseBody = response.body()) {
                    if (responseBody == null) {
                        logger.error("Response body is null");
                        return null;
                    }

                    String responseContent = responseBody.string();
                    logger.info("Response body: {}", responseContent);

                    JsonObject responseJson = gson.fromJson(responseContent, JsonObject.class);
                    if (responseJson.has("uuid")) {
                        return generateConnectionLink(responseJson.get("uuid").getAsString());
                    } else {
                        logger.error("UUID not found in response: {}", responseContent);
                        return null;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error creating Hiddify user: {}", e.getMessage(), e);
            return null;
        }
    }
     */

    /**
     * Генерирует ссылку для подключения к VPN на основе UUID пользователя и имени
     *
     * @param userUuid UUID пользователя
     * @return Ссылка для подключения
     */
    private String generateConnectionLink(String userUuid) {
        // Формат: https://45.67.231.231.sslip.io/aMwTnyTwAxHZo/uuid
        // Это старый сервак если что

        return apiBaseUrl + userProxyPath + "/" + userUuid;
    }
}
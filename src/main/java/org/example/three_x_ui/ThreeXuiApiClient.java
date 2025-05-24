package org.example.three_x_ui;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.example.config.ThreeXuiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

import okhttp3.OkHttpClient;

@Component
public class ThreeXuiApiClient {
    private static final Logger logger = LoggerFactory.getLogger(ThreeXuiApiClient.class);
    private final String urlForLink;
    private final String urlForApi;
    private final String cookiesFilePath;

    /**
     * @param config Конфигурация с urlForLink и urlForApi
     */
    public ThreeXuiApiClient(ThreeXuiConfig config) {
        this.urlForLink = config.getUrlForLink();
        this.urlForApi = config.getUrlForApi();
        new Gson();
        new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // Правильный путь к файлу cookies.txt в папке three_x_ui
        this.cookiesFilePath = createCookiesFilePath();
    }

    /**
     * Создает правильный путь к файлу cookies.txt
     */
    private String createCookiesFilePath() {
        try {
            // Получаем текущую рабочую директорию
            Path currentPath = Paths.get("").toAbsolutePath();
            Path cookiesPath = currentPath.resolve("three_x_ui").resolve("cookies.txt");
            
            // Создаем директорию three_x_ui если её нет
            Files.createDirectories(cookiesPath.getParent());
            
            logger.info("Путь к файлу cookies: {}", cookiesPath.toString());
            return cookiesPath.toString();
        } catch (IOException e) {
            logger.error("Ошибка при создании пути к файлу cookies", e);
            // Fallback к относительному пути
            return "three_x_ui/cookies.txt";
        }
    }

    public static String sendPostRequestWithCookies(String url, String jsonData, String cookieFilePath) {
        Logger staticLogger = LoggerFactory.getLogger(ThreeXuiApiClient.class);
        
        try {
            // Создание небезопасного SSL контекста (аналог -k в curl)
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Создание HTTP клиента с отключенной проверкой SSL
            HttpClient client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            // Создание POST запроса
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonData))
                    .build();

            staticLogger.debug("Отправка POST запроса на URL: {}", url);

            // Отправка запроса
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            staticLogger.info("Получен ответ со статусом: {}", response.statusCode());

            // Сохранение cookies в файл
            saveCookiesToFile(response.headers(), cookieFilePath);

            // Возврат ответа сервера
            return response.body();

        } catch (Exception e) {
            staticLogger.error("Ошибка при выполнении POST запроса", e);
            return "Ошибка при выполнении запроса: " + e.getMessage();
        }
    }

    private static void saveCookiesToFile(java.net.http.HttpHeaders headers, String cookieFilePath) {
        Logger staticLogger = LoggerFactory.getLogger(ThreeXuiApiClient.class);
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(cookieFilePath))) {
            // Получение всех Set-Cookie заголовков
            List<String> cookies = headers.allValues("set-cookie");

            if (cookies.isEmpty()) {
                staticLogger.warn("Cookies не найдены в ответе сервера");
                return;
            }

            writer.println("# Netscape HTTP Cookie File");
            writer.println("# Generated by Java HTTP Client");

            for (String cookie : cookies) {
                // Парсинг cookie и запись в формате Netscape
                String[] parts = cookie.split(";");
                String[] nameValue = parts[0].split("=", 2);

                if (nameValue.length == 2) {
                    String name = nameValue[0].trim();
                    String value = nameValue[1].trim();

                    // Простой формат для cookies.txt (как в curl)
                    writer.println(String.format("keyvpn.ru\tTRUE\t/\tFALSE\t0\t%s\t%s", name, value));
                }
            }

            staticLogger.info("Cookies успешно сохранены в файл: {}", cookieFilePath);

        } catch (FileNotFoundException e) {
            staticLogger.error("Не удалось найти или создать файл для сохранения cookies: {}", cookieFilePath, e);
        } catch (SecurityException e) {
            staticLogger.error("Недостаточно прав для записи в файл cookies: {}", cookieFilePath, e);
        } catch (IOException e) {
            staticLogger.error("Ошибка ввода-вывода при сохранении cookies в файл: {}", cookieFilePath, e);
        }
    }

    public String addUser(String email) {
        try {
            // Сначала обновляем cookies через логин
            String loginUrl = urlForApi + "/login";
            String loginData = "{\"username\":\"\",\"password\":\"\"}";

            logger.info("Начинается процесс авторизации для добавления пользователя: {}", email);
            String loginResponse = sendPostRequestWithCookies(loginUrl, loginData, cookiesFilePath);
            logger.info("Авторизация завершена успешно");

            // Генерируем UUID и subId
            String uuid = java.util.UUID.randomUUID().toString();
            String subId = generateSubId();

            logger.debug("Сгенерированный UUID: {}", uuid);
            logger.debug("Сгенерированный SubID: {}", subId);

            // Читаем cookies из файла
            String cookies = readCookiesFromFile(cookiesFilePath);

            // Создание небезопасного SSL контекста
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Создание HTTP клиента
            HttpClient client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL) // аналог -L в curl
                    .build();

            // Формирование JSON данных
            String jsonData = String.format(
                    "{\"id\": 1,\"settings\": \"{\\\"clients\\\": [{\\\"id\\\": \\\"%s\\\",\\\"flow\\\": \\\"\\\",\\\"email\\\": \\\"%s\\\",\\\"limitIp\\\": 0,\\\"totalGB\\\": 0,\\\"expiryTime\\\": 0,\\\"enable\\\": true,\\\"tgId\\\": \\\"\\\",\\\"subId\\\":\\\"%s\\\",\\\"reset\\\": 0}]}\"}",
                    uuid, email, subId
            );

            logger.debug("Подготовленные JSON данные для отправки: {}", jsonData);

            // Создание POST запроса с cookies
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(urlForApi + "/panel/api/inbounds/addClient"))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonData));

            // Добавляем cookies если они есть
            if (!cookies.isEmpty()) {
                requestBuilder.header("Cookie", cookies);
                logger.debug("Добавлены cookies в запрос");
            } else {
                logger.warn("Cookies не найдены, запрос отправляется без авторизационных данных");
            }

            HttpRequest request = requestBuilder.build();

            // Отправка запроса
            logger.info("Отправка запроса на добавление клиента: {}", email);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            logger.info("Получен ответ сервера (статус {}): {}", response.statusCode(), response.body());

            // Проверяем корректность ответа сервера
            String expectedResponse = "{\"success\":true,\"msg\":\"Inbound client(s) have been added.\",\"obj\":null}";
            if (response.body().equals(expectedResponse)) {
                // Если ответ совпадает с ожидаемым, генерируем ссылку для подключения
                logger.info("Пользователь {} успешно добавлен", email);
                return generateConnectionLink(subId);
            } else {
                // Если ответ не совпадает, возвращаем сообщение об ошибке
                logger.error("Ошибка при добавлении пользователя: {}, ответ сервера: {}", email, response.body());
                return "Ошибка при добавлении пользователя: ответ сервера не соответствует ожидаемому";
            }

        } catch (Exception e) {
            logger.error("Критическая ошибка при добавлении пользователя: {}", email, e);
            return "Ошибка при выполнении запроса: " + e.getMessage();
        }
    }

    public static String generateSubId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String readCookiesFromFile(String cookieFilePath) {
        Logger staticLogger = LoggerFactory.getLogger(ThreeXuiApiClient.class);
        StringBuilder cookies = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(cookieFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Пропускаем комментарии и пустые строки
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                // Парсим строку cookie в формате Netscape
                String[] parts = line.split("\t");
                if (parts.length >= 7) {
                    String name = parts[5];
                    String value = parts[6];

                    if (cookies.length() > 0) {
                        cookies.append("; ");
                    }
                    cookies.append(name).append("=").append(value);
                }
            }
            
            if (cookies.length() > 0) {
                staticLogger.debug("Успешно прочитаны cookies из файла: {}", cookieFilePath);
            } else {
                staticLogger.warn("Файл cookies пуст или не содержит валидных данных: {}", cookieFilePath);
            }
            
        } catch (FileNotFoundException e) {
            staticLogger.error("Файл с cookies не найден: {}", cookieFilePath, e);
        } catch (SecurityException e) {
            staticLogger.error("Недостаточно прав для чтения файла cookies: {}", cookieFilePath, e);
        } catch (IOException e) {
            staticLogger.error("Ошибка ввода-вывода при чтении cookies из файла: {}", cookieFilePath, e);
        }
        
        return cookies.toString();
    }

    /**
     * Генерирует ссылку для подключения к VPN на основе subId пользователя
     */
    public String generateConnectionLink(String subId) {
        String link = urlForLink + "/" + subId;
        logger.debug("Сгенерирована ссылка для подключения: {}", link);
        return link;
    }

}
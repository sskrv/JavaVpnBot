package java.org.example.yookassa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.YooKassaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class YooKassaPayment {
    private static final Logger log = LoggerFactory.getLogger(YooKassaPayment.class);
    private static final String YOOKASSA_API_URL = "https://api.yookassa.ru/v3/payments";

    private final YooKassaConfig yooKassaConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public YooKassaPayment(YooKassaConfig yooKassaConfig) {
        this.yooKassaConfig = yooKassaConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        log.info("YooKassaPayment initialized with shopID: {}", yooKassaConfig.getShopID());
    }

    /**
     * Creates a payment and returns the confirmation URL
     * @param amount The amount to be paid
     * @param description Payment description
     * @return The URL for the user to complete payment
     */
    public String createPayment(BigDecimal amount, String description) {
        log.info("Creating payment for amount: {} RUB, description: {}", amount, description);

        // Идемпотенс кей это уникальный ключ каждого платежа
        String idempotenceKey = UUID.randomUUID().toString();
        log.debug("Generated idempotence key: {}", idempotenceKey);

        // В головах меняем тип и вставляем уникальный ключ
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotence-Key", idempotenceKey);

        // Basic authentication with shopID and secretKey
        String auth = yooKassaConfig.getShopID() + ":" + yooKassaConfig.getSecretKey();
        // Переводим в формат UTF_8 а потом кодируем в Base64, так как этого требует HTTP
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);
        headers.set("Authorization", authHeader);

        /*
         Вот как выглядит запрос:

        {
            "amount": {
            "value": "100.00",
                    "currency": "RUB"
        },
            "capture": true,
                "confirmation": {
            "type": "redirect",
                    "return_url": "https://t.me/caucas_vpn_bot"
        },
            "description": "Оплата VPN ключа для пользователя + userId"
        }
        */
        Map<String, Object> requestMap = new HashMap<>();

        Map<String, Object> amountMap = new HashMap<>();
        amountMap.put("value", amount.toString());
        amountMap.put("currency", "RUB");

        Map<String, Object> confirmationMap = new HashMap<>();
        confirmationMap.put("type", "redirect");
        confirmationMap.put("return_url", yooKassaConfig.getReturnUrl());

        requestMap.put("amount", amountMap);
        requestMap.put("capture", true);
        requestMap.put("confirmation", confirmationMap);
        requestMap.put("description", description);

        /* Формирование HTTP-запроса
          @requestMap тело запроса
          @headers заголовки
         */
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestMap, headers);

        try {
            log.debug("Sending request to YooKassa API: {}", requestMap);
            ResponseEntity<String> response = restTemplate.exchange(  // Запрос на API
                    YOOKASSA_API_URL,  // Ссылка API
                    HttpMethod.POST,  // Тип запроса
                    requestEntity,  // Сам запрос
                    String.class  // Тип ответа (json в виде строки)
            );

            log.debug("Received response from YooKassa API: {}", response.getBody());

            if (response.getStatusCode() == HttpStatus.OK) {  // Если запрос успешно обработан
                JsonNode rootNode = objectMapper.readTree(response.getBody());  // Превращаем строку json в объект JsonNode
                String confirmationUrl = rootNode.path("confirmation").path("confirmation_url").asText();  // Ссылка для оплаты
                String paymentId = rootNode.path("id").asText();  // ID запроса
                String status = rootNode.path("status").asText();  // Статус запроса

                log.info("Payment created successfully. ID: {}, Status: {}", paymentId, status);
                log.info("Confirmation URL: {}", confirmationUrl);

                return confirmationUrl;  // Возвращаем ссылку на оплату
            } else {
                log.error("Failed to create payment. Status code: {}", response.getStatusCode());
                throw new RuntimeException("Failed to create payment: " + response.getBody());
            }
        } catch (Exception e) {
            log.error("Exception occurred while creating payment", e);
            throw new RuntimeException("Failed to create payment", e);
        }
    }

    /**
     * Checks the status of a payment
     * @param paymentId The ID of the payment to check
     * @return The status of the payment
     */
    public String checkPaymentStatus(String paymentId) {
        log.info("Checking status for payment ID: {}", paymentId);

        // Prepare headers
        HttpHeaders headers = new HttpHeaders();

        // Basic authentication with shopID and secretKey
        String auth = yooKassaConfig.getShopID() + ":" + yooKassaConfig.getSecretKey();
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);
        headers.set("Authorization", authHeader);

        HttpEntity<?> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    YOOKASSA_API_URL + "/" + paymentId,
                    HttpMethod.GET,
                    requestEntity,
                    String.class
            );

            log.debug("Received response from YooKassa API: {}", response.getBody());

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                String status = rootNode.path("status").asText();

                log.info("Payment status retrieved. ID: {}, Status: {}", paymentId, status);

                return status;
            } else {
                log.error("Failed to check payment status. Status code: {}", response.getStatusCode());
                throw new RuntimeException("Failed to check payment status: " + response.getBody());
            }
        } catch (Exception e) {
            log.error("Exception occurred while checking payment status", e);
            throw new RuntimeException("Failed to check payment status", e);
        }
    }
    /**
     * Отменяет платеж в ЮKassa.
     *
     * @param paymentId Идентификатор платежа, который необходимо отменить.
     * @return true, если платеж успешно отменен; false в противном случае.
     */
    public boolean cancelPayment(String paymentId) {
        log.info("Attempting to cancel payment with ID: {}", paymentId);

        // Генерация идемпотентного ключа
        String idempotenceKey = UUID.randomUUID().toString();
        log.debug("Generated idempotence key for cancellation: {}", idempotenceKey);

        // Настройка заголовков HTTP-запроса
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotence-Key", idempotenceKey);

        // Базовая аутентификация с использованием shopID и secretKey
        String auth = yooKassaConfig.getShopID() + ":" + yooKassaConfig.getSecretKey();
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);
        headers.set("Authorization", authHeader);

        // Формирование URL для запроса отмены платежа
        String cancelUrl = YOOKASSA_API_URL + "/" + paymentId + "/cancel";

        // Создание HTTP-запроса
        HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        try {
            // Отправка запроса на отмену платежа
            ResponseEntity<String> response = restTemplate.exchange(
                    cancelUrl,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            log.debug("Received response from YooKassa API for cancellation: {}", response.getBody());

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                String status = rootNode.path("status").asText();

                if ("canceled".equals(status)) {
                    log.info("Payment with ID: {} has been successfully canceled.", paymentId);
                    return true;
                } else {
                    log.warn("Payment with ID: {} could not be canceled. Current status: {}", paymentId, status);
                    return false;
                }
            } else {
                log.error("Failed to cancel payment. Status code: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Exception occurred while canceling payment", e);
            return false;
        }
    }

}
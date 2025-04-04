package org.example.logic;

import org.example.config.BotConfig;
import org.example.db.DatabaseManager;
import org.example.hiddify.HiddifyApiClient;
import org.example.yookassa.YooKassaPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BotLogic extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(BotLogic.class);
    private static final BigDecimal VPN_PRICE = new BigDecimal("100.00");
    private static final long PAYMENT_TIMEOUT_MINUTES = 5;

    private final BotConfig botConfig;
    private final DatabaseManager dbManager;
    private final HiddifyApiClient hiddifyClient;
    private final YooKassaPayment yooKassaPayment;

    // Хранение информации о платежах (paymentId -> PaymentInfo)
    private final Map<String, PaymentInfo> paymentTracker = new ConcurrentHashMap<>();

    // Класс для хранения информации о платежах
    private static class PaymentInfo {
        private final long userId;
        private final String chatId;
        private final Instant createdAt;

        public PaymentInfo(long userId, String chatId) {
            this.userId = userId;
            this.chatId = chatId;
            this.createdAt = Instant.now();
        }

        public long getUserId() {
            return userId;
        }

        public String getChatId() {
            return chatId;
        }

        public boolean isExpired() {
            return createdAt.plus(PAYMENT_TIMEOUT_MINUTES, ChronoUnit.MINUTES).isBefore(Instant.now());
        }
    }

    @Deprecated
    public BotLogic(BotConfig botConfig, HiddifyApiClient hiddifyClient, DatabaseManager dbManager, YooKassaPayment yooKassaPayment) {
        this.botConfig = botConfig;
        this.dbManager = dbManager;
        this.hiddifyClient = hiddifyClient;
        this.yooKassaPayment = yooKassaPayment;
        logger.info("BotLogic initialized with YooKassa payment integration");
    }

    @Override
    public String getBotUsername() {
        return botConfig.getUsername();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }


    // Обрабатываем поступающие обновления. Либо это сообщение, либо нажатие на кнопку
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    // Если нам поступило сообщение старт, то выводим меню, иначе выводим что не понял
    private void handleTextMessage(Message message) {
        long userId = message.getFrom().getId();
        String chatId = message.getChatId().toString();
        String receivedText = message.getText();
        String username = message.getFrom().getUserName();

        logger.info("Received message '{}' from user {} (username: {})", receivedText, userId, username);

        dbManager.addUser(userId);

        if ("/start".equals(receivedText)) {
            sendMainMenu(chatId);
        } else {
            SendMessage answerMessage = new SendMessage();
            answerMessage.setChatId(chatId);
            answerMessage.setText("Некорректное сообщение");
            sendMessage(answerMessage);
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();  // Данные с кнопки
        String chatId = callbackQuery.getMessage().getChatId().toString();
        long userId = callbackQuery.getFrom().getId();

        logger.info("Received callback '{}' from user {}", callbackData, userId);

        // Отдельно обрабатываем кнопку проверки оплаты
        if (callbackData.startsWith("check_payment:")) {  // Если нажали на кнопку проверки оплаты
            String paymentId = callbackData.substring("check_payment:".length());
            checkPaymentStatus(chatId, userId, paymentId);
            return;
        }

        switch (callbackData) {
            case "buy_key" -> handleBuyKeyRequest(chatId, userId);
            case "show_key" -> handleShowExistingKey(chatId, userId);
            case "instructions" -> sendInstructions(chatId);
            case "main_menu" -> sendMainMenu(chatId);
            case "pay_vpn" -> initiatePayment(chatId, userId);
        }
    }

    // Отдельные методы для inline кнопок
    
    /**
     * Создает клавиатуру с одной кнопкой "Назад в меню"
     */
    private InlineKeyboardMarkup createBackToMenuKeyboard() {
        return createKeyboard(
            createButtonRow(createButton("⬅️ Назад в меню", "main_menu"))
        );
    }
    
    /**
     * Создает клавиатуру с двумя кнопками: "Инструкция" и "Назад в меню"
     */
    private InlineKeyboardMarkup createInstructionsAndMenuKeyboard() {
        return createKeyboard(
            createButtonRow(createButton("📖 Инструкция", "instructions")),
            createButtonRow(createButton("⬅️ Назад в меню", "main_menu"))
        );
    }
    
    /**
     * Создает кнопку проверки оплаты и кнопку возврата
     */
    private InlineKeyboardMarkup createPaymentCheckKeyboard(String paymentId) {
        return createKeyboard(
            createButtonRow(createButton("✅ Проверить оплату", "check_payment:" + paymentId)),
            createButtonRow(createButton("⬅️ Отменить и вернуться в меню", "main_menu"))
        );
    }
    
    /**
     * Создает кнопку для создания кнопок Telegram
     */
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
    
    /**
     * Создает кнопку с URL
     */
    private InlineKeyboardButton createUrlButton() {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("📞 Поддержка");
        button.setUrl("https://t.me/caucasian114");
        return button;
    }
    
    /**
     * Создает ряд кнопок
     */
    private List<InlineKeyboardButton> createButtonRow(InlineKeyboardButton... buttons) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        Collections.addAll(row, buttons);
        return row;
    }
    
    /**
     * Создает клавиатуру из рядов кнопок
     */
    @SafeVarargs  // Нужно для подавления 
    private InlineKeyboardMarkup createKeyboard(List<InlineKeyboardButton>... rows) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        Collections.addAll(keyboard, rows);
        markup.setKeyboard(keyboard);
        return markup;
    }
    
    // Отдельные методы для сообщений
    
    /**
     * Создает базовое сообщение с клавиатурой
     */
    private SendMessage createMessage(String chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(keyboard);
        return message;
    }
    
    /**
     * Создает HTML сообщение с клавиатурой
     */
    private SendMessage createHtmlMessage(String chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = createMessage(chatId, text, keyboard);
        message.setParseMode("HTML");
        return message;
    }
    
    /**
     * Создает Markdown сообщение с клавиатурой
     */
    private SendMessage createMarkdownMessage(String chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = createMessage(chatId, text, keyboard);
        message.enableMarkdown(true);
        return message;
    }

    /**
     * Безопасно отправляет сообщение с логированием
     */
    private void sendMessage(SendMessage message) {
        try {
            execute(message);
            logger.info("Message sent successfully to chat {}", message.getChatId());
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chat {}: {}", message.getChatId(), e.getMessage());
        }
    }
    
    /**
     * Отправляет сообщение об ошибке с кнопкой возврата в меню
     */
    private void sendErrorMessage(String chatId, String errorText) {
        sendMessage(createMessage(chatId, errorText, createBackToMenuKeyboard()));
    }

    /**
     * Отправляет сообщение об ошибке с кнопкой поддержки
     */
    private void sendSupportErrorMessage(String chatId, String errorText) {
        InlineKeyboardMarkup keyboard = createKeyboard(
            createButtonRow(createUrlButton()),
            createButtonRow(createButton("⬅️ Вернуться в меню", "main_menu"))
        );
        
        sendMessage(createMessage(chatId, errorText, keyboard));
    }
    
    // Логика бота

    /**
     * Отправляет главное меню с инлайн-кнопками
     */
    private void sendMainMenu(String chatId) {
        InlineKeyboardMarkup keyboard = createKeyboard(
            createButtonRow(createButton("\uD83D\uDCB3 Купить ключ", "buy_key")),
            createButtonRow(
                createButton("\uD83D\uDD11 Мой ключ", "show_key"),
                createButton("\uD83D\uDCD6 Инструкция", "instructions")
            )
        );
        
        SendMessage message = createMessage(
            chatId, 
            "\uD83C\uDF0D Добро пожаловать в " + botConfig.getNickname() + "! \uD83D\uDD12",
            keyboard
        );
        message.setParseMode("HTML");
        
        sendMessage(message);
    }

    /**
     * Обрабатывает запрос на покупку ключа
     */
    private void handleBuyKeyRequest(String chatId, long userId) {
        // Сначала проверяем, есть ли у пользователя уже ключ
        String existingKey = dbManager.getVpnKey(userId);

        if (existingKey != null && !existingKey.isEmpty()) {
            // У пользователя уже есть ключ
            showExistingKeyDetails(chatId, userId, existingKey);
        } else {
            // Отправляем предложение об оплате
            sendPaymentOffer(chatId);
        }
    }

    /**
     * Отображает детали существующего ключа
     */
    private void showExistingKeyDetails(String chatId, long userId, String key) {
        String keyDate = dbManager.getKeyCreationDate(userId);
        String dateInfo = (keyDate != null) ? "\uD83D\uDDD3️ Ключ создан: " + keyDate : "";
        String formattedKey = "<code>" + key + "</code>";
        
        String text = "\uD83D\uDD27 Ваш ключ VPN:\n\n" + formattedKey +
                "\n\n" + "<i>⬆ Нажмите чтобы скопировать</i>" +
                "\n\n" + dateInfo + "\n" +
                "\n❗ Этот ключ действителен 30 дней с момента получения. \n\n" +
                "⚙️ Для инструкции по подключению нажмите кнопку \"Инструкция\".";
                
        sendMessage(createHtmlMessage(chatId, text, createInstructionsAndMenuKeyboard()));
    }

    /**
     * Отправляет предложение об оплате
     */
    private void sendPaymentOffer(String chatId) {
        InlineKeyboardMarkup keyboard = createKeyboard(
                // Если ты нажал на pay_vpn, то выполнится initiatePayment
                // Если ты нажал на main_menu, то выполнится sendMainMenu
            createButtonRow(createButton("Оплатить " + VPN_PRICE + " руб.", "pay_vpn")),
            createButtonRow(createButton("⬅️ Назад в меню", "main_menu"))
        );
        
        String text = "💳 Для получения ключа VPN необходимо произвести оплату в размере " + VPN_PRICE + " руб.";
        
        sendMessage(createHtmlMessage(chatId, text, keyboard));
    }

    /**
     * Инициирует процесс оплаты через YooKassa
     */
    private void initiatePayment(String chatId, long userId) {
        logger.info("Initiating payment for user {}", userId);

        try {
            // Создаем платеж через YooKassa
            String description = "Оплата VPN ключа для пользователя " + userId;
            // Метод createPayment возвращает нам ссылку для оплаты
            String confirmationUrl = yooKassaPayment.createPayment(VPN_PRICE, description);

            // Извлекаем payment_id из URL
            String paymentId = confirmationUrl.substring(confirmationUrl.lastIndexOf("=") + 1);
            
            // Сохраняем информацию о платеже с timestamp
            paymentTracker.put(paymentId, new PaymentInfo(userId, chatId));
            
            String text = "💳 Для оплаты перейдите по ссылке ниже:\n\n" + confirmationUrl +
                    "\n\n⏳ После оплаты нажмите кнопку 'Проверить оплату'";
            
            sendMessage(createHtmlMessage(chatId, text, createPaymentCheckKeyboard(paymentId)));
            
            logger.info("Payment link sent to user {}, paymentId: {}", userId, paymentId);
        } catch (Exception e) {
            logger.error("Error initiating payment for user {}: {}", userId, e.getMessage());
            sendErrorMessage(chatId, "❌ Произошла ошибка при создании платежа. Пожалуйста, попробуйте позже.");
        }
    }

    /**
     * Проверяет статус оплаты
     */
    private void checkPaymentStatus(String chatId, long userId, String paymentId) {
        logger.info("Checking payment status for user {}, paymentId: {}", userId, paymentId);

        try {
            String status = yooKassaPayment.checkPaymentStatus(paymentId);
            logger.info("Payment status for paymentId {}: {}", paymentId, status);

            switch (status) {
                case "succeeded" -> processSuccessfulPayment(chatId, userId);
                case "pending" -> {
                    String text = "⏳ Ваш платеж обрабатывается. Пожалуйста, подождите немного и проверьте статус снова.";
                    InlineKeyboardMarkup keyboard = createKeyboard(
                        createButtonRow(createButton("🔄 Проверить снова", "check_payment:" + paymentId)),
                        createButtonRow(createButton("⬅️ Вернуться в меню", "main_menu"))
                    );
                    sendMessage(createMessage(chatId, text, keyboard));
                }
                default -> {
                    String text = "❌ Платеж не был завершен. Статус: " + status + ". Пожалуйста, попробуйте еще раз.";
                    InlineKeyboardMarkup keyboard = createKeyboard(
                        createButtonRow(createButton("🔄 Попробовать снова", "buy_key")),
                        createButtonRow(createButton("⬅️ Вернуться в меню", "main_menu"))
                    );
                    sendMessage(createMessage(chatId, text, keyboard));
                }
            }
        } catch (Exception e) {
            logger.error("Error checking payment status for user {}: {}", userId, e.getMessage());
            sendErrorMessage(chatId, "❌ Произошла ошибка при проверке статуса платежа. Пожалуйста, попробуйте позже.");
        }
    }

    /**
     * Обрабатывает успешную оплату и генерирует VPN ключ
     */
    private void processSuccessfulPayment(String chatId, long userId) {
        logger.info("Processing successful payment for user {}", userId);

        try {
            // Отправляем сообщение о начале генерации ключа
            sendMessage(createMessage(
                chatId, 
                "✅ Оплата успешно произведена! ⏳ Генерируем для вас ключ VPN...",
                null
            ));

            // Создаем VPN ключ
            String vpnKey = hiddifyClient.createUser(userId, 100, 30);

            if (vpnKey != null) {
                // Обновляем дату создания ключа и сохраняем ключ в базе данных
                // А также выводим текст о готовности ключа
                updateAndSave(chatId, userId, vpnKey);

                logger.info("VPN key successfully generated and sent to user {}", userId);
            } else {
                logger.error("Failed to generate VPN key for user {} after successful payment", userId);
                sendSupportErrorMessage(chatId, 
                    "❌ Не удалось сгенерировать ключ VPN. Пожалуйста, попробуйте позже или обратитесь в поддержку."
                );
            }
        } catch (Exception e) {
            logger.error("Error processing successful payment for user {}: {}", userId, e.getMessage());
            sendSupportErrorMessage(chatId, 
                "❌ Произошла ошибка при генерации ключа. Пожалуйста, обратитесь в поддержку."
            );
        }
    }

    private void updateAndSave(String chatId, long userId, String vpnKey) {
        dbManager.updateKeyCreationDate(userId);
        dbManager.saveVpnKey(userId, vpnKey);

        String formattedKey = "<code>" + vpnKey + "</code>";
        String text = "✅ Ваш ключ VPN готов:\n\n" + formattedKey +
                "\n" + "<i>⬆ Нажмите чтобы скопировать</i>" +
                "\n\n❗ Этот ключ действителен на 30 дней и имеет лимит 100 ГБ трафика.";

        InlineKeyboardMarkup keyboard = createKeyboard(
            createButtonRow(createButton("📖 Инструкция по подключению", "instructions")),
            createButtonRow(createButton("⬅️ В главное меню", "main_menu"))
        );

        sendMessage(createHtmlMessage(chatId, text, keyboard));
    }

    /**
     * Показывает существующий ключ пользователя
     */
    private void handleShowExistingKey(String chatId, long userId) {
        String existingKey = dbManager.getVpnKey(userId);

        if (existingKey != null && !existingKey.isEmpty()) {
            showExistingKeyDetails(chatId, userId, existingKey);
        } else {
            InlineKeyboardMarkup keyboard = createKeyboard(
                createButtonRow(createButton("💳 Купить ключ", "buy_key")),
                createButtonRow(createButton("⬅️ В главное меню", "main_menu"))
            );
            
            sendMessage(createMessage(chatId, "\uD83D\uDCC9 У вас еще нет ключа VPN.", keyboard));
        }
    }

    /**
     * Отправляет инструкцию по подключению с кнопкой возврата в главное меню
     */
    private void sendInstructions(String chatId) {
        String instructions = """
    📱 *Инструкция по подключению к VPN*

    1️⃣ *Установите приложение Hiddify:*
    ▪️ Android: [Google Play](https://play.google.com/store/apps/details?id=app.hiddify.com)
    ▪️ iOS: [App Store](https://apps.apple.com/app/hiddify/id6444472349)
    ▪️ Windows: [Скачать](https://github.com/hiddify/hiddify-next/releases/latest)

    2️⃣ *Подключение к серверу:*
    ▪️ Скопируйте купленный ключ
    ▪️ Откройте установленное приложение
    ▪️ Нажмите на + в правом верхнем углу
    ▪️ Выберите "Импорт из URL"
    ▪️ Готово!

    3️⃣ *Использование:*
    ▪️ Выберите добавленный сервер
    ▪️ Нажмите кнопку подключения (круглая кнопка посередине)
    ▪️ Готово! Вы подключены к VPN

    ❓ Если у вас возникли вопросы, обратитесь в [поддержку](https://t.me/caucasian114).
    """;

        sendMessage(createMarkdownMessage(chatId, instructions, createBackToMenuKeyboard()));
    }
}

package org.example.logic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.example.config.BotConfig;
import org.example.db.DatabaseManager;
import org.example.three_x_ui.ThreeXuiApiClient;
import org.example.yookassa.YooKassaPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Service
public class BotLogic extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(BotLogic.class);
    private static final BigDecimal VPN_PRICE = new BigDecimal("99.00");

    private final BotConfig botConfig;
    private final DatabaseManager dbManager;
    private final ThreeXuiApiClient threeXuiClient;
    private final YooKassaPayment yooKassaPayment;

    public BotLogic(BotConfig botConfig, ThreeXuiApiClient threeXuiClient, DatabaseManager dbManager, YooKassaPayment yooKassaPayment) {
        this.botConfig = botConfig;
        this.dbManager = dbManager;
        this.threeXuiClient = threeXuiClient;
        this.yooKassaPayment = yooKassaPayment;
        logger.info("BotLogic initialized with YooKassa payment integration and 3X-UI API client");
    }

    @Override
    public String getBotUsername() {
        return botConfig.getUsername();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

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
        String callbackData = callbackQuery.getData();
        String chatId = callbackQuery.getMessage().getChatId().toString();
        long userId = callbackQuery.getFrom().getId();
        int messageId = callbackQuery.getMessage().getMessageId();
        String username = callbackQuery.getFrom().getUserName();

        logger.info("Received callback '{}' from user {}", callbackData, userId, username);

        if (callbackData.startsWith("check_payment:")) {
            String paymentId = callbackData.substring("check_payment:".length());
            checkPaymentStatus(chatId, userId, paymentId, username);
            removeInlineKeyboard(chatId, messageId);
            return;
        }

        if (callbackData.startsWith("cancel_payment:")) {
            String paymentId = callbackData.substring("cancel_payment:".length());
            cancelPayment(chatId, userId, paymentId);
            removeInlineKeyboard(chatId, messageId);
            return;
        }

        switch (callbackData) {
            case "buy_key" -> {
                handleBuyKeyRequest(chatId, userId);
                removeInlineKeyboard(chatId, messageId);
            }
            case "show_key" -> {
                handleShowExistingKey(chatId, userId);
                removeInlineKeyboard(chatId, messageId);
            }
            case "instructions" -> {
                sendInstructions(chatId);
                removeInlineKeyboard(chatId, messageId);
            }
            case "main_menu" -> {
                sendMainMenu(chatId);
                removeInlineKeyboard(chatId, messageId);
            }
            case "pay_vpn" -> {
                initiatePayment(chatId, userId);
                removeInlineKeyboard(chatId, messageId);
            }
        }
    }

    // --- Клавиатуры и кнопки ---
    private InlineKeyboardMarkup createBackToMenuKeyboard() {
        return createKeyboard(
            createButtonRow(createButton("⬅️ Назад в меню", "main_menu"))
        );
    }
    private InlineKeyboardMarkup createInstructionsAndMenuKeyboard() {
        return createKeyboard(
            createButtonRow(createButton("📖 Инструкция", "instructions")),
            createButtonRow(createButton("⬅️ Назад в меню", "main_menu"))
        );
    }
    private InlineKeyboardMarkup createPaymentCheckKeyboard(String paymentId) {
        return createKeyboard(
            createButtonRow(createButton("✅ Проверить оплату", "check_payment:" + paymentId)),
            createButtonRow(createButton("⬅️ Отменить и вернуться в меню", "cancel_payment:" + paymentId))
        );
    }
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
    private InlineKeyboardButton createUrlButton() {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("📞 Поддержка");
        button.setUrl("https://t.me/caucasianO5");
        return button;
    }
    private List<InlineKeyboardButton> createButtonRow(InlineKeyboardButton... buttons) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        Collections.addAll(row, buttons);
        return row;
    }
    @SafeVarargs
    private InlineKeyboardMarkup createKeyboard(List<InlineKeyboardButton>... rows) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        Collections.addAll(keyboard, rows);
        markup.setKeyboard(keyboard);
        return markup;
    }

    // --- Формирование сообщений ---
    private SendMessage createMessage(String chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(keyboard);
        return message;
    }
    private SendMessage createHtmlMessage(String chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = createMessage(chatId, text, keyboard);
        message.setParseMode("HTML");
        return message;
    }
    private SendMessage createMarkdownMessage(String chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = createMessage(chatId, text, keyboard);
        message.enableMarkdown(true);
        return message;
    }
    private void sendMessage(SendMessage message) {
        try {
            execute(message);
            logger.info("Message sent successfully to chat {}", message.getChatId());
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chat {}: {}", message.getChatId(), e.getMessage());
        }
    }
    private void sendErrorMessage(String chatId, String errorText) {
        sendMessage(createMessage(chatId, errorText, createBackToMenuKeyboard()));
    }
    private void sendSupportErrorMessage(String chatId, String errorText) {
        InlineKeyboardMarkup keyboard = createKeyboard(
            createButtonRow(createUrlButton()),
            createButtonRow(createButton("⬅️ Вернуться в меню", "main_menu"))
        );
        sendMessage(createMessage(chatId, errorText, keyboard));
    }
    private void sendCancelPaymentMessage(String chatId, String errorText) {
        sendMessage(createMessage(chatId, errorText, null));
    }
    private void removeInlineKeyboard(String chatId, int messageId) {
        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(chatId);
        editMessageReplyMarkup.setMessageId(messageId);
        editMessageReplyMarkup.setReplyMarkup(new InlineKeyboardMarkup(Collections.emptyList()));
        try {
            execute(editMessageReplyMarkup);
        } catch (TelegramApiException e) {
            logger.error("Failed to remove inline keyboard for chat {}: {}", chatId, e.getMessage());
        }
    }

    // --- Логика VPN-продаж ---

    private void sendMainMenu(String chatId) {
        InlineKeyboardMarkup keyboard = createKeyboard(
            createButtonRow(createButton("\uD83D\uDCB3 Купить ключ", "buy_key")),
            createButtonRow(
                createButton("\uD83D\uDD11 Мой ключ", "show_key"),
                createButton("📖 Инструкция", "instructions")
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

    private void handleBuyKeyRequest(String chatId, long userId) {
        String existingKey = dbManager.getVpnKey(userId);
        if (existingKey != null && !existingKey.isEmpty()) {
            showExistingKeyDetails(chatId, userId, existingKey);
        } else {
            sendPaymentOffer(chatId);
        }
    }
    private void showExistingKeyDetails(String chatId, long userId, String key) {
        String keyDate = dbManager.getKeyCreationDate(userId);
        String dateInfo = (keyDate != null) ? "\uD83D\uDDD3️ Ключ создан: " + keyDate : "";
        String formattedKey = "<code>" + key + "</code>";

        String text = "\uD83D\uDD27 Ваша VPN-ссылка:\n\n" + formattedKey +
                "\n\n" + "<i>⬆ Нажмите чтобы скопировать</i>" +
                "\n\n" + dateInfo + "\n" +
                "\n❗ Эта ссылка действительна 30 дней с момента получения. \n\n" +
                "⚙️ Для инструкции по подключению нажмите кнопку \"Инструкция\".";
        sendMessage(createHtmlMessage(chatId, text, createInstructionsAndMenuKeyboard()));
    }
    private void sendPaymentOffer(String chatId) {
        InlineKeyboardMarkup keyboard = createKeyboard(
            createButtonRow(createButton("Оплатить " + VPN_PRICE + " руб.", "pay_vpn")),
            createButtonRow(createButton("⬅️ Назад в меню", "main_menu"))
        );
        String text = "💳 Для получения доступа к VPN необходимо произвести оплату в размере " + VPN_PRICE + " руб.";
        sendMessage(createHtmlMessage(chatId, text, keyboard));
    }
    private void initiatePayment(String chatId, long userId) {
        logger.info("Initiating payment for user {}", userId);
        try {
            String description = "Оплата VPN подписки для пользователя " + userId;
            String confirmationUrl = yooKassaPayment.createPayment(VPN_PRICE, description);
            String paymentId = confirmationUrl.substring(confirmationUrl.lastIndexOf("=") + 1);

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
    private void checkPaymentStatus(String chatId, long userId, String paymentId, String username) {
        logger.info("Checking payment status for user {}, paymentId: {}", userId, paymentId);
        try {
            String status = yooKassaPayment.checkPaymentStatus(paymentId);
            logger.info("Payment status for paymentId {}: {}", paymentId, status);

            switch (status) {
                case "succeeded" -> processSuccessfulPayment(chatId, userId, username);
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
    private void cancelPayment(String chatId, long userId, String paymentId) {
        logger.info("Cancel payment for user {}, paymentId: {}", userId, paymentId);
        try {
            yooKassaPayment.cancelPayment(paymentId);
            sendCancelPaymentMessage(chatId, "\uD83D\uDCC9 Платёж отменён");
            logger.info("Payment successfully canceled for user {}, paymentId: {}", userId, paymentId);
        } catch (Exception e) {
            logger.error("Error cancel payment for user {}: {}", userId, e.getMessage());
            sendErrorMessage(chatId, "❌ Произошла ошибка при отмене платежа. Пожалуйста, попробуйте позже.");
        }
    }

    /**
     * Обрабатывает успешную оплату и генерирует VPN ключ (создаёт пользователя в inbound)
     */
    private void processSuccessfulPayment(String chatId, long userId, String username) {
        logger.info("Processing successful payment for user {}", userId);

        try {
            sendMessage(createMessage(
                chatId,
                "✅ Оплата успешно произведена! ⏳ Генерируем для вас доступ к VPN...",
                null
            ));

            // Вызываем addUser, который теперь возвращает ссылку для подключения
            String vpnLink = threeXuiClient.addUser(username);

            // Проверяем, что ссылка не начинается с "Ошибка" (что указывает на проблему)
            if (vpnLink != null && !vpnLink.startsWith("Ошибка")) {
                updateAndSave(chatId, userId, vpnLink);
                logger.info("VPN subscription successfully generated and sent to user {}", userId);
            } else {
                logger.error("Failed to add VPN client after successful payment, user {}: {}", userId, vpnLink);
                sendSupportErrorMessage(chatId,
                    "❌ Не удалось создать подписку VPN. Пожалуйста, попробуйте позже или обратитесь в поддержку."
                );
            }
        } catch (Exception e) {
            logger.error("Error processing successful payment for user {}: {}", userId, e.getMessage());
            sendSupportErrorMessage(chatId,
                "❌ Произошла ошибка при создании подписки. Пожалуйста, обратитесь в поддержку."
            );
        }
    }

    private void updateAndSave(String chatId, long userId, String vpnLink) {
        dbManager.updateKeyCreationDate(userId);
        dbManager.saveVpnKey(userId, vpnLink);

        String formattedKey = "<code>" + vpnLink + "</code>";
        String text = "✅ Ваша VPN-ссылка готова:\n\n" + formattedKey +
                "\n" + "<i>⬆ Нажмите чтобы скопировать</i>" +
                "\n\n❗ Эта ссылка действительна на 30 дней.";

        InlineKeyboardMarkup keyboard = createKeyboard(
            createButtonRow(createButton("📖 Инструкция по подключению", "instructions")),
            createButtonRow(createButton("⬅️ В главное меню", "main_menu"))
        );

        sendMessage(createHtmlMessage(chatId, text, keyboard));
    }
    private void handleShowExistingKey(String chatId, long userId) {
        String existingKey = dbManager.getVpnKey(userId);
        if (existingKey != null && !existingKey.isEmpty()) {
            showExistingKeyDetails(chatId, userId, existingKey);
        } else {
            InlineKeyboardMarkup keyboard = createKeyboard(
                createButtonRow(createButton("💳 Купить доступ", "buy_key")),
                createButtonRow(createButton("⬅️ В главное меню", "main_menu"))
            );
            sendMessage(createMessage(chatId, "\uD83D\uDCC9 У вас еще нет доступа к VPN.", keyboard));
        }
    }
    private void sendInstructions(String chatId) {
        String instructions = """
    📱 *Инструкция по подключению к VPN*

    1️⃣ *Установите приложение V2Box:*
    ▪️ Android: [Google Play](https://play.google.com/store/apps/details?id=com.v2ray.ang)
    ▪️ iOS: [App Store](https://apps.apple.com/app/v2box-v2ray-client/id6446814690)
    ▪️ Windows: [Скачать V2rayN](https://github.com/2dust/v2rayN/releases/latest)

    2️⃣ *Подключение к серверу:*
    ▪️ Скопируйте полученную VPN-ссылку
    ▪️ Откройте установленное приложение
    ▪️ Нажмите на + в правом верхнем углу
    ▪️ Выберите "Импорт из буфера обмена"
    ▪️ Готово!

    3️⃣ *Использование:*
    ▪️ Выберите добавленный сервер
    ▪️ Нажмите кнопку подключения 
    ▪️ Готово! Вы подключены к VPN

    ❓ Если у вас возникли вопросы, обратитесь в [поддержку](https://t.me/caucasianO5).
    """;
        sendMessage(createMarkdownMessage(chatId, instructions, createBackToMenuKeyboard()));
    }
}
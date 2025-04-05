package org.example.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Repository
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Добавляет нового пользователя или обновляет существующего
     *
     * @param userId ID пользователя в Telegram
     */
    @Transactional
    public void addUser(long userId) {
        try {
            User user = entityManager.find(User.class, userId);
            if (user == null) {
                user = new User(userId);
                user.setKey("");
                user.setData("");
                logger.info("Adding new user with ID: {}", userId);
                entityManager.persist(user);
            } else {
                entityManager.merge(user);
            }

            logger.info("User with ID: {} added/updated successfully", userId);
        } catch (Exception e) {
            logger.error("Error adding/updating user: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Сохраняет VPN ключ для пользователя
     *
     * @param userId ID пользователя в Telegram
     * @param vpnKey Ключ VPN
     */
    @Transactional
    public void saveVpnKey(long userId, String vpnKey) {
        try {
            User user = entityManager.find(User.class, userId);
            if (user == null) {
                user = new User(userId);
                logger.info("Creating new user with ID: {} for VPN key", userId);
                entityManager.persist(user);
            }

            user.setKey(vpnKey);

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            user.setData(now.format(formatter));

            entityManager.merge(user);

            logger.info("VPN key saved for user: {}", userId);
        } catch (Exception e) {
            logger.error("Error saving VPN key: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Получает VPN ключ пользователя
     * @param userId ID пользователя в Telegram
     * @return Ключ VPN или null, если ключ не найден
     */
    @Transactional(readOnly = true)
    public String getVpnKey(long userId) {
        try {
            User user = entityManager.find(User.class, userId);

            if (user != null && user.getKey() != null && !user.getKey().isEmpty()) {
                logger.info("Retrieved VPN key for user: {}", userId);
                return user.getKey();
            }

            logger.info("No VPN key found for user: {}", userId);
            return null;
        } catch (Exception e) {
            logger.error("Error getting VPN key: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Получает дату создания ключа пользователя
     * @param userId ID пользователя в Telegram
     * @return Дата в виде строки или null, если дата не найдена
     */
    @Transactional(readOnly = true)
    public String getKeyCreationDate(long userId) {
        try {
            User user = entityManager.find(User.class, userId);

            if (user != null && user.getData() != null && !user.getData().isEmpty()) {
                return user.getData();
            }

            return null;
        } catch (Exception e) {
            logger.error("Error getting key creation date: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Обновляет дату создания ключа пользователя.
     * Понадобится нам для обновления даты при обновлении подписки.
     * @param userId ID пользователя в Telegram
     */
    @Transactional
    public void updateKeyCreationDate(long userId) {
        try {
            User user = entityManager.find(User.class, userId);
            if (user != null) {
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                user.setData(now.format(formatter));

                entityManager.merge(user);

                logger.info("Key creation date updated for user: {}", userId);
                return;
            }

            logger.info("No user found to update key creation date: {}", userId);
        } catch (Exception e) {
            logger.error("Error updating key creation date: {}", e.getMessage());
            throw e;
        }
    }
}
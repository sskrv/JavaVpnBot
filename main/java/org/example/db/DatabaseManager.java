package org.example.db;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Repository
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

    /**
     * Фабрика сессий создается один раз, т.к она очень тяжелая.
     * После создания мы уже можем открывать сессию и взаимодействовать с бд.
     */
    private static final SessionFactory sessionFactory = new Configuration()  // Создаём сессию hibernate
            .configure("hibernate.cfg.xml")  // Конфигурацию берем из файла
            .addAnnotatedClass(User.class)  // Добавляем сущность (может потом еще сделаю)
            .buildSessionFactory();

    /**
     * Добавляет нового пользователя или обновляет существующего
     *
     * @param userId ID пользователя в Telegram
     */
    public void addUser(long userId) {
        Transaction transaction = null;
        try (Session session = sessionFactory.openSession()) {  // Открываем сессию
            transaction = session.beginTransaction();  // Открываем транзактион для добавления данных в бд

            User user = session.get(User.class, userId);
            if (user == null) {
                user = new User(userId);
                // Инициализируем пустые значения для новых пользователей
                user.setKey("");
                user.setData("");
                logger.info("Adding new user with ID: {}", userId);
            }
            // После данного участка кода у нас есть объект, но он отсоединен от бд

            session.merge(user);  // С помощью этой команды hibernate знает, что есть изменения, но бд не знает
            transaction.commit();  // А вот после этого бд тоже знает
//            session.close();  // Вызывается автоматически

            logger.info("User with ID: {} added/updated successfully", userId);
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();  // Откатываем транзакцию в случае ошибки
            }
            logger.error("Error adding/updating user: {}", e.getMessage());
        }
    }

    /**
     * Сохраняет VPN ключ для пользователя
     *
     * @param userId ID пользователя в Telegram
     * @param vpnKey Ключ VPN
     */
    public void saveVpnKey(long userId, String vpnKey) {
        Transaction transaction = null;
        try (Session session = sessionFactory.openSession()) {
            transaction = session.beginTransaction();

            User user = session.get(User.class, userId);
            if (user == null) {
                user = new User(userId);
                logger.info("Creating new user with ID: {} for VPN key", userId);
            }

            // Сохраняем ключ
            user.setKey(vpnKey);

            // Формируем и сохраняем текущую дату
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            user.setData(now.format(formatter));  // Сохраняем дату в формате заданном выше

            session.merge(user);
            transaction.commit();
//            session.close();  // Вызывается автоматически

            logger.info("VPN key saved for user: {}", userId);
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();  // Откатываем транзакцию в случае ошибки
            }
            logger.error("Error saving VPN key: {}", e.getMessage());
        }
    }

    /**
     * Получает VPN ключ пользователя
     * @param userId ID пользователя в Telegram
     * @return Ключ VPN или null, если ключ не найден
     */
    public String getVpnKey(long userId) {
        try (Session session = sessionFactory.openSession()) {
            User user = session.get(User.class, userId);

            if (user != null && user.getKey() != null && !user.getKey().isEmpty()) {
                logger.info("Retrieved VPN key for user: {}", userId);
                return user.getKey();
            }

//            session.close();  // Вызывается автоматически

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
    public String getKeyCreationDate(long userId) {
        try (Session session = sessionFactory.openSession()) {
            User user = session.get(User.class, userId);

            if (user != null && user.getData() != null && !user.getData().isEmpty()) {
                return user.getData();
            }

//            session.close();  // Вызывается автоматически

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
    public void updateKeyCreationDate(long userId) {
        Transaction transaction = null;
        try (Session session = sessionFactory.openSession()) {
            transaction = session.beginTransaction();

            User user = session.get(User.class, userId);
            if (user != null) {
                // Формируем и сохраняем текущую дату
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                user.setData(now.format(formatter));

                session.merge(user);
                transaction.commit();

//            session.close();  // Вызывается автоматически

                logger.info("Key creation date updated for user: {}", userId);
                return;
            }

            logger.info("No user found to update key creation date: {}", userId);
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();  // Откатываем транзакцию в случае ошибки
            }
            logger.error("Error updating key creation date: {}", e.getMessage());
        }
    }
}
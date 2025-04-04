package java.org.example.db;

import jakarta.persistence.*;

// Данная сущность нам нужна для сохранения данных в бд

@Entity
@Table(name = "users")
public class User {
    @Id
    private Long id;

    @Column(name = "user_key", nullable = false)
    private String key;

    @Column(name = "user_data", nullable = false)
    private String data;

    public User() {}

    public User(Long id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}

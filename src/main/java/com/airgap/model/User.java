package com.airgap.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "users")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 100, nullable = true)
    private String email;

    @Column(name = "default_direction", columnDefinition = "TEXT")
    private String defaultDirection = "Help me understand why this exists, what it is, and key practical aspects.";

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Date createdAt;

    public User() {
    }

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.defaultDirection = "Help me understand why this exists, what it is, and key practical aspects.";
    }

    public User(String username, String password, String email) {
        this(username, password);
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDefaultDirection() {
        return (defaultDirection != null && !defaultDirection.isBlank())
                ? defaultDirection
                : "Help me understand why this exists, what it is, and key practical aspects.";
    }

    public void setDefaultDirection(String defaultDirection) {
        this.defaultDirection = defaultDirection;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}

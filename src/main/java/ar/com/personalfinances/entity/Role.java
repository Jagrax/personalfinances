package ar.com.personalfinances.entity;

import lombok.Getter;

@Getter
public enum Role {
    USER("User"),
    ADMIN("Admin");

    private final String value;

    Role(String value) {
        this.value = value;
    }
}
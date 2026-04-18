package com.clairtax.backend.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MaritalStatus {
    SINGLE("single"),
    MARRIED("married"),
    PREVIOUSLY_MARRIED("previously_married");

    private final String value;

    MaritalStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MaritalStatus fromValue(String value) {
        for (MaritalStatus maritalStatus : values()) {
            if (maritalStatus.value.equalsIgnoreCase(value)) {
                return maritalStatus;
            }
        }

        throw new IllegalArgumentException("Unsupported marital status: " + value);
    }
}

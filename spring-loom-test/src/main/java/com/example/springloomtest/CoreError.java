package com.example.springloomtest;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
public enum CoreError implements CoreErrorEnumInterface {
    REQUEST_TIMEOUT("REQUEST_TIMEOUT", CoreErrorCategory.UNEXPECTED, HttpStatus.REQUEST_TIMEOUT.value()),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", CoreErrorCategory.UNEXPECTED, HttpStatus.INTERNAL_SERVER_ERROR.value());

    private String errorMessageFriendly;
    private CoreErrorCategory errorCategory;
    private Integer errorHttpCode;

    public int getErrorHttpCode() {
        return this.errorHttpCode;
    }

    public CoreErrorCategory getErrorCategory() {
        return errorCategory;
    }

    public String getErrorMessageFriendly() {
        return this.errorMessageFriendly;
    }
}


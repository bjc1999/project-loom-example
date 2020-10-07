package com.example.springloomtest;

public interface CoreErrorEnumInterface {
    // Will be automatically implemented by Enum.class
    public String name();
    // End
    public int getErrorHttpCode();

    public CoreErrorCategory getErrorCategory();

    public String getErrorMessageFriendly();
}

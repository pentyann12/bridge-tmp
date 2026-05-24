package com.example.dto;

/**
 * CORBA Any を型情報付きで表現する DTO ラッパー。
 */
public class AnyValue {
    public String type;
    public Object value;

    public AnyValue() {
    }

    public AnyValue(String type, Object value) {
        this.type = type;
        this.value = value;
    }
}

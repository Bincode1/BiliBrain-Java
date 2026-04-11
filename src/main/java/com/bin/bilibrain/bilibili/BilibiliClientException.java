package com.bin.bilibrain.bilibili;

public class BilibiliClientException extends RuntimeException {
    public BilibiliClientException(String message) {
        super(message);
    }

    public BilibiliClientException(String message, Throwable cause) {
        super(message, cause);
    }
}

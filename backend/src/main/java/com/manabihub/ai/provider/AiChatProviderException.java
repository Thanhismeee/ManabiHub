package com.manabihub.ai.provider;

public class AiChatProviderException extends RuntimeException {

    public AiChatProviderException(String message) {
        super(message);
    }

    public AiChatProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}

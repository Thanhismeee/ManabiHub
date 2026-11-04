package com.manabihub.identity.service;

/** Delivery boundary for phone verification messages. */
public interface SmsSender {
    void send(String phoneNumber, String message);
}

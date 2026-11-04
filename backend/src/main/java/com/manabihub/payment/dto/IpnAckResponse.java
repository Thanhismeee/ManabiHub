package com.manabihub.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Acknowledgement body returned to VNPay from the IPN endpoint. VNPay expects the exact
 * JSON keys {@code RspCode} and {@code Message} and uses {@code RspCode} to decide whether
 * to stop retrying the callback.
 */
public record IpnAckResponse(
        @JsonProperty("RspCode") String rspCode,
        @JsonProperty("Message") String message
) {
    public static IpnAckResponse of(String rspCode, String message) {
        return new IpnAckResponse(rspCode, message);
    }
}

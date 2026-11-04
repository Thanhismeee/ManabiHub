package com.manabihub.payment.gateway;

import com.manabihub.order.entity.Order;
import com.manabihub.payment.config.VnPayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnPayGatewayTest {

    private VnPayGateway gateway;
    private Order order;

    @BeforeEach
    void setUp() {
        VnPayProperties props = new VnPayProperties();
        props.setTmnCode("TESTTMN1");
        props.setHashSecret("MYSANDBOXSECRETKEY1234567890");
        props.setReturnUrl("http://localhost:5173/checkout/return");
        gateway = new VnPayGateway(props);

        order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode("OD202607250001")
                .totalAmount(new BigDecimal("150000.00"))
                .currency("VND")
                .build();
    }

    @Test
    void signedCallback_isVerifiedByParseCallback_roundTrip() {
        Map<String, String> signed = gateway.buildSignedCallbackParams(order, true);

        PaymentCallbackResult result = gateway.parseCallback(signed);

        assertTrue(result.signatureValid(), "self-signed callback must verify");
        assertTrue(result.paymentSuccessful());
        assertEquals("OD202607250001", result.orderCode());
        assertEquals(15_000_000L, result.amount()); // 150000.00 VND × 100
    }

    @Test
    void extraNonVnpParam_doesNotBreakVerification() {
        // The return URL carries our own orderId alongside VNPay's vnp_* params; it must be
        // ignored by the checksum so verification still succeeds.
        Map<String, String> signed = new java.util.HashMap<>(gateway.buildSignedCallbackParams(order, true));
        signed.put("orderId", UUID.randomUUID().toString());

        assertTrue(gateway.parseCallback(signed).signatureValid());
    }

    @Test
    void tamperedCallback_failsSignatureVerification() {
        Map<String, String> signed = new java.util.HashMap<>(gateway.buildSignedCallbackParams(order, true));
        signed.put("vnp_Amount", "1"); // tamper with the amount after signing

        assertFalse(gateway.parseCallback(signed).signatureValid());
    }

    @Test
    void buildPaymentUrl_includesTxnRefAndSecureHash() {
        String url = gateway.buildPaymentUrl(order, "1.2.3.4");

        assertTrue(url.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(url.contains("vnp_TxnRef=OD202607250001"));
        assertTrue(url.contains("vnp_SecureHash="));
    }
}

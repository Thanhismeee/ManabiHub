package com.manabihub.payment.gateway;

import com.manabihub.order.entity.Order;
import com.manabihub.payment.config.VnPayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * VNPay implementation of {@link PaymentGateway} following the VNPay 2.1.0
 * pay/IPN contract (HMAC-SHA512 checksum over sorted, URL-encoded parameters).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VnPayGateway implements PaymentGateway {

    public static final String PROVIDER = "VNPAY";

    private static final BigDecimal MINOR_UNIT_FACTOR = BigDecimal.valueOf(100);
    private static final String SECURE_HASH = "vnp_SecureHash";
    private static final String SECURE_HASH_TYPE = "vnp_SecureHashType";
    private static final DateTimeFormatter VNP_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VnPayProperties properties;

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public String buildPaymentUrl(Order order, String clientIp) {
        Map<String, String> params = baseRequestParams(order, clientIp);

        List<String> fieldNames = new ArrayList<>(params.keySet());
        java.util.Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String name = itr.next();
            String value = params.get(name);
            if (value == null || value.isEmpty()) {
                continue;
            }
            hashData.append(name).append('=').append(encode(value));
            query.append(encode(name)).append('=').append(encode(value));
            if (itr.hasNext()) {
                hashData.append('&');
                query.append('&');
            }
        }

        String secureHash = hmacSHA512(properties.getHashSecret(), hashData.toString());
        query.append('&').append(SECURE_HASH).append('=').append(secureHash);

        return properties.getPayUrl() + "?" + query;
    }

    @Override
    public PaymentCallbackResult parseCallback(Map<String, String> params) {
        String received = params.get(SECURE_HASH);
        String expected = sign(params);
        boolean valid = received != null && received.equalsIgnoreCase(expected);

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        boolean success = "00".equals(responseCode) && "00".equals(transactionStatus);

        long amount = 0L;
        try {
            String raw = params.get("vnp_Amount");
            if (raw != null) {
                amount = Long.parseLong(raw.trim());
            }
        } catch (NumberFormatException e) {
            log.warn("VNPay callback had non-numeric vnp_Amount: {}", params.get("vnp_Amount"));
        }

        return new PaymentCallbackResult(
                valid,
                params.get("vnp_TxnRef"),
                params.get("vnp_TransactionNo"),
                amount,
                responseCode,
                transactionStatus,
                success);
    }

    @Override
    public Map<String, String> buildSignedCallbackParams(Order order, boolean success) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", minorAmount(order));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", orderInfo(order));
        params.put("vnp_PayDate", VNP_TIME.format(Instant.now()));
        params.put("vnp_ResponseCode", success ? "00" : "24");
        params.put("vnp_TransactionNo", String.format("%010d", Math.abs(RANDOM.nextLong() % 10_000_000_000L)));
        params.put("vnp_TransactionStatus", success ? "00" : "02");
        params.put("vnp_TxnRef", order.getOrderCode());
        params.put("vnp_SecureHashType", "SHA512");

        params.put(SECURE_HASH, sign(params));
        return params;
    }

    // ── internals ───────────────────────────────────────────────────────────

    private Map<String, String> baseRequestParams(Order order, String clientIp) {
        Instant now = Instant.now();
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", properties.getVersion());
        params.put("vnp_Command", properties.getCommand());
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", minorAmount(order));
        params.put("vnp_CurrCode", properties.getCurrency());
        params.put("vnp_TxnRef", order.getOrderCode());
        params.put("vnp_OrderInfo", orderInfo(order));
        params.put("vnp_OrderType", properties.getOrderType());
        params.put("vnp_Locale", properties.getLocale());
        params.put("vnp_ReturnUrl", buildReturnUrl(order));
        params.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", VNP_TIME.format(now));
        params.put("vnp_ExpireDate", VNP_TIME.format(
                now.plus(Duration.ofMinutes(Math.max(1, properties.getPaymentExpiryMinutes())))));
        return params;
    }

    /**
     * VNPay amount in minor units (VND × 100), integer. Uses the gateway portion
     * (total minus any wallet-paid amount) so combined payments charge only the remainder.
     */
    private String minorAmount(Order order) {
        return String.valueOf(order.getGatewayAmount().multiply(MINOR_UNIT_FACTOR).longValue());
    }

    private String orderInfo(Order order) {
        return "Thanh toan don hang " + order.getOrderCode();
    }

    private String buildReturnUrl(Order order) {
        String base = properties.getReturnUrl();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "orderId=" + order.getId();
    }

    /**
     * Computes the checksum over the {@code vnp_*} fields (except the hash fields), sorted by
     * name, in the canonical VNPay format. Non-VNPay params (e.g. our own {@code orderId}
     * appended to the return URL) are ignored so they cannot break verification.
     */
    private String sign(Map<String, String> params) {
        String hashData = params.entrySet().stream()
                .filter(e -> e.getKey().startsWith("vnp_"))
                .filter(e -> !e.getKey().equals(SECURE_HASH) && !e.getKey().equals(SECURE_HASH_TYPE))
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        return hmacSHA512(properties.getHashSecret(), hashData);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private static String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * bytes.length);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute VNPay checksum", e);
        }
    }
}

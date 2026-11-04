package com.manabihub.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Fails production startup when VNPay could redirect a customer to an unsafe or
 * unroutable browser return URL.
 */
@Component
@Profile("prod")
public class VnPayProductionConfigurationValidator implements InitializingBean {

    private static final String CHECKOUT_RETURN_PATH = "/checkout/return";

    private final VnPayProperties vnPayProperties;
    private final String frontendBaseUrl;

    public VnPayProductionConfigurationValidator(
            VnPayProperties vnPayProperties,
            @Value("${app.frontend.base-url}") String frontendBaseUrl
    ) {
        this.vnPayProperties = vnPayProperties;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void afterPropertiesSet() {
        validate(vnPayProperties, frontendBaseUrl);
    }

    static void validate(String returnUrl, String frontendBaseUrl) {
        URI returnUri = parseHttpsUrl("VNPAY_RETURN_URL", returnUrl);
        URI frontendUri = parseHttpsUrl("FRONTEND_BASE_URL", frontendBaseUrl);

        if (isLocalOrPrivateHost(returnUri.getHost())) {
            throw invalid("VNPAY_RETURN_URL must not target localhost or a local/private IP address");
        }
        if (!CHECKOUT_RETURN_PATH.equals(returnUri.getPath())) {
            throw invalid("VNPAY_RETURN_URL path must be exactly " + CHECKOUT_RETURN_PATH);
        }
        if (returnUri.getRawQuery() != null || returnUri.getRawFragment() != null) {
            throw invalid("VNPAY_RETURN_URL must not contain a query string or fragment");
        }
        if (!sameOrigin(returnUri, frontendUri)) {
            throw invalid("VNPAY_RETURN_URL must use the same origin as FRONTEND_BASE_URL");
        }
    }

    static void validate(VnPayProperties properties, String frontendBaseUrl) {
        if (properties == null) {
            throw invalid("VNPay properties must be configured");
        }

        validate(properties.getReturnUrl(), frontendBaseUrl);
        requireText("VNPAY_TMN_CODE", properties.getTmnCode());
        requireText("VNPAY_HASH_SECRET", properties.getHashSecret());
    }

    private static void requireText(String variableName, String value) {
        if (!StringUtils.hasText(value)) {
            throw invalid(variableName + " must be configured");
        }
    }

    private static URI parseHttpsUrl(String variableName, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            throw invalid(variableName + " must be configured");
        }

        URI uri;
        try {
            uri = new URI(rawValue.trim());
        } catch (URISyntaxException exception) {
            throw invalid(variableName + " must be a valid absolute URI", exception);
        }

        if (!uri.isAbsolute()
                || !"https".equalsIgnoreCase(uri.getScheme())
                || !StringUtils.hasText(uri.getHost())) {
            throw invalid(variableName + " must be an absolute HTTPS URL");
        }
        if (uri.getUserInfo() != null) {
            throw invalid(variableName + " must not contain user information");
        }
        return uri;
    }

    private static boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && normalizedPort(first) == normalizedPort(second);
    }

    private static int normalizedPort(URI uri) {
        return uri.getPort() == -1 ? 443 : uri.getPort();
    }

    private static boolean isLocalOrPrivateHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.contains(":") || normalized.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) {
            try {
                InetAddress address = InetAddress.getByName(normalized);
                return address.isLoopbackAddress()
                        || address.isAnyLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress()
                        || address.isMulticastAddress()
                        || isUniqueLocalIpv6(address);
            } catch (UnknownHostException ignored) {
                return false;
            }
        }

        return normalized.equals("localhost")
                || normalized.endsWith(".localhost")
                || normalized.equals("0.0.0.0")
                || normalized.startsWith("127.");
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] addressBytes = address.getAddress();
        return addressBytes.length == 16 && (addressBytes[0] & 0xfe) == 0xfc;
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Invalid production VNPay configuration: " + reason);
    }

    private static IllegalStateException invalid(String reason, Exception cause) {
        return new IllegalStateException(
                "Invalid production VNPay configuration: " + reason,
                cause
        );
    }
}

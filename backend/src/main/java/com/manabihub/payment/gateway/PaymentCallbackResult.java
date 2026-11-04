package com.manabihub.payment.gateway;

/**
 * Normalised outcome of parsing a payment provider callback (VNPay return/IPN).
 *
 * @param signatureValid       whether the provider checksum verified
 * @param orderCode            our order reference ({@code vnp_TxnRef})
 * @param providerTransactionId provider-side transaction id ({@code vnp_TransactionNo})
 * @param amount               amount reported by the provider, in minor units (VND × 100)
 * @param responseCode         provider response code ({@code vnp_ResponseCode})
 * @param transactionStatus    provider transaction status ({@code vnp_TransactionStatus})
 * @param paymentSuccessful    true only when the provider reports a completed payment
 */
public record PaymentCallbackResult(
        boolean signatureValid,
        String orderCode,
        String providerTransactionId,
        long amount,
        String responseCode,
        String transactionStatus,
        boolean paymentSuccessful
) {
}

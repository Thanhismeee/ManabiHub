package com.manabihub.wallet.exception;

import com.manabihub.common.constants.MessageCodes;
import com.manabihub.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class WalletCaptureReconciliationException extends BusinessException {

    public WalletCaptureReconciliationException(String message) {
        super(MessageCodes.COMMON_CONFLICT, message, HttpStatus.CONFLICT);
    }
}

package com.manabihub.payout.security;

import com.manabihub.payout.entity.BankAccountSnapshot;
import com.manabihub.payout.entity.TeacherBankAccount;
import com.manabihub.payout.entity.WithdrawalRequest;
import com.manabihub.payout.repository.TeacherBankAccountRepository;
import com.manabihub.payout.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class PayoutBankDataMigrationRunner implements ApplicationRunner {

    private final TeacherBankAccountRepository bankAccountRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final PayoutSecurityService securityService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int securedAccounts = secureSavedAccounts();
        int securedSnapshots = secureWithdrawalSnapshots();
        if (securedAccounts > 0 || securedSnapshots > 0) {
            log.info(
                    "Secured legacy payout bank data (savedAccounts={}, withdrawalSnapshots={})",
                    securedAccounts,
                    securedSnapshots
            );
        }
    }

    private int secureSavedAccounts() {
        List<TeacherBankAccount> changed = new ArrayList<>();
        for (TeacherBankAccount account : bankAccountRepository.findAll()) {
            String plaintext = securityService.decryptAccountNumber(account.getAccountNumber());
            boolean needsEncryption = !securityService.isEncrypted(account.getAccountNumber());
            boolean needsFingerprint = account.getAccountFingerprint() == null
                    || account.getAccountFingerprint().isBlank();
            if (!needsEncryption && !needsFingerprint) {
                continue;
            }
            account.setAccountNumber(securityService.encryptAccountNumber(plaintext));
            account.setAccountFingerprint(securityService.fingerprintAccountNumber(plaintext));
            changed.add(account);
        }
        if (!changed.isEmpty()) {
            bankAccountRepository.saveAll(changed);
            bankAccountRepository.flush();
        }
        return changed.size();
    }

    private int secureWithdrawalSnapshots() {
        List<WithdrawalRequest> changed = new ArrayList<>();
        for (WithdrawalRequest request : withdrawalRequestRepository.findAll()) {
            BankAccountSnapshot snapshot = request.getBankAccountSnapshot();
            if (snapshot == null
                    || snapshot.getAccountNumber() == null
                    || securityService.isEncrypted(snapshot.getAccountNumber())) {
                continue;
            }
            snapshot.setAccountNumber(
                    securityService.encryptAccountNumber(snapshot.getAccountNumber())
            );
            request.setBankAccountSnapshot(snapshot);
            changed.add(request);
        }
        if (!changed.isEmpty()) {
            withdrawalRequestRepository.saveAll(changed);
            withdrawalRequestRepository.flush();
        }
        return changed.size();
    }
}

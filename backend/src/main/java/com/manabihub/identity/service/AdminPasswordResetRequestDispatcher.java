package com.manabihub.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AdminPasswordResetRequestDispatcher {

    private static final Logger log =
            LoggerFactory.getLogger(AdminPasswordResetRequestDispatcher.class);

    private final TaskExecutor executor;
    private final ObjectProvider<PlatformTransactionManager> transactionManagerProvider;

    public AdminPasswordResetRequestDispatcher(
            @Qualifier("adminPasswordResetExecutor") TaskExecutor executor,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider
    ) {
        this.executor = executor;
        this.transactionManagerProvider = transactionManagerProvider;
    }

    public boolean dispatch(Runnable requestProcessor) {
        try {
            executor.execute(() -> processInTransaction(requestProcessor));
            return true;
        } catch (TaskRejectedException rejected) {
            log.warn("Admin password reset request dropped because the queue is saturated");
            return false;
        }
    }

    private void processInTransaction(Runnable requestProcessor) {
        PlatformTransactionManager transactionManager =
                transactionManagerProvider.getIfAvailable();
        if (transactionManager == null) {
            log.error("Admin password reset request dropped because transactions are unavailable");
            return;
        }
        try {
            TransactionTemplate transactionTemplate =
                    new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> requestProcessor.run());
        } catch (RuntimeException failure) {
            log.error(
                    "Admin password reset request processing failed ({})",
                    failure.getClass().getSimpleName()
            );
        }
    }
}

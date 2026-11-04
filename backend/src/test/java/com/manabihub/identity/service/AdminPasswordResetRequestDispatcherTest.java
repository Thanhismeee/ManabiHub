package com.manabihub.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPasswordResetRequestDispatcherTest {

    @Mock private TaskExecutor executor;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private ObjectProvider<PlatformTransactionManager> transactionManagerProvider;
    @Mock private Runnable processor;

    private AdminPasswordResetRequestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AdminPasswordResetRequestDispatcher(
                executor,
                transactionManagerProvider
        );
    }

    @Test
    void acceptedRequestRunsInsideItsOwnTransaction() {
        when(transactionManagerProvider.getIfAvailable())
                .thenReturn(transactionManager);
        when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        assertTrue(dispatcher.dispatch(processor));
        verify(executor).execute(taskCaptor.capture());

        taskCaptor.getValue().run();

        verify(processor).run();
        verify(transactionManager).commit(any());
    }

    @Test
    void saturatedQueueDropsRequestWithoutExecutingProcessor() {
        doThrow(new TaskRejectedException("queue full"))
                .when(executor)
                .execute(any());

        assertFalse(dispatcher.dispatch(processor));
    }

    @Test
    void missingTransactionManagerFailsClosedInsideWorker() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        assertTrue(dispatcher.dispatch(processor));
        verify(executor).execute(taskCaptor.capture());

        taskCaptor.getValue().run();

        verify(processor, never()).run();
    }
}

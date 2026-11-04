package com.manabihub.operations.logging;

import ch.qos.logback.classic.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OperationsLogCapture {

    private static final String APPENDER_NAME = "MANABIHUB_OPERATIONS_RING_BUFFER";

    private final RecentApplicationLogBuffer buffer;
    private InMemoryOperationsLogAppender appender;

    public OperationsLogCapture(RecentApplicationLogBuffer buffer) {
        this.buffer = buffer;
    }

    @PostConstruct
    void attach() {
        if (!(LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext context)) {
            return;
        }
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (rootLogger.getAppender(APPENDER_NAME) != null) {
            return;
        }

        appender = new InMemoryOperationsLogAppender(buffer);
        appender.setName(APPENDER_NAME);
        appender.setContext(context);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @PreDestroy
    void detach() {
        if (appender == null) {
            return;
        }
        if (LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext context) {
            context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        }
        appender.stop();
    }
}

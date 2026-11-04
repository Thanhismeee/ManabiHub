package com.manabihub.operations.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryOperationsLogAppenderTest {

    @Test
    void capturesApplicationInfoButDropsFrameworkInfo() {
        RecentApplicationLogBuffer buffer = new RecentApplicationLogBuffer(
                new OperationsLogSanitizer(), 100, 3_145_728
        );
        LoggerContext context = new LoggerContext();
        context.start();
        InMemoryOperationsLogAppender appender = new InMemoryOperationsLogAppender(buffer);
        appender.setContext(context);
        appender.start();

        appender.doAppend(event("com.manabihub.Payment", Level.INFO, "application event"));
        appender.doAppend(event("org.springframework.web", Level.INFO, "framework noise"));
        appender.doAppend(event("org.springframework.web", Level.ERROR, "framework failure"));

        assertThat(buffer.find(null, null, null, 10))
                .extracting(com.manabihub.operations.dto.RecentApplicationLogResponse.LogEntry::message)
                .containsExactly("framework failure", "application event");

        appender.stop();
        context.stop();
    }

    private ILoggingEvent event(String logger, Level level, String message) {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getLoggerName()).thenReturn(logger);
        when(event.getLevel()).thenReturn(level);
        when(event.getFormattedMessage()).thenReturn(message);
        when(event.getTimeStamp()).thenReturn(System.currentTimeMillis());
        when(event.getMDCPropertyMap()).thenReturn(Map.of());
        return event;
    }
}

package com.manabihub.operations.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import com.manabihub.operations.dto.RecentApplicationLogResponse.LogEntry;

import java.time.Instant;

final class InMemoryOperationsLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_STACK_FRAMES = 8;
    private final RecentApplicationLogBuffer buffer;

    InMemoryOperationsLogAppender(RecentApplicationLogBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    protected void append(ILoggingEvent event) {
        String loggerName = event.getLoggerName();
        boolean applicationLog = loggerName != null && loggerName.startsWith("com.manabihub");
        boolean applicationInfoOrHigher = applicationLog
                && event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.INFO);
        boolean externalError = event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.ERROR);
        if (!applicationInfoOrHigher && !externalError) {
            return;
        }
        buffer.append(new LogEntry(
                Instant.ofEpochMilli(event.getTimeStamp()),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getFormattedMessage(),
                event.getMDCPropertyMap().get("correlationId"),
                summarizeThrowable(event.getThrowableProxy())
        ));
    }

    private String summarizeThrowable(IThrowableProxy throwable) {
        if (throwable == null) {
            return null;
        }

        StringBuilder summary = new StringBuilder(throwable.getClassName());
        if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
            summary.append(": ").append(throwable.getMessage());
        }
        StackTraceElementProxy[] frames = throwable.getStackTraceElementProxyArray();
        for (int index = 0; index < Math.min(frames.length, MAX_STACK_FRAMES); index++) {
            summary.append(" | at ").append(frames[index].getStackTraceElement());
        }
        if (frames.length > MAX_STACK_FRAMES) {
            summary.append(" | … ").append(frames.length - MAX_STACK_FRAMES)
                    .append(" more frames");
        }
        return summary.toString();
    }
}

package com.aiasti.tiger.infrastructure.online;

import java.util.concurrent.TimeUnit;

import com.aiasti.tiger.domain.exception.OnlineAccessException;

/**
 * 简单串行限流器（DEV-004、BIZ-009）。
 *
 * <p>按「最小请求间隔」阻塞调用线程，保证同一 Provider 的请求频率不超上限。
 */
final class RequestThrottle {

    private final long intervalNanos;
    private long nextNanos = System.nanoTime();

    RequestThrottle(int requestsPerSecond) {
        int perSecond = Math.max(1, requestsPerSecond);
        this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / perSecond;
    }

    synchronized void acquire() {
        long now = System.nanoTime();
        long waitNanos = nextNanos - now;
        if (waitNanos > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OnlineAccessException("在线检索被中断", e);
            }
            now = System.nanoTime();
        }
        nextNanos = Math.max(now, nextNanos) + intervalNanos;
    }
}

package com.blackbamboo.apns2.core.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.blackbamboo.apns2.core.module.ApnsConfig;
import com.blackbamboo.apns2.core.module.Payload;
import com.blackbamboo.apns2.core.module.PushNotification;

public abstract class AbstractApnsService implements ApnsService {

    protected static final Logger logger = LogManager.getLogger("ApnsService");

    /**
     * expire in minutes
     */
    private static final int EXPIRE = 15 * 60 * 1000;

    private static final AtomicInteger IDS = new AtomicInteger(0);

    protected ExecutorService executorService;

    public AbstractApnsService(ApnsConfig config) {
        executorService = Executors.newFixedThreadPool(config.getPoolSize());
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void sendNotification(String token, Payload payload) {
        PushNotification notification = new PushNotification();
        notification.setToken(token);
        notification.setPayload(payload);
        notification.setExpire(EXPIRE);
        notification.setId(IDS.incrementAndGet());
        sendNotification(notification);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(6, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("shutdown", e);
        }
    }
}

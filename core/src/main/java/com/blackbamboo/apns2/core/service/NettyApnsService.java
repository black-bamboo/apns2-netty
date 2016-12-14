package com.blackbamboo.apns2.core.service;

import com.blackbamboo.apns2.core.module.ApnsConfig;
import com.blackbamboo.apns2.core.module.PushNotification;

public class NettyApnsService extends AbstractApnsService {

    private NettyApnsConnectionPool connectionPool;

    private NettyApnsService(ApnsConfig config) {
        super(config);
        connectionPool = new NettyApnsConnectionPool(config);
    }

    public static NettyApnsService create(ApnsConfig apnsConfig) {
        return new NettyApnsService(apnsConfig);
    }

    @Override
    public void sendNotification(PushNotification notification) {
        executorService.execute(() -> {
            NettyApnsConnection connection = null;
            try {
                connection = connectionPool.acquire();
                if (connection != null) {
                    connection.sendNotification(notification);
                }
            } catch (Exception e) {
                logger.error("sendNotification", e);
            } finally {
                connectionPool.release(connection);
            }
        });
    }

    @Override
    public void shutdown() {
        connectionPool.shutdown();
        super.shutdown();
    }
}

package com.blackbamboo.apns2.core.service;

import java.security.KeyStore;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.KeyManagerFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.blackbamboo.apns2.core.module.ApnsConfig;

class NettyApnsConnectionPool {

    private static final Logger logger = LogManager.getLogger(NettyApnsConnectionPool.class);

    private static final String HOST_DEVELOPMENT = "api.development.push.apple.com";

    private static final String HOST_PRODUCTION = "api.push.apple.com";

    private static final String ALGORITHM = "sunx509";

    private static final String KEY_STORE_TYPE = "PKCS12";

    private static final int PORT = 2197;

    private volatile boolean isShutdown;

    private BlockingQueue<NettyApnsConnection> connectionQueue;

    NettyApnsConnectionPool(ApnsConfig config) {
        int poolSize = config.getPoolSize();
        connectionQueue = new LinkedBlockingQueue<>(poolSize);

        String host = config.isDevEnv() ? HOST_DEVELOPMENT : HOST_PRODUCTION;
        KeyManagerFactory keyManagerFactory = createKeyManagerFactory(config);
        for (int i = 0; i < poolSize; i++) {
            NettyApnsConnection connection = new NettyApnsConnection("conn-" + i, host, PORT,
                    config.getRetries(), config.getTimeout(), keyManagerFactory);
            connection.setConnectionPool(this);
            connectionQueue.add(connection);
        }
    }

    private KeyManagerFactory createKeyManagerFactory(ApnsConfig config) {
        try {
            char[] password = config.getPassword().toCharArray();
            KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
            keyStore.load(config.getKeyStore(), password);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(ALGORITHM);
            keyManagerFactory.init(keyStore, password);
            return keyManagerFactory;
        } catch (Exception e) {
            logger.error("createKeyManagerFactory", e);
            throw new IllegalStateException("create key manager factory failed");
        }
    }

    NettyApnsConnection acquire() {
        try {
            return connectionQueue.take();
        } catch (InterruptedException e) {
            logger.error("acquire", e);
        }

        return null;
    }

    void release(NettyApnsConnection connection) {
        if (connection != null) {
            connectionQueue.add(connection);
        }
    }

    void shutdown() {
        isShutdown = true;
    }

    boolean isShutdown() {
        return isShutdown;
    }
}

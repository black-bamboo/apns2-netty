package com.blackbamboo.apns2.core.service;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.blackbamboo.apns2.core.module.PushNotification;

import static com.blackbamboo.apns2.core.service.HttpResponseHandler.CODE_READ_FAILED;
import static com.blackbamboo.apns2.core.service.HttpResponseHandler.CODE_READ_TIMEOUT;
import static com.blackbamboo.apns2.core.service.HttpResponseHandler.CODE_SUCCESS;
import static com.blackbamboo.apns2.core.service.HttpResponseHandler.CODE_WRITE_FAILED;
import static com.blackbamboo.apns2.core.service.HttpResponseHandler.CODE_WRITE_TIMEOUT;

class NettyApnsConnection {

    private static final Logger logger = LogManager.getLogger(NettyApnsConnection.class);

    private static final int INITIAL_STREAM_ID = 3;

    private int retryTimes;

    private KeyManagerFactory keyManagerFactory;

    private Channel channel;

    private AtomicInteger streamId = new AtomicInteger(INITIAL_STREAM_ID);

    private Http2ClientInitializer http2ClientInitializer;

    private NettyApnsConnectionPool connectionPool;

    private String host;

    private String name;

    private int port;

    private int timeout;

    NettyApnsConnection(String name, String host, int port, int retryTimes, int timeout,
                        KeyManagerFactory keyManagerFactory) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.retryTimes = retryTimes;
        this.timeout = timeout;
        this.keyManagerFactory = keyManagerFactory;
    }

    void setConnectionPool(NettyApnsConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    void sendNotification(PushNotification notification) {
        if (connectionPool.isShutdown()) {
            return;
        }

        info("send payload " + notification.getPayload().toString() + " token " + notification.getToken());
        int retryCount = 0;
        int streamId = -1;
        boolean success = false;
        FullHttpRequest request = null;
        while (retryCount < retryTimes && !connectionPool.isShutdown()) {
            if (channel == null || !channel.isActive()) {
                try {
                    initializeNettyClient();
                } catch (Exception e) {
                    error("initializeNettyClient", e);
                    http2ClientInitializer = null;
                    retryCount++;
                    continue;
                }
            }

            HttpResponseHandler responseHandler = http2ClientInitializer.responseHandler();
            if (request == null) {
                request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,
                        HttpMethod.POST, "https://" + host + "/3/device/" + notification.getToken(),
                        Unpooled.copiedBuffer(notification.getPayload().toString().getBytes()));
                streamId = this.streamId.getAndAdd(2);
                responseHandler.put(streamId, channel.writeAndFlush(request), channel.newPromise());
            }

            Map<Integer, Integer> responses = responseHandler.awaitResponses(timeout, TimeUnit.MILLISECONDS);
            int code = responses.get(streamId);
            if (code == CODE_SUCCESS) {
                info("send success token " + notification.getToken());
                success = true;
                break;
            } else if (code == CODE_READ_TIMEOUT || code == CODE_WRITE_TIMEOUT) {
                retryCount++;
            } else if (code == CODE_READ_FAILED || code == CODE_WRITE_FAILED) {
                request = null;
                retryCount++;
                try {
                    channel.close().sync();
                } catch (InterruptedException e) {
                    error("close channel", e);
                }
            }
        }

        if (!success) {
            error("send failed token " + notification.getToken());
        }
    }

    private void initializeNettyClient() throws Exception {
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Http2ClientInitializer http2ClientInitializer = new Http2ClientInitializer(name,
                createSslContext(), Integer.MAX_VALUE);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .remoteAddress(host, port)
                .handler(http2ClientInitializer);

        debug("connecting to " + host);
        channel = bootstrap.connect().syncUninterruptibly().channel();
        debug("connected");

        Http2SettingsHandler http2SettingsHandler = http2ClientInitializer.settingsHandler();
        debug("await setting");
        http2SettingsHandler.awaitSettings(5, TimeUnit.SECONDS);
        debug("setting success");

        streamId.set(INITIAL_STREAM_ID);
        this.http2ClientInitializer = http2ClientInitializer;
    }

    private SslContext createSslContext() throws SSLException {
        SslProvider provider = SslProvider.JDK;
        return SslContextBuilder.forClient()
                .sslProvider(provider)
                /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                 * Please refer to the HTTP/2 specification for cipher requirements. */
                .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .keyManager(keyManagerFactory)
                .applicationProtocolConfig(ApplicationProtocolConfig.DISABLED)
                .build();
    }

    private void info(String message) {
        logger.info(name + " " + message);
    }

    private void debug(String message) {
        logger.debug(name + " " + message);
    }

    private void error(String message) {
        logger.error(name + " " + message);
    }

    private void error(String message, Throwable throwable) {
        logger.error(name + " " + message, throwable);
    }
}

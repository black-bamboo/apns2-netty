package com.blackbamboo.apns.sample;

import java.io.InputStream;

import com.blackbamboo.apns2.core.module.ApnsConfig;
import com.blackbamboo.apns2.core.module.Payload;
import com.blackbamboo.apns2.core.service.ApnsService;
import com.blackbamboo.apns2.core.service.NettyApnsService;

public class ApnsClient {

    private static final String TOKEN = "778bde3ceb4628456689963d0319763338441807ef04bc23ecc486d61d428ed7";

    public static void main(String[] args) {
        ApnsConfig config = new ApnsConfig();
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("/developmentAPNs.p12");
        config.setKeyStore(is);
        config.setDevEnv(true);
        config.setPassword("123456");
        config.setPoolSize(5);
        config.setTimeout(30_000);
        ApnsService apnsService = NettyApnsService.create(config);

        Payload payload = new Payload();
        payload.setAlert("test");
        apnsService.sendNotification(TOKEN, payload);
    }
}

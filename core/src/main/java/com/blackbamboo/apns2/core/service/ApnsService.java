package com.blackbamboo.apns2.core.service;

import com.blackbamboo.apns2.core.module.Payload;
import com.blackbamboo.apns2.core.module.PushNotification;

public interface ApnsService {

    void sendNotification(String token, Payload payload);

    void sendNotification(PushNotification var1);

    void shutdown();

}

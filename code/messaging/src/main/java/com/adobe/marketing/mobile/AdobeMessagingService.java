package com.adobe.marketing.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;


import com.adobe.marketing.mobile.messaging.internal.MessagingConstants;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Random;


public class AdobeMessagingService extends FirebaseMessagingService {

    static final String SELF_TAG = "AdobeMessagingService";
    
    @Override
    public void onNewToken(@NonNull String s) {
        super.onNewToken(s);
        MobileCore.setPushIdentifier(s);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        handleRemoteMessage(remoteMessage);
    }

    public static boolean handleRemoteMessage(@NonNull RemoteMessage remoteMessage) {
        if (!isAdobePushNotification(remoteMessage)) {
            Log.debug(MessagingConstants.LOG_TAG, SELF_TAG, "The received push message is not generated from Adobe Journey Optimizer, Messaging extension is ignoring to display the push notification.");
            return false;
        }

        HashMap<String,Object> notificationData = new HashMap<>();
        notificationData.put("messageId", remoteMessage.getMessageId());
        notificationData.put("messageData", remoteMessage.getData());
        Event pushNotificationReceivedEvent = new Event.Builder("Push Notification Received", EventType.MESSAGING, EventSource.REQUEST_CONTENT).setEventData(notificationData).build();
        MobileCore.dispatchEvent(pushNotificationReceivedEvent);
        return true;

    }


    private static boolean isAdobePushNotification(@NonNull RemoteMessage remoteMessage) {
        return remoteMessage.getData() != null &&
                remoteMessage.getData().containsKey(MessagingConstants.PushPayloadKeys.TITLE) &&
                remoteMessage.getData().containsKey(MessagingConstants.PushPayloadKeys.BODY);
    }


}

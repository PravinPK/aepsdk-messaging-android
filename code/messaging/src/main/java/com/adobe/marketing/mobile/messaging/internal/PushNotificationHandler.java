package com.adobe.marketing.mobile.messaging.internal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.Messaging;
import com.adobe.marketing.mobile.MessagingPushPayload;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.messaging.R;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.StringUtils;
import com.google.firebase.messaging.RemoteMessage;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

class PushNotificationHandler {

    private final String DEFAULT_CHANNEL_ID = "AdobeJourneyOptimizer_notification_channel";
    private final String DEFAULT_CHANNEL_NAME = "Messaging Notifications Channel";

    PushNotificationHandler() {}

    public void handlePushNotificationEvent(final Event event) {

        String messageID = DataReader.optString(event.getEventData(), "messageId", null);
        if (StringUtils.isNullOrEmpty(messageID)) {
            // log here
           return;
        }

        Map<String,String> messageData = DataReader.optStringMap(event.getEventData(), "messageData", null);
        if (StringUtils.isNullOrEmpty(messageID)) {
            // log here
            return;
        }

        // Use the MessagingPushPayload object to extract the payload attributes for creating notification
        MessagingPushPayload payload = new MessagingPushPayload(messageData);

        // Setting the channel
        String channelId = payload.getChannelId() == null ? DEFAULT_CHANNEL_ID : payload.getChannelId();

        // Understanding whats the importance from priority
        int importance = getImportanceFromPriority(payload.getNotificationPriority());

        Context context = ServiceProvider.getInstance().getAppContextService().getApplicationContext();

        // Create a notification channel (for Android O and later)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, DEFAULT_CHANNEL_NAME, importance);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // Create the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_search_category_default)
                .setContentTitle("Adobe")
                .setContentText("Two in One")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);



        Bitmap notificationImage = downloadBitMapFromURL(payload.getImageUrl());
        if (notificationImage != null) {
            builder.setLargeIcon(notificationImage);
        }

        Bitmap notificationIcon = downloadBitMapFromURL(payload.getIcon());
        if (notificationIcon != null) {
            builder.setSmallIcon(0);
        }

        if (payload.getActionButtons() != null) {
            List<MessagingPushPayload.ActionButton> buttons = payload.getActionButtons();
            for (int i = 0; i< buttons.size(); i++) {
                MessagingPushPayload.ActionButton obj = buttons.get(i);
                String buttonName = obj.getLabel();
                builder.addAction(new NotificationCompat.Action(0, buttonName, null));
            }
        }

        // add push tracking details to the launching intent
        Intent startingIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        Messaging.addPushTrackingDetails(startingIntent, messageID, messageData);

        // Show the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(new Random().nextInt(100), builder.build());

        HashMap<String,Object> notificationData = new HashMap<>();
        notificationData.put("messageId", messageID);
        Event pushNotificationReceivedEvent = new Event.Builder("Push Notification Displayed", EventType.MESSAGING, EventSource.REQUEST_CONTENT).setEventData(notificationData).build();
        MobileCore.dispatchEvent(pushNotificationReceivedEvent);
    }


    private Bitmap downloadBitMapFromURL(final String mediaURL) {
        if (StringUtils.isNullOrEmpty(mediaURL)) {
            return null;
        }
        final Bitmap notificationMedia = null;
        try {
            InputStream inputStream = new URL(mediaURL).openStream();
             return BitmapFactory.decodeStream(inputStream);
        } catch (Exception exp) {
            return null;
        }
    }


    private int getImportanceFromPriority(int priority) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            switch (priority) {
                case Notification.PRIORITY_MIN:
                    return NotificationManager.IMPORTANCE_MIN;
                case Notification.PRIORITY_LOW:
                    return NotificationManager.IMPORTANCE_LOW;
                case Notification.PRIORITY_HIGH:
                    return NotificationManager.IMPORTANCE_HIGH;
                case Notification.PRIORITY_MAX:
                    return NotificationManager.IMPORTANCE_MAX;
                case Notification.PRIORITY_DEFAULT:
                    return NotificationManager.IMPORTANCE_DEFAULT;
                default:
                    return NotificationManager.IMPORTANCE_NONE;
            }
        } else {
            return NotificationManager.IMPORTANCE_DEFAULT;
        }
    }
}

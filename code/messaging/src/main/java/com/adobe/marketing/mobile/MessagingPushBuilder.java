/*
  Copyright 2023 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
 */

package com.adobe.marketing.mobile;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.StringUtils;

import java.util.List;
import java.util.Map;

class MessagingPushBuilder {

    private static final String SELF_TAG = "PushNotificationBuilder";
    private static String DEFAULT_CHANNEL_ID = "AdobeAJOPushChannel";
    // When no channel name is received from the push notification, this default channel name is used.
    // This will appear in the notification settings for the app.
    private static String DEFAULT_CHANNEL_NAME = "General";

    @NonNull
    private MessagingPushPayload payload;
    @NonNull
    private Context context;

    @NonNull
    private MessagingPushUtils utils;

    /**
     * Constructor.
     * @param payload {@link MessagingPushPayload} the payload received from the push notification
     * @param context the application {@link Context}
     **/
    MessagingPushBuilder(final @NonNull MessagingPushPayload payload, final @NonNull Context context) {
        this(payload, context, new MessagingPushUtils(context));
    }

    /**
     * Constructor for testing purposes.
     * @param payload {@link MessagingPushPayload} the payload received from the push notification
     * @param context the application {@link Context}
     * @param utils {@link MessagingPushUtils} the utils class
     */
    MessagingPushBuilder(final @NonNull MessagingPushPayload payload, final @NonNull Context context, final @NonNull MessagingPushUtils utils) {
        this.utils = utils;
        this.payload = payload;
        this.context = context;
    }

    /**
     * Builds a notification for the received payload.
     * @return the notification
     */
    @NonNull
    Notification build(){
        final String channelId = createChannelAndGetChannelID(payload, context);

        // Create the notification
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
        builder.setContentTitle(payload.getTitle());
        builder.setContentText(payload.getBody());
        builder.setNumber(payload.getBadgeCount());
        builder.setPriority(payload.getNotificationPriority());
        builder.setAutoCancel(true);

        setLargeIcon(builder);
        setSmallIcon(builder); // Small Icon must be present, otherwise the notification will not be displayed.
        addActionButtons(builder); // Add action buttons if any
        setSound(builder);
        setNotificationClickAction(builder);
        setNotificationDeleteAction(builder);

        return builder.build();
    }

    /**
     * Creates a channel if it does not exist and returns the channel ID.
     * If a channel ID is received from the payload and if channel exists for the channel ID, the same channel ID is returned.
     * If a channel ID is received from the payload and if channel does not exist for the channel ID, Messaging extension's default channel is used.
     * If no channel ID is received from the payload, Messaging extension's default channel is used.
     * For Android versions below O, no channel is created. Just return the obtained channel ID.
     *
     * @param payload {@link MessagingPushPayload} the payload received from the push notification
     * @param context the application {@link Context}
     * @return the channel ID
     */
    @NonNull
    private String createChannelAndGetChannelID(final MessagingPushPayload payload, final Context context)  {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // For Android versions below O, no channel is created. Just return the obtained channel ID.
            return payload.getChannelId() == null ? DEFAULT_CHANNEL_ID : payload.getChannelId();
        } else {
            // For Android versions O and above, create a channel if it does not exist and return the channel ID.
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            final String channelIdFromPayload = payload.getChannelId();

            // if a channel from the payload
            if (channelIdFromPayload != null && notificationManager.getNotificationChannel(channelIdFromPayload) != null) {
                Log.debug(MessagingPushConstants.LOG_TAG, SELF_TAG, "Channel exists for channel ID: " + channelIdFromPayload + ". Using the same for push notification.");
                return channelIdFromPayload;
            } else {
                Log.debug(MessagingPushConstants.LOG_TAG, SELF_TAG, "Channel does not exist for channel ID obtained from payload ( " + channelIdFromPayload + "). Using the Messaging Extension's default channel.");
            }

            // Use the default channel ID if the channel ID from the payload is null or if a channel does not exist for the channel ID from the payload.
            final String channelId = DEFAULT_CHANNEL_ID;
            if (notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID) != null) {
                Log.debug(MessagingPushConstants.LOG_TAG, SELF_TAG, "Channel already exists for the default channel ID: " + channelId);
                return DEFAULT_CHANNEL_ID;
            } else {
                Log.debug(MessagingPushConstants.LOG_TAG, SELF_TAG, "Creating a new channel for the default channel ID: " + channelId + ".");
                final NotificationChannel channel = new NotificationChannel(channelId, DEFAULT_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
            }
            return channelId;
        }
    }

    /**
     * Sets the small icon for the notification.
     * If a small icon is received from the payload, the same is used.
     * If a small icon is not received from the payload, we use the icon set using MobileCore.setSmallIcon().
     * If a small icon is not set using MobileCore.setSmallIcon(), we use the default small icon of the application.
     *
     * @param builder the notification builder
     */
    private void setSmallIcon(final NotificationCompat.Builder builder) {
        final int iconFromPayload = utils.getSmallIconWithResourceName(payload.getIcon());
        final int iconFromMobileCore =  -1 ;   //MobileCore.getSmallIconResourceId();

        if (isValidIcon(iconFromPayload)) {
            builder.setSmallIcon(iconFromPayload);
        } else if (isValidIcon(iconFromMobileCore)) {
            builder.setSmallIcon(iconFromMobileCore);
        } else {
            final int iconFromApp = utils.getDefaultAppIcon();
            if (isValidIcon(iconFromApp)) {
                builder.setSmallIcon(iconFromApp);
            } else {
                Log.warning(MessagingPushConstants.LOG_TAG, SELF_TAG, "No valid small icon found. Notification will not be displayed.");
            }
        }
    }

    /**
     * Sets the sound for the notification.
     * If a sound is received from the payload, the same is used.
     * If a sound is not received from the payload, the default sound is used
     * @param notificationBuilder the notification builder
     */
    private void setSound(final NotificationCompat.Builder notificationBuilder) {
        if(!StringUtils.isNullOrEmpty(payload.getSound())) {
            notificationBuilder.setSound(utils.getSoundUriForResourceName(payload.getSound()));
        }
        notificationBuilder.setDefaults(Notification.DEFAULT_ALL);
    }

    /**
     * Sets the large icon for the notification.
     * If a large icon url is received from the payload, the image is downloaded and the notification style is set to BigPictureStyle.
     * If large icon url is not received from the payload, default style is used for the notification.
     * @param notificationBuilder the notification builder
     */
    private void setLargeIcon(final NotificationCompat.Builder notificationBuilder) {
        if (!StringUtils.isNullOrEmpty(payload.getImageUrl())) {
            Bitmap bitmap = utils.download(payload.getImageUrl());
            if (bitmap != null) {
                notificationBuilder.setLargeIcon(bitmap);
                NotificationCompat.BigPictureStyle bigPictureStyle = new NotificationCompat.BigPictureStyle();
                bigPictureStyle.bigPicture(bitmap);
                bigPictureStyle.bigLargeIcon(null);
                notificationBuilder.setStyle(bigPictureStyle);
            }
        }
    }

    private void setNotificationClickAction(final NotificationCompat.Builder notificationBuilder) {
        if (payload.getActionType() == MessagingPushPayload.ActionType.DEEPLINK || payload.getActionType() == MessagingPushPayload.ActionType.WEBURL) {
            notificationBuilder.setContentIntent(createDeepLinkIntent(payload.getActionUri(), MessagingPushConstants.Tracking.Values.PUSH_TRACKING_APPLICATION_OPENED));
        } else {
            notificationBuilder.setContentIntent(createOpenAppIntent());
        }
    }

    private void addActionButtons(final NotificationCompat.Builder builder) {
        final List<MessagingPushPayload.ActionButton> actionButtons = payload.getActionButtons();
        if (actionButtons == null || actionButtons.isEmpty()) {
            return;
        }

        for(MessagingPushPayload.ActionButton eachButton : actionButtons) {
            final PendingIntent pendingIntent;
            if (eachButton.getType() == MessagingPushPayload.ActionType.DEEPLINK|| eachButton.getType() == MessagingPushPayload.ActionType.WEBURL) {
                pendingIntent = createDeepLinkIntent(eachButton.getLink(), MessagingPushConstants.Tracking.Values.PUSH_TRACKING_CUSTOM_ACTION);
            } else {
                pendingIntent = createOpenAppIntent();
            }
            builder.addAction(0, eachButton.getLabel(), pendingIntent);
        }
    }

    private PendingIntent createOpenAppIntent() {
        final Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Messaging.addPushTrackingDetails(launchIntent,payload.getMessageId(), payload.getData());
        launchIntent.putExtra("AJOPushInteraction", true);
        launchIntent.putExtra(MessagingPushConstants.Tracking.Keys.EVENT_TYPE, MessagingPushConstants.Tracking.Values.PUSH_TRACKING_APPLICATION_OPENED);
        launchIntent.putExtra(MessagingPushConstants.Tracking.Keys.APPLICATION_OPENED, true);
        return PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent createDeepLinkIntent(final String actionUri, final String eventType) {
        if(StringUtils.isNullOrEmpty(actionUri)) {
            return createOpenAppIntent();
        }
        final Intent deeplinkIntent = new Intent(Intent.ACTION_VIEW);
        deeplinkIntent.putExtra("AJOPushInteraction", true);
        deeplinkIntent.putExtra(MessagingPushConstants.Tracking.Keys.EVENT_TYPE, eventType);
        deeplinkIntent.putExtra(MessagingPushConstants.Tracking.Keys.APPLICATION_OPENED, true);
        deeplinkIntent.setData(Uri.parse(actionUri));
        Messaging.addPushTrackingDetails(deeplinkIntent,payload.getMessageId(), payload.getData());
        return PendingIntent.getActivity(context, 0, deeplinkIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void setNotificationDeleteAction(final NotificationCompat.Builder builder) {
        final Intent deleteIntent = new Intent(context, MessagingDeleteIntentReceiver.class);
        Messaging.addPushTrackingDetails(deleteIntent,payload.getMessageId(), payload.getData());
        final PendingIntent intent = PendingIntent.getBroadcast(context, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setDeleteIntent(intent);
    }

    private boolean isValidIcon(final int icon) {
        return icon > 0 ? true : false;
    }
}

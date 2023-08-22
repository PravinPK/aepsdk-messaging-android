/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.messaging.internal;

import static com.adobe.marketing.mobile.messaging.internal.TestHelper.getDispatchedEventsWith;
import static com.adobe.marketing.mobile.messaging.internal.TestHelper.getSharedStateFor;
import static com.adobe.marketing.mobile.messaging.internal.TestHelper.resetTestExpectations;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.adobe.marketing.mobile.AdobeCallback;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.FeedItem;
import com.adobe.marketing.mobile.Messaging;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.Surface;
import com.adobe.marketing.mobile.messaging.BuildConfig;
import com.adobe.marketing.mobile.util.DataReader;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class MessagingPublicAPITests {
    static {
        BuildConfig.IS_E2E_TEST.set(false);
        BuildConfig.IS_FUNCTIONAL_TEST.set(true);
    }

    @Rule
    public RuleChain rule = RuleChain.outerRule(new TestHelper.SetupCoreRule())
            .around(new TestHelper.RegisterMonitorExtensionRule());

    // --------------------------------------------------------------------------------------------
    // Setup
    // --------------------------------------------------------------------------------------------

    @Before
    public void setup() throws Exception {
        MessagingTestUtils.setEdgeIdentityPersistence(MessagingTestUtils.createIdentityMap("ECID", "mockECID"), TestHelper.defaultApplication);
        HashMap<String, Object> config = new HashMap<String, Object>() {
            {
                put("messaging.eventDataset", "somedatasetid");
            }
        };
        MobileCore.updateConfiguration(config);
        Messaging.registerExtension();
        com.adobe.marketing.mobile.edge.identity.Identity.registerExtension();

        final CountDownLatch latch = new CountDownLatch(1);
        MobileCore.start((AdobeCallback) o -> latch.countDown());

        latch.await(2, TimeUnit.SECONDS);
        resetTestExpectations();
    }

    // --------------------------------------------------------------------------------------------
    // Tests for GetExtensionVersion API
    // --------------------------------------------------------------------------------------------
    @Test
    public void testGetExtensionVersionAPI() {
        Assert.assertEquals(MessagingConstants.EXTENSION_VERSION, Messaging.extensionVersion());
    }

    // --------------------------------------------------------------------------------------------
    // Tests for RegisterExtension API
    // --------------------------------------------------------------------------------------------

    @Test
    public void testRegisterExtensionAPI() throws InterruptedException {
        // test
        // Messaging.registerExtension() is called in the setup method

        // verify that the extension is registered with the correct version details
        Map<String, String> sharedStateMap = MessagingTestUtils.flattenMap(getSharedStateFor(MessagingTestConstants.SharedStateName.EVENT_HUB, 1000));
        assertEquals(MessagingConstants.EXTENSION_VERSION, sharedStateMap.get("extensions.com.adobe.messaging.version"));
    }

    // --------------------------------------------------------------------------------------------
    // Tests for Messaging.addPushTrackingDetails API
    // --------------------------------------------------------------------------------------------
    @Test
    public void testAddPushTrackingDetails() {
        // Parameters
        Intent intent = new Intent();
        String messageId = "mock_message_id";
        Map<String, String> data = new HashMap<>();
        data.put(MessagingTestConstants.TrackingKeys._XDM, "xdmjson");

        // test
        boolean updated = Messaging.addPushTrackingDetails(intent, messageId, data);

        // verify intent is updated
        assertTrue(updated);

        // verify if the intent is updated with messageId and xdm data.
        String actualMsgId = intent.getStringExtra(MessagingTestConstants.EventDataKeys.Messaging.TRACK_INFO_KEY_MESSAGE_ID);
        String actualXdmData = intent.getStringExtra(MessagingTestConstants.EventDataKeys.Messaging.TRACK_INFO_KEY_ADOBE_XDM);
        assertEquals(messageId, actualMsgId);
        assertEquals("xdmjson", actualXdmData);
    }

    @Test
    public void testAddPushTrackingDetails_dataIsNull() {
        // Parameters
        Intent intent = new Intent();
        String messageId = "mock_message_id";

        // test
        boolean updated = Messaging.addPushTrackingDetails(intent, messageId, null);

        // verify intent is updated
        assertFalse(updated);

        // verify if the intent is updated with messageId and xdm data.
        String actualMsgId = intent.getStringExtra(MessagingTestConstants.EventDataKeys.Messaging.TRACK_INFO_KEY_MESSAGE_ID);
        String actualXdmData = intent.getStringExtra(MessagingTestConstants.EventDataKeys.Messaging.TRACK_INFO_KEY_ADOBE_XDM);
        assertNull(actualMsgId);
        assertNull(actualXdmData);
    }

    @Test
    public void testAddPushTrackingDetails_messageIdIsMissing() {
        // Parameters
        Intent intent = new Intent();
        Map<String, String> data = new HashMap<>();
        data.put(MessagingTestConstants.TrackingKeys._XDM, "xdmjson");

        // test
        boolean updated = Messaging.addPushTrackingDetails(intent, null, data);

        // verify intent is updated
        assertFalse(updated);

        // verify if the intent is updated with messageId and xdm data.
        String actualMsgId = intent.getStringExtra(MessagingTestConstants.EventDataKeys.Messaging.TRACK_INFO_KEY_MESSAGE_ID);
        String actualXdmData = intent.getStringExtra(MessagingTestConstants.EventDataKeys.Messaging.TRACK_INFO_KEY_ADOBE_XDM);
        assertNull(actualMsgId);
        assertNull(actualXdmData);
    }

    @Test
    public void testAddPushTrackingDetails_xdmKeyIsMissing() {
        // Parameters
        Intent intent = new Intent();
        String messageId = "mock_message_id";
        Map<String, String> data = new HashMap<>();
        data.put("someRandomKey", "value");

        // test
        boolean updated = Messaging.addPushTrackingDetails(intent, messageId, data);

        // verify intent is updated
        assertTrue(updated);

        // verify if the intent is updated with messageId and xdm data.
        String actualMsgId = intent.getStringExtra(MessagingTestConstants.EventDataKeys.Messaging.TRACK_INFO_KEY_MESSAGE_ID);
        String actualXdmData = intent.getStringExtra(MessagingTestConstants.EventDataKeys.Messaging.TRACK_INFO_KEY_ADOBE_XDM);
        assertEquals(messageId, actualMsgId);
        assertNull(actualXdmData);
    }

    @Test
    public void testAddPushTrackingDetails_intentIsMissing() {
        String messageId = "mock_message_id";
        Map<String, String> data = new HashMap<>();
        data.put(MessagingTestConstants.TrackingKeys._XDM, "xdmjson");

        // test
        boolean updated = Messaging.addPushTrackingDetails(null, messageId, data);

        // verify intent is updated
        assertFalse(updated);
    }

    // --------------------------------------------------------------------------------------------
    // Tests for Messaging.handleNotificationResponse API
    // --------------------------------------------------------------------------------------------
    @Test
    public void testHandleNotificationResponse() throws InterruptedException {
        // Parameters
        Intent intent = getResponseIntent();
        String mockCustomActionId = "mockCustomActionId";
        String expectedMessagingEventData = "{messageId=mockMessageId, actionId=mockCustomActionId, eventType=pushTracking.customAction, applicationOpened=true, adobe_xdm={\n" +
                "            cjm ={\n" +
                "              _experience= {\n" +
                "                customerJourneyManagement= {\n" +
                "                  messageExecution= {\n" +
                "                    messageExecutionID= 16-Sept-postman, \n" +
                "                    messageID= 567, \n" +
                "                    journeyVersionID= some-journeyVersionId, \n" +
                "                    journeyVersionInstanceID= someJourneyVersionInstanceID\n" +
                "                  }\n" +
                "                }\n" +
                "              }\n" +
                "            }\n" +
                "          }}";
        String expectedEdgeEventData = "{xdm={pushNotificationTracking={customAction={actionID=mockCustomActionId}, pushProviderMessageID=mockMessageId, pushProvider=fcm}, application={launches={value=1}}, eventType=pushTracking.customAction, _experience={customerJourneyManagement={pushChannelContext={platform=fcm}, messageExecution={messageExecutionID=16-Sept-postman, journeyVersionInstanceID=someJourneyVersionInstanceID, messageID=567, journeyVersionID=some-journeyVersionId}, messageProfile={channel={_id=https://ns.adobe.com/xdm/channels/push}}}}}, meta={collect={datasetId=somedatasetid}}}";

        // test
        Messaging.handleNotificationResponse(intent, true, mockCustomActionId);

        // verify messaging event
        List<Event> messagingRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.MESSAGING,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, messagingRequestEvents.size());
        assertEquals(expectedMessagingEventData, messagingRequestEvents.get(0).getEventData().toString());

        // verify push tracking edge event
        List<Event> edgeRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.EDGE,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, edgeRequestEvents.size());
        assertEquals(expectedEdgeEventData, edgeRequestEvents.get(0).getEventData().toString());
    }

    @Test
    public void testHandleNotificationResponse_noCustomActionId() throws InterruptedException {
        // Parameters
        Intent intent = getResponseIntent();
        String expectedMessagingEventData = "{messageId=mockMessageId, eventType=pushTracking.applicationOpened, applicationOpened=true, adobe_xdm={\n" +
                "            cjm ={\n" +
                "              _experience= {\n" +
                "                customerJourneyManagement= {\n" +
                "                  messageExecution= {\n" +
                "                    messageExecutionID= 16-Sept-postman, \n" +
                "                    messageID= 567, \n" +
                "                    journeyVersionID= some-journeyVersionId, \n" +
                "                    journeyVersionInstanceID= someJourneyVersionInstanceID\n" +
                "                  }\n" +
                "                }\n" +
                "              }\n" +
                "            }\n" +
                "          }}";
        String expectedEdgeEventData = "{xdm={pushNotificationTracking={pushProviderMessageID=mockMessageId, pushProvider=fcm}, application={launches={value=1}}, eventType=pushTracking.applicationOpened, _experience={customerJourneyManagement={pushChannelContext={platform=fcm}, messageExecution={messageExecutionID=16-Sept-postman, journeyVersionInstanceID=someJourneyVersionInstanceID, messageID=567, journeyVersionID=some-journeyVersionId}, messageProfile={channel={_id=https://ns.adobe.com/xdm/channels/push}}}}}, meta={collect={datasetId=somedatasetid}}}";

        // test
        Messaging.handleNotificationResponse(intent, true, null);

        // verify messaging event
        List<Event> messagingRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.MESSAGING,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, messagingRequestEvents.size());
        assertEquals(expectedMessagingEventData, messagingRequestEvents.get(0).getEventData().toString());

        // verify push tracking edge event
        List<Event> edgeRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.EDGE,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, edgeRequestEvents.size());
        assertEquals(expectedEdgeEventData, edgeRequestEvents.get(0).getEventData().toString());
    }

    @Test
    public void testHandleNotificationResponse_noIntent() throws InterruptedException {
        // test
        Messaging.handleNotificationResponse(null, true, "customAction");

        // verify messaging event
        List<Event> messagingRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.MESSAGING,
                EventSource.REQUEST_CONTENT);
        assertEquals(0, messagingRequestEvents.size());

        // verify no push tracking edge event
        List<Event> edgeRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.EDGE,
                EventSource.REQUEST_CONTENT);
        assertEquals(0, edgeRequestEvents.size());
    }

    @Test
    public void testHandleNotificationResponse_noMessageId() throws InterruptedException {
        // test
        Messaging.handleNotificationResponse(new Intent(), true, "customAction");

        // verify messaging event
        List<Event> messagingRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.MESSAGING,
                EventSource.REQUEST_CONTENT);
        assertEquals(0, messagingRequestEvents.size());

        // verify no push tracking edge event
        List<Event> edgeRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.EDGE,
                EventSource.REQUEST_CONTENT);
        assertEquals(0, edgeRequestEvents.size());
    }

    @Test
    public void testHandleNotificationResponse_noXdmData() throws InterruptedException {
        // Parameters
        Intent intent = new Intent();
        intent.putExtra(MessagingTestConstants.EventDataKeys.Messaging.TRACK_INFO_KEY_MESSAGE_ID, "mockMessageId");

        // expected
        String expectedMessagingEventData = "{messageId=mockMessageId, eventType=pushTracking.applicationOpened, applicationOpened=true, adobe_xdm=null}";
        String expectedEdgeEventData = "{xdm={pushNotificationTracking={pushProviderMessageID=mockMessageId, pushProvider=fcm}, application={launches={value=1}}, eventType=pushTracking.applicationOpened}, meta={collect={datasetId=somedatasetid}}}";

        // test
        Messaging.handleNotificationResponse(intent, true, null);

        // verify messaging event
        List<Event> messagingRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.MESSAGING,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, messagingRequestEvents.size());
        assertEquals(expectedMessagingEventData, messagingRequestEvents.get(0).getEventData().toString());

        // verify push tracking edge event
        List<Event> edgeRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.EDGE,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, edgeRequestEvents.size());
        assertEquals(expectedEdgeEventData, edgeRequestEvents.get(0).getEventData().toString());
    }

    @Test
    public void testHandleNotificationResponse_emptyDatasetId() throws InterruptedException {
        // update config
        HashMap<String, Object> config = new HashMap<String, Object>() {
            {
                put("messaging.eventDataset", "");
            }
        };
        MobileCore.updateConfiguration(config);

        TestHelper.waitForThreads(1000);

        // Parameters
        Intent intent = getResponseIntent();
        String mockCustomActionId = "mockCustomActionId";
        String expectedMessagingEventData = "{messageId=mockMessageId, actionId=mockCustomActionId, eventType=pushTracking.customAction, applicationOpened=true, adobe_xdm={\n" +
                "            cjm ={\n" +
                "              _experience= {\n" +
                "                customerJourneyManagement= {\n" +
                "                  messageExecution= {\n" +
                "                    messageExecutionID= 16-Sept-postman, \n" +
                "                    messageID= 567, \n" +
                "                    journeyVersionID= some-journeyVersionId, \n" +
                "                    journeyVersionInstanceID= someJourneyVersionInstanceID\n" +
                "                  }\n" +
                "                }\n" +
                "              }\n" +
                "            }\n" +
                "          }}";

        // test
        Messaging.handleNotificationResponse(intent, true, mockCustomActionId);

        // verify messaging event
        List<Event> messagingRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.MESSAGING,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, messagingRequestEvents.size());
        assertEquals(expectedMessagingEventData, messagingRequestEvents.get(0).getEventData().toString());

        // verify no push tracking edge event
        List<Event> edgeRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.EDGE,
                EventSource.REQUEST_CONTENT);
        assertEquals(0, edgeRequestEvents.size());
    }

    // --------------------------------------------------------------------------------------------
    // Tests for Messaging.refreshInAppMessages API
    // --------------------------------------------------------------------------------------------
    @Test
    public void testRefreshInAppMessages() throws InterruptedException {
        // test
        Messaging.refreshInAppMessages();
        TestHelper.sleep(500);

        // verify messaging request content event
        final List<Event> messagingRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.MESSAGING,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, messagingRequestEvents.size());
        final Map<String, Object> messagingEventData = messagingRequestEvents.get(0).getEventData();
        assertEquals(true, messagingEventData.get("refreshmessages"));

        // verify edge request content event
        final List<Event> edgePersonalizationRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.EDGE,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, edgePersonalizationRequestEvents.size());
        final Map<String, Object> edgeEventData = edgePersonalizationRequestEvents.get(0).getEventData();
        final Map<String, Object> xdmDataMap = DataReader.optTypedMap(Object.class, edgeEventData, "xdm", null);
        final Map<String, Object> queryDataMap = DataReader.optTypedMap(Object.class, edgeEventData, "query", null);
        final Map<String, Object> personalizationDataMap = DataReader.optTypedMap(Object.class, queryDataMap, "personalization", null);
        final List<String> surfacesList = DataReader.optStringList(personalizationDataMap, "surfaces", null);
        assertEquals("personalization.request", xdmDataMap.get("eventType"));
        assertEquals(1, surfacesList.size());
        assertEquals("mobileapp://com.adobe.marketing.mobile.messaging.test", surfacesList.get(0));
    }

    // --------------------------------------------------------------------------------------------
    // Tests for Messaging.updatePropositionsForSurfacePaths API
    // --------------------------------------------------------------------------------------------
    @Test
    public void testUpdatePropositionsForSurfacePaths() throws InterruptedException {
        // setup
        final List<Surface> surfacePaths = new ArrayList<>();
        surfacePaths.add(new Surface("promos/feed1"));
        surfacePaths.add(new Surface("promos/feed2"));
        final List<String> expectedSurfaces = new ArrayList<>();
        expectedSurfaces.add("mobileapp://com.adobe.marketing.mobile.messaging.test/promos/feed1");
        expectedSurfaces.add("mobileapp://com.adobe.marketing.mobile.messaging.test/promos/feed2");

        // test
        Messaging.updatePropositionsForSurfaces(surfacePaths);
        TestHelper.sleep(500);

        // verify messaging request content event
        final List<Event> messagingRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.MESSAGING,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, messagingRequestEvents.size());
        final Map<String, Object> messagingEventData = messagingRequestEvents.get(0).getEventData();
        assertEquals(true, messagingEventData.get("updatepropositions"));
        assertEquals(expectedSurfaces, messagingEventData.get("surfaces"));

        // verify edge request content event
        final List<Event> edgePersonalizationRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.EDGE,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, edgePersonalizationRequestEvents.size());
        final Map<String, Object> edgeEventData = edgePersonalizationRequestEvents.get(0).getEventData();
        final Map<String, Object> xdmDataMap = DataReader.optTypedMap(Object.class, edgeEventData, "xdm", null);
        final Map<String, Object> queryDataMap = DataReader.optTypedMap(Object.class, edgeEventData, "query", null);
        final Map<String, Object> personalizationDataMap = DataReader.optTypedMap(Object.class, queryDataMap, "personalization", null);
        final List<String> surfacesList = DataReader.optStringList(personalizationDataMap, "surfaces", null);
        assertEquals("personalization.request", xdmDataMap.get("eventType"));
        assertEquals(2, surfacesList.size());
        assertEquals("mobileapp://com.adobe.marketing.mobile.messaging.test/promos/feed1", surfacesList.get(0));
        assertEquals("mobileapp://com.adobe.marketing.mobile.messaging.test/promos/feed2", surfacesList.get(1));
    }

    @Test
    public void testUpdatePropositionsForSurfacePaths_somePathsInvalid() throws InterruptedException {
        // setup
        final List<Surface> surfacePaths = new ArrayList<>();
        surfacePaths.add(new Surface("promos/feed1"));
        surfacePaths.add(new Surface("##invalid##"));
        surfacePaths.add(new Surface("##alsoinvalid!"));
        surfacePaths.add(new Surface("promos/feed2"));
        final List<String> expectedSurfaces = new ArrayList<>();
        expectedSurfaces.add("mobileapp://com.adobe.marketing.mobile.messaging.test/promos/feed1");
        expectedSurfaces.add("mobileapp://com.adobe.marketing.mobile.messaging.test/promos/feed2");

        // test
        Messaging.updatePropositionsForSurfaces(surfacePaths);
        TestHelper.sleep(500);

        // verify messaging request content event
        final List<Event> messagingRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.MESSAGING,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, messagingRequestEvents.size());
        final Map<String, Object> messagingEventData = messagingRequestEvents.get(0).getEventData();
        assertEquals(true, messagingEventData.get("updatepropositions"));
        assertEquals(expectedSurfaces, messagingEventData.get("surfaces"));

        // verify edge request content event
        final List<Event> edgePersonalizationRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.EDGE,
                EventSource.REQUEST_CONTENT);
        assertEquals(1, edgePersonalizationRequestEvents.size());
        final Map<String, Object> edgeEventData = edgePersonalizationRequestEvents.get(0).getEventData();
        final Map<String, Object> xdmDataMap = DataReader.optTypedMap(Object.class, edgeEventData, "xdm", null);
        final Map<String, Object> queryDataMap = DataReader.optTypedMap(Object.class, edgeEventData, "query", null);
        final Map<String, Object> personalizationDataMap = DataReader.optTypedMap(Object.class, queryDataMap, "personalization", null);
        final List<String> surfacesList = DataReader.optStringList(personalizationDataMap, "surfaces", null);
        assertEquals("personalization.request", xdmDataMap.get("eventType"));
        assertEquals(2, surfacesList.size());
        assertEquals("mobileapp://com.adobe.marketing.mobile.messaging.test/promos/feed1", surfacesList.get(0));
        assertEquals("mobileapp://com.adobe.marketing.mobile.messaging.test/promos/feed2", surfacesList.get(1));
    }

    @Test
    public void testUpdatePropositionsForSurfacePaths_emptyPaths() throws InterruptedException {
        // setup
        final List<Surface> surfacePaths = new ArrayList<>();
        // test
        Messaging.updatePropositionsForSurfaces(surfacePaths);
        TestHelper.sleep(500);

        // verify no messaging request content event
        final List<Event> messagingRequestEvents = getDispatchedEventsWith(MessagingTestConstants.EventType.MESSAGING,
                EventSource.REQUEST_CONTENT);
        assertEquals(0, messagingRequestEvents.size());
    }

    // ========================================================================================
    // Tests for Messaging.setPropositionsHandler API
    // ========================================================================================
    private static final String TITLE = "title";
    private static final String BODY = "body";
    private static final String IMAGE_URL = "imageUrl";
    private static final String ACTION_URL = "actionUrl";
    private static final String ACTION_TITLE = "actionTitle";
    private static final String TEST_TITLE = "testTitle";
    private static final String TEST_BODY = "testBody";
    private static final String TEST_IMAGE_URL = "testImageUrl";
    private static final String TEST_ACTION_URL = "testActionUrl";
    private static final String TEST_ACTION_TITLE = "testActionTitle";
    private static final String EXPECTED_SURFACE_URI = "mobileapp://com.adobe.marketing.mobile.messaging.test";

    Map<String, Object> toEventData(FeedItem feedItem) {
        final Map<String, Object> feedItemAsMap = new HashMap<>();
        feedItemAsMap.put(TITLE, feedItem.getTitle());
        feedItemAsMap.put(BODY, feedItem.getBody());
        feedItemAsMap.put(IMAGE_URL, feedItem.getImageUrl());
        feedItemAsMap.put(ACTION_URL, feedItem.getActionUrl());
        feedItemAsMap.put(ACTION_TITLE, feedItem.getActionTitle());
        return feedItemAsMap;
    }

    @Test
    public void test_setPropositionsHandler() throws InterruptedException {
        // setup
        FeedItem feedItem = new FeedItem.Builder(TEST_TITLE, TEST_BODY)
                .setImageUrl(TEST_IMAGE_URL)
                .setActionUrl(TEST_ACTION_URL)
                .setActionTitle(TEST_ACTION_TITLE)
                .build();

        List<Map<String, Object>> feedItemList = new ArrayList<>();
        feedItemList.add(toEventData(feedItem));
        feedItemList.add(toEventData(feedItem));
        feedItemList.add(toEventData(feedItem));
        Map<String, Object> feedItemMap = new HashMap<>();
        feedItemMap.put("items", feedItemList);

        Map<String, Object> messageNotificationEventData = new HashMap<>();
        messageNotificationEventData.put(EXPECTED_SURFACE_URI, feedItemMap);

        Event messageNotificationEvent = new Event.Builder(MessagingTestConstants.EventName.MESSAGE_FEEDS_NOTIFICATION,
                EventType.MESSAGING, MessagingTestConstants.EventSource.NOTIFICATION).setEventData(messageNotificationEventData).build();

        // test
        MobileCore.dispatchEvent(messageNotificationEvent);

        // verify
        Map<Surface, List<FeedItem>>[] returnedPropositions = new Map[]{new HashMap<>()};
        CountDownLatch latch = new CountDownLatch(1);
        Messaging.setPropositionsHandler(value -> {
            returnedPropositions[0] = value;
            latch.countDown();
        });
        latch.await(5, TimeUnit.SECONDS);

        assertNotNull(returnedPropositions[0]);
        for (Map.Entry<Surface, List<FeedItem>> returnedProposition : returnedPropositions[0].entrySet()) {
            assertEquals(EXPECTED_SURFACE_URI, returnedProposition.getKey().getUri());
            assertEquals(3, returnedProposition.getValue().size());
            for (FeedItem returnedFeedItem : returnedProposition.getValue()) {
                assertEquals(TEST_TITLE, returnedFeedItem.getTitle());
                assertEquals(TEST_BODY, returnedFeedItem.getBody());
                assertEquals(TEST_ACTION_TITLE, returnedFeedItem.getActionTitle());
                assertEquals(TEST_ACTION_URL, returnedFeedItem.getActionUrl());
                assertEquals(TEST_IMAGE_URL, returnedFeedItem.getImageUrl());
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------------------
    private Intent getResponseIntent() {
        Intent intent = new Intent();
        intent.putExtra(MessagingTestConstants.EventDataKeys.Messaging.TRACK_INFO_KEY_MESSAGE_ID, "mockMessageId");
        intent.putExtra(MessagingTestConstants.EventDataKeys.Messaging.TRACK_INFO_KEY_ADOBE_XDM, "{\n            cjm ={\n              _experience= {\n                customerJourneyManagement= {\n                  messageExecution= {\n                    messageExecutionID= 16-Sept-postman, \n                    messageID= 567, \n                    journeyVersionID= some-journeyVersionId, \n                    journeyVersionInstanceID= someJourneyVersionInstanceID\n                  }\n                }\n              }\n            }\n          }");
        return intent;
    }
}

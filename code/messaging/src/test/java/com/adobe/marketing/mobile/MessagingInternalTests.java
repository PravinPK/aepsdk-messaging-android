/*
  Copyright 2020 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import android.app.Application;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ExtensionApi.class, ExtensionUnexpectedError.class, MessagingState.class, PlatformServices.class, LocalStorageService.class, Edge.class, ExperienceEvent.class, App.class, Context.class})
public class MessagingInternalTests {

    private int EXECUTOR_TIMEOUT = 5;
    private MessagingInternal messagingInternal;

    // Mocks
    @Mock
    ExtensionApi mockExtensionApi;
    @Mock
    ExtensionUnexpectedError mockExtensionUnexpectedError;
    @Mock
    MessagingState messagingState;
    @Mock
    PlatformServices mockPlatformServices;
    @Mock
    LocalStorageService mockLocalStorageService;
    @Mock
    NetworkService mockNetworkService;
    @Mock
    Map<String, Object> mockConfigData;
    @Mock
    ConcurrentLinkedQueue<Event> mockEventQueue;
    @Mock
    Application mockApplication;
    @Mock
    Context context;

    @Before
    public void setup() {
        PowerMockito.mockStatic(Edge.class);
        PowerMockito.mockStatic(ExperienceEvent.class);
        PowerMockito.mockStatic(App.class);
        Mockito.when(App.getAppContext()).thenReturn(context);
        messagingInternal = new MessagingInternal(mockExtensionApi);
    }

    // ========================================================================================
    // constructor
    // ========================================================================================
    @Test
    public void test_Constructor() {
        // verify 3 listeners are registered
        verify(mockExtensionApi, times(1)).registerListener(eq(EventType.CONFIGURATION),
                eq(EventSource.RESPONSE_CONTENT), eq(ConfigurationResponseContentListener.class));
        verify(mockExtensionApi, times(1)).registerListener(eq(EventType.GENERIC_DATA),
                eq(EventSource.OS), eq(GenericDataOSListener.class));
        verify(mockExtensionApi, times(1)).registerListener(eq(EventType.GENERIC_IDENTITY),
                eq(EventSource.REQUEST_CONTENT), eq(IdentityRequestContentListener.class));
    }

    // ========================================================================================
    // getName
    // ========================================================================================
    @Test
    public void test_getName() {
        // test
        String moduleName = messagingInternal.getName();
        assertEquals("getName should return the correct module name", MessagingConstant.EXTENSION_NAME, moduleName);
    }

    // ========================================================================================
    // getVersion
    // ========================================================================================
    @Test
    public void test_getVersion() {
        // test
        String moduleVersion = messagingInternal.getVersion();
        assertEquals("getVesion should return the correct module version", MessagingConstant.EXTENSION_VERSION,
                moduleVersion);
    }

    // ========================================================================================
    // onUnexpectedError
    // ========================================================================================
    @Test
    public void test_onUnexpectedError() {
        // test
        messagingInternal.onUnexpectedError(mockExtensionUnexpectedError);
        verify(mockExtensionApi, times(1)).clearSharedEventStates(null);
    }

    // ========================================================================================
    // onUnregistered
    // ========================================================================================

    @Test
    public void test_onUnregistered() {
        // test
        messagingInternal.onUnregistered();
        verify(mockExtensionApi, times(1)).clearSharedEventStates(null);
    }

    // ========================================================================================
    // queueEvent
    // ========================================================================================
    @Test
    public void test_QueueEvent() {
        // test 1
        assertNotNull("EventQueue instance is should never be null", messagingInternal.getEventQueue());

        // test 2
        Event sampleEvent = new Event.Builder("event 1", "eventType", "eventSource").build();
        messagingInternal.queueEvent(sampleEvent);
        assertEquals("The size of the eventQueue should be correct", 1, messagingInternal.getEventQueue().size());

        // test 3
        messagingInternal.queueEvent(null);
        assertEquals("The size of the eventQueue should be correct", 1, messagingInternal.getEventQueue().size());

        // test 4
        Event anotherEvent = new Event.Builder("event 2", "eventType", "eventSource").build();
        messagingInternal.queueEvent(anotherEvent);
        assertEquals("The size of the eventQueue should be correct", 2, messagingInternal.getEventQueue().size());
    }

    // ========================================================================================
    // processEvents
    // ========================================================================================
    @Test
    public void test_processEvents_when_noEventInQueue() {
        // Mocks
        ExtensionErrorCallback<ExtensionError> mockCallback = new ExtensionErrorCallback<ExtensionError>() {
            @Override
            public void error(ExtensionError extensionError) {

            }
        };
        Event mockEvent = new Event.Builder("event 2", "eventType", "eventSource").build();

        // test
        messagingInternal.processEvents();

        // verify
        verify(mockExtensionApi, times(0)).getSharedEventState(MessagingConstant.SharedState.Configuration.EXTENSION_NAME, mockEvent, mockCallback);
        verify(mockExtensionApi, times(0)).getSharedEventState(MessagingConstant.SharedState.Identity.EXTENSION_NAME, mockEvent, mockCallback);
    }

    @Test
    public void test_processEvents_when_handlingPushToken_withPrivacyOptIn() {
        // private mocks
        Whitebox.setInternalState(messagingInternal, "messagingState", messagingState);
        Whitebox.setInternalState(messagingInternal, "platformServices", mockPlatformServices);

        // Mocks
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(MessagingConstant.EventDataKeys.Identity.PUSH_IDENTIFIER, "mock_token");
        Event mockEvent = new Event.Builder("handlePushToken", EventType.GENERIC_IDENTITY.getName(), EventSource.REQUEST_CONTENT.getName()).setEventData(eventData).build();

        // when configState containsKey return true
        when(mockExtensionApi.getSharedEventState(anyString(), any(Event.class),
                any(ExtensionErrorCallback.class))).thenReturn(mockConfigData);
        when(mockConfigData.containsKey(anyString())).thenReturn(true);

        // when getLocalStorageService() return mockLocalStorageService
        LocalStorageService.DataStore mockDataStore = mock(LocalStorageService.DataStore.class);
        when(mockPlatformServices.getLocalStorageService()).thenReturn(mockLocalStorageService);
        when(mockLocalStorageService.getDataStore(anyString())).thenReturn(mockDataStore);

        // when get privacy status retirn opt in
        when(messagingState.getPrivacyStatus()).thenReturn(MobilePrivacyStatus.OPT_IN);

        // test
        messagingInternal.queueEvent(mockEvent);
        messagingInternal.processEvents();

        // verify
        verify(mockPlatformServices, times(1)).getLocalStorageService();
        verify(mockPlatformServices, times(1)).getNetworkService();
        verify(mockLocalStorageService, times(1)).getDataStore("AdobeMobile_ExperienceMessage");
        verify(mockDataStore, times(1)).setString(anyString(), anyString());
    }

    @Test
    public void test_processEvents_when_handlingTrackingInfo() {
        // Mocks
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_EVENT_TYPE, "mock_event_type");
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_MESSAGE_ID, "mock_message_id");
        Event mockEvent = new Event.Builder("handleTrackingInfo", EventType.GENERIC_DATA.getName(), EventSource.OS.getName()).setEventData(eventData).build();

        // when configState containsKey return true
        when(mockExtensionApi.getSharedEventState(anyString(), any(Event.class),
                any(ExtensionErrorCallback.class))).thenReturn(mockConfigData);
        when(mockConfigData.containsKey(anyString())).thenReturn(true);

        // when getExperienceEventDatasetId return mock datasetId
        when(messagingState.getExperienceEventDatasetId()).thenReturn("mock_dataset_id");

        // test
        messagingInternal.queueEvent(mockEvent);
        messagingInternal.processEvents();

        // verify
        PowerMockito.verifyStatic(Edge.class, times(1));
        Edge.sendEvent(any(ExperienceEvent.class), any(EdgeCallback.class));
    }

    // ========================================================================================
    // processConfigurationResponse
    // ========================================================================================
    @Test
    public void test_processConfigurationResponse_when_NullEvent() {
        // test
        messagingInternal.processConfigurationResponse(null);

        // verify
        verify(messagingState, times(0)).setState(any(EventData.class), any(EventData.class));
    }

    @Test
    public void test_processConfigurationResponse_when_privacyOptOut() {
        // dummy executor
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // private mocks
        Whitebox.setInternalState(messagingInternal, "messagingState", messagingState);
        Whitebox.setInternalState(messagingInternal, "platformServices", mockPlatformServices);
        Whitebox.setInternalState(messagingInternal, "eventQueue", mockEventQueue);
        Whitebox.setInternalState(messagingInternal, "executorService", executor);

        // Mocks
        Event mockEvent = new Event.Builder("event1", EventType.CONFIGURATION.getName(), EventSource.RESPONSE_CONTENT.getName()).setEventData(mockConfigData).build();
        when(mockExtensionApi.getSharedEventState(anyString(), any(Event.class))).thenReturn(mockEvent.getData());

        // when
        when(messagingState.getPrivacyStatus()).thenReturn(MobilePrivacyStatus.OPT_OUT);

        // when getLocalStorageService() return mockLocalStorageService
        LocalStorageService.DataStore mockDataStore = mock(LocalStorageService.DataStore.class);
        when(mockPlatformServices.getLocalStorageService()).thenReturn(mockLocalStorageService);
        when(mockLocalStorageService.getDataStore(anyString())).thenReturn(mockDataStore);

        //test
        messagingInternal.processConfigurationResponse(mockEvent);
        TestUtils.waitForExecutor(executor, EXECUTOR_TIMEOUT);

        // verify
        verify(messagingState, times(1)).setState(mockEvent.getData(), mockEvent.getData());
        verify(mockPlatformServices, times(1)).getLocalStorageService();
        verify(mockLocalStorageService, times(1)).getDataStore("AdobeMobile_ExperienceMessage");
        verify(mockDataStore, times(1)).remove("pushIdentifier");
        verify(mockEventQueue, times(1)).clear();
    }

    @Test
    public void test_processConfigurationResponse_when_privacyOptIn() {
        // private mocks
        Whitebox.setInternalState(messagingInternal, "messagingState", messagingState);
        Whitebox.setInternalState(messagingInternal, "platformServices", mockPlatformServices);
        Whitebox.setInternalState(messagingInternal, "eventQueue", mockEventQueue);

        // Mocks
        Event mockEvent = new Event.Builder("event1", EventType.CONFIGURATION.getName(), EventSource.RESPONSE_CONTENT.getName()).setEventData(mockConfigData).build();
        when(mockExtensionApi.getSharedEventState(anyString(), any(Event.class))).thenReturn(mockEvent.getData());
        // Mocks
        ExtensionErrorCallback<ExtensionError> mockCallback = new ExtensionErrorCallback<ExtensionError>() {
            @Override
            public void error(ExtensionError extensionError) {

            }
        };

        // when
        when(messagingState.getPrivacyStatus()).thenReturn(MobilePrivacyStatus.OPT_IN);

        // when getLocalStorageService() return mockLocalStorageService
        LocalStorageService.DataStore mockDataStore = mock(LocalStorageService.DataStore.class);
        when(mockPlatformServices.getLocalStorageService()).thenReturn(mockLocalStorageService);
        when(mockLocalStorageService.getDataStore(anyString())).thenReturn(mockDataStore);

        //test
        messagingInternal.processConfigurationResponse(mockEvent);

        // verify
        verify(messagingState, times(1)).setState(mockEvent.getData(), mockEvent.getData());
        verify(mockPlatformServices, times(0)).getLocalStorageService();
        verify(mockEventQueue, times(0)).clear();
        verify(mockExtensionApi, times(0)).getSharedEventState(MessagingConstant.SharedState.Configuration.EXTENSION_NAME, mockEvent, mockCallback);
        verify(mockExtensionApi, times(0)).getSharedEventState(MessagingConstant.SharedState.Identity.EXTENSION_NAME, mockEvent, mockCallback);
    }

    // ========================================================================================
    // handlePushToken
    // ========================================================================================
    @Test
    public void test_handlePushToken_when_WrongEventType() {
        // Mocks
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(MessagingConstant.EventDataKeys.Identity.PUSH_IDENTIFIER, "mock_push_token");
        Event mockEvent = new Event.Builder("event1", EventType.GENERIC_DATA.getName(), EventSource.REQUEST_CONTENT.getName()).setEventData(eventData).build();

        // private mocks
        Whitebox.setInternalState(messagingInternal, "messagingState", messagingState);
        Whitebox.setInternalState(messagingInternal, "platformServices", mockPlatformServices);

        //test
        messagingInternal.handlePushToken(mockEvent);

        // verify
        verify(messagingState, times(0)).getPrivacyStatus();
    }

    @Test
    public void test_handlePushToken_when_privacyOptOut() {
        // Mocks
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(MessagingConstant.EventDataKeys.Identity.PUSH_IDENTIFIER, "mock_push_token");
        Event mockEvent = new Event.Builder("event1", EventType.GENERIC_IDENTITY.getName(), EventSource.REQUEST_CONTENT.getName()).setEventData(eventData).build();

        // private mocks
        Whitebox.setInternalState(messagingInternal, "messagingState", messagingState);
        Whitebox.setInternalState(messagingInternal, "platformServices", mockPlatformServices);

        // when
        when(messagingState.getPrivacyStatus()).thenReturn(MobilePrivacyStatus.OPT_OUT);

        //test
        messagingInternal.handlePushToken(mockEvent);

        // verify
        verify(messagingState, times(2)).getPrivacyStatus();
        verify(mockPlatformServices, times(0)).getLocalStorageService();
        verify(mockPlatformServices, times(0)).getNetworkService();
    }

    @Test
    public void test_handlePushToken_when_privacyOptIn() {
        // Mocks
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(MessagingConstant.EventDataKeys.Identity.PUSH_IDENTIFIER, "mock_push_token");
        Event mockEvent = new Event.Builder("event1", EventType.GENERIC_IDENTITY.getName(), EventSource.REQUEST_CONTENT.getName()).setEventData(eventData).build();
        String mockECID = "mock_ecid";
        String mockDccsUrl = "mock_dccs_url";
        String mockExperienceCloudOrg = "mock_exp_org";
        String mockProfileDatasetId = "mock_profileDatasetId";

        // private mocks
        Whitebox.setInternalState(messagingInternal, "messagingState", messagingState);
        Whitebox.setInternalState(messagingInternal, "platformServices", mockPlatformServices);

        // when
        when(messagingState.getPrivacyStatus()).thenReturn(MobilePrivacyStatus.OPT_IN);
        when(messagingState.getEcid()).thenReturn(mockECID);
        when(messagingState.getDccsURL()).thenReturn(mockDccsUrl);
        when(messagingState.getProfileDatasetId()).thenReturn(mockProfileDatasetId);
        when(messagingState.getExperienceCloudOrg()).thenReturn(mockExperienceCloudOrg);

        // when getLocalStorageService() return mockLocalStorageService
        LocalStorageService.DataStore mockDataStore = mock(LocalStorageService.DataStore.class);
        when(mockPlatformServices.getLocalStorageService()).thenReturn(mockLocalStorageService);
        when(mockLocalStorageService.getDataStore(anyString())).thenReturn(mockDataStore);

        // when getNetworkService() return mockNetworkService
        when(mockPlatformServices.getNetworkService()).thenReturn(mockNetworkService);

        // when App.getApplication().getPackageName() return mock packageName
        when(App.getApplication()).thenReturn(mockApplication);
        when(mockApplication.getPackageName()).thenReturn("mock_package");

        //test
        messagingInternal.handlePushToken(mockEvent);

        // verify
        verify(messagingState, times(2)).getPrivacyStatus();
        verify(mockPlatformServices, times(1)).getLocalStorageService();
        verify(mockPlatformServices, times(1)).getNetworkService();
        verify(mockNetworkService, times(1)).connectUrl(anyString(), any(NetworkService.HttpCommand.class), any(byte[].class), ArgumentMatchers.<String, String>anyMap(), anyInt(), anyInt());
    }

    // ========================================================================================
    // handleTrackingInfo
    // ========================================================================================
    @Test
    public void test_handleTrackingInfo_when_EventDataNull() {
        // Mocks
        Event mockEvent = new Event.Builder("event1", EventType.GENERIC_DATA.getName(), EventSource.REQUEST_CONTENT.getName()).setEventData(null).build();

        // private mocks
        Whitebox.setInternalState(messagingInternal, "messagingState", messagingState);

        //test
        messagingInternal.handleTrackingInfo(mockEvent);

        // verify
        verify(messagingState, times(0)).getExperienceEventDatasetId();
    }

    @Test
    public void test_handleTrackingInfo_when_eventTypeIsNull() {
        // Mocks
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_EVENT_TYPE, null);
        Event mockEvent = new Event.Builder("event1", EventType.GENERIC_DATA.getName(), EventSource.REQUEST_CONTENT.getName()).setEventData(eventData).build();

        // private mocks
        Whitebox.setInternalState(messagingInternal, "messagingState", messagingState);

        //test
        messagingInternal.handleTrackingInfo(mockEvent);

        // verify
        // verify
        verify(messagingState, times(0)).getExperienceEventDatasetId();
    }

    @Test
    public void test_handleTrackingInfo_when_MessageIdIsNull() {
        // Mocks
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_EVENT_TYPE, "mock_eventType");
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_MESSAGE_ID, null);
        Event mockEvent = new Event.Builder("event1", EventType.GENERIC_DATA.getName(), EventSource.REQUEST_CONTENT.getName()).setEventData(eventData).build();

        // private mocks
        Whitebox.setInternalState(messagingInternal, "messagingState", messagingState);

        //test
        messagingInternal.handleTrackingInfo(mockEvent);

        // verify
        // verify
        verify(messagingState, times(0)).getExperienceEventDatasetId();
    }

    @Test
    public void test_handleTrackingInfo() {
        final ArgumentCaptor<ExperienceEvent> eventCaptor = ArgumentCaptor.forClass(ExperienceEvent.class);

        // Mocks
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_EVENT_TYPE, "mock_eventType");
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_MESSAGE_ID, "mock_messageId");
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_ACTION_ID, "mock_actionId");
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_APPLICATION_OPENED, true);
        Event mockEvent = new Event.Builder("event1", EventType.GENERIC_DATA.getName(), EventSource.OS.getName()).setEventData(eventData).build();

        // private mocks
        Whitebox.setInternalState(messagingInternal, "messagingState", messagingState);

        //test
        messagingInternal.handleTrackingInfo(mockEvent);

        // verify
        verify(messagingState, times(1)).getExperienceEventDatasetId();

        PowerMockito.verifyStatic(Edge.class);
        Edge.sendEvent(eventCaptor.capture(), any(EdgeCallback.class));

        // verify event
        ExperienceEvent event = eventCaptor.getValue();
        assertNotNull(event.getXdmSchema());
        assertEquals("mock_eventType", event.getXdmSchema().get("eventType"));

        // verify the applicationOpened is added to adobe standard mixin for application
        int value = (int)((Map<String, Object>)(((Map<String, Object>)event.getXdmSchema().get(MessagingConstant.TrackingKeys.APPLICATION)).get(MessagingConstant.TrackingKeys.LAUNCHES))).get(MessagingConstant.TrackingKeys.LAUNCHES_VALUE);
        assertEquals(1, value);
    }

    @Test
    public void test_handleTrackingInfo_when_cjmData() {
        final ArgumentCaptor<ExperienceEvent> eventCaptor = ArgumentCaptor.forClass(ExperienceEvent.class);
        final String mockCJMData = "{\n" +
                "        \"cjm\" :{\n" +
                "          \"_experience\": {\n" +
                "            \"customerJourneyManagement\": {\n" +
                "              \"messageExecution\": {\n" +
                "                \"messageExecutionID\": \"16-Sept-postman\",\n" +
                "                \"messageID\": \"567\",\n" +
                "                \"journeyVersionID\": \"some-journeyVersionId\",\n" +
                "                \"journeyVersionInstanceId\": \"someJourneyVersionInstanceId\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }";

        // Mocks
        Map<String, Object> eventData = new HashMap<>();
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_EVENT_TYPE, "mock_eventType");
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_MESSAGE_ID, "mock_messageId");
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_ACTION_ID, "mock_actionId");
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_APPLICATION_OPENED, "mock_application_opened");
        eventData.put(MessagingConstant.EventDataKeys.Messaging.TRACK_INFO_KEY_ADOBE, mockCJMData);
        Event mockEvent = new Event.Builder("event1", EventType.GENERIC_DATA.getName(), EventSource.OS.getName()).setEventData(eventData).build();

        // private mocks
        Whitebox.setInternalState(messagingInternal, "messagingState", messagingState);

        //test
        messagingInternal.handleTrackingInfo(mockEvent);

        // verify
        verify(messagingState, times(1)).getExperienceEventDatasetId();

        PowerMockito.verifyStatic(Edge.class);
        Edge.sendEvent(eventCaptor.capture(), any(EdgeCallback.class));

        // verify event
        ExperienceEvent event = eventCaptor.getValue();
        assertNotNull(event.getXdmSchema());
        assertEquals("mock_eventType", event.getXdmSchema().get("eventType"));
        // Verify _experience exist
        assertTrue(event.getXdmSchema().containsKey(MessagingConstant.TrackingKeys.EXPERIENCE));
    }

    // ========================================================================================
    // getExecutor
    // ========================================================================================
    @Test
    public void test_getExecutor_NeverReturnsNull() {
        // test
        ExecutorService executorService = messagingInternal.getExecutor();
        assertNotNull("The executor should not return null", executorService);

        // verify
        assertEquals("Gets the same executor instance on the next get", executorService, messagingInternal.getExecutor());
    }
}
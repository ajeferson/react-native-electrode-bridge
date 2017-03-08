package com.walmartlabs.electrode.reactnative.bridge;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.test.InstrumentationTestCase;

import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.shell.MainReactPackage;
import com.walmartlabs.electrode.reactnative.bridge.helpers.Logger;
import com.walmartlabs.electrode.reactnative.bridge.util.BridgeArguments;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BaseBridgeTestCase extends InstrumentationTestCase {

    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Logger.overrideLogLevel(Logger.LogLevel.DEBUG);
        initBridge();
    }

    private void initBridge() {
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        ReactInstanceManager reactInstanceManager = null;
        final ElectrodeBridgePackage mockElectrodePackage = new MockElectrodePackage();
        try {
            reactInstanceManager = ReactInstanceManager.builder()
                    .setApplication(this.getInstrumentation().newApplication(MyTestApplication.class.getClassLoader(), MyTestApplication.class.getName(), getInstrumentation().getContext()))
                    .setBundleAssetName("index.android.bundle")
                    .setJSMainModuleName("index.android")
                    .addPackage(new MainReactPackage())
                    .setUseDeveloperSupport(false)
                    .setInitialLifecycleState(LifecycleState.BEFORE_CREATE)
                    .addPackage(mockElectrodePackage)
                    .build();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }


        if (reactInstanceManager != null) {
            final ReactInstanceManager finalReactInstanceManager = reactInstanceManager;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    finalReactInstanceManager.createReactContextInBackground();
                }
            });

            reactInstanceManager.addReactInstanceEventListener(new ReactInstanceManager.ReactInstanceEventListener() {
                @Override
                public void onReactContextInitialized(ReactContext context) {
                    mockElectrodePackage.onReactNativeInitialized();
                    countDownLatch.countDown();
                }
            });
        }

        waitForCountDownToFinishOrFail(countDownLatch);
    }

    private class MockElectrodePackage extends ElectrodeBridgePackage {

        @Override
        public List<NativeModule> createNativeModules(final ReactApplicationContext reactContext) {
            List<NativeModule> modules = new ArrayList<>();
            this.electrodeBridgeInternal = ElectrodeBridgeInternal.create(getReactContextWrapper(reactContext));
            modules.add(electrodeBridgeInternal);
            return modules;
        }
    }

    private void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    void waitForCountDownToFinishOrFail(CountDownLatch countDown) {
        try {
            assertTrue(countDown.await(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail();
        }
    }

    /**
     * Returns a default ReactContextWrapper, overide if needed.
     *
     * @param reactContext {@link ReactApplicationContext}
     * @return ReactContextWrapper
     */
    @NonNull
    private ReactContextWrapper getReactContextWrapper(final ReactApplicationContext reactContext) {
        return new ReactContextWrapper() {
            @Override
            public void emitEvent(@NonNull BridgeMessage event) {
                assertNotNull(event);
                assertNotNull(event.getName());
                mockJsEventHandler(event.map());
            }

            @Override
            public void runOnUiQueueThread(@NonNull Runnable runnable) {
                runOnUiThread(runnable);
            }

            @NonNull
            @Override
            public ReactApplicationContext getContext() {
                return reactContext;
            }
        };
    }

    /**
     * This is mainly exposed to mock a JS side event handling. This is called when {@link ElectrodeBridge} emits en event to JS side to handle a request or event.
     *
     * @param inputMessage {@link WritableMap}
     */
    private void mockJsEventHandler(@Nullable final ReadableMap inputMessage) {
        assertNotNull(inputMessage);
        BridgeArguments.Type type = BridgeArguments.Type.getType(inputMessage.getString(BridgeMessage.BRIDGE_MSG_TYPE));
        assertNotNull(type);
        String eventName = inputMessage.getString(BridgeMessage.BRIDGE_MSG_NAME);
        assertNotNull(eventName);

        for (MockElectrodeEventListener listener : mockEventRegistrar.getEventListeners(eventName)) {

            switch (type) {
                case EVENT:
                    listener.onEvent(inputMessage);
                    break;
                case REQUEST:
                    listener.onRequest(inputMessage, new MockJsResponseDispatcher() {
                        @Override
                        public void dispatchResponse(@Nullable WritableMap responseData) {
                            WritableMap finalResponse = Arguments.createMap();
                            finalResponse.putString(ElectrodeBridgeResponse.BRIDGE_MSG_ID, inputMessage.getString(ElectrodeBridgeRequest.BRIDGE_MSG_ID));
                            finalResponse.putString(ElectrodeBridgeResponse.BRIDGE_MSG_NAME, inputMessage.getString(ElectrodeBridgeRequest.BRIDGE_MSG_NAME));
                            finalResponse.putString(ElectrodeBridgeResponse.BRIDGE_MSG_TYPE, BridgeArguments.Type.RESPONSE.getKey());
                            if (responseData != null) {
                                if (responseData.hasKey(ElectrodeBridgeResponse.BRIDGE_MSG_DATA)) {
                                    //This is used for response coming wth primitives instead of complex objects.
                                    finalResponse.merge(responseData);
                                } else {
                                    finalResponse.putMap(ElectrodeBridgeResponse.BRIDGE_MSG_DATA, responseData);
                                }
                            }
                            ElectrodeBridgeInternal.instance().dispatchEvent(finalResponse);
                        }
                    });
                    break;
                case RESPONSE:
                    listener.onResponse(inputMessage);
                    break;
            }
        }
    }

    private static final EventRegistrar<MockElectrodeEventListener> mockEventRegistrar = new EventRegistrarImpl<>();

    UUID addMockEventListener(@NonNull String eventName, @NonNull MockElectrodeEventListener mockElectrodeEventListener) {
        UUID uuid = mockEventRegistrar.registerEventListener(eventName, mockElectrodeEventListener);
        assertNotNull(uuid);
        assertTrue(mockEventRegistrar.getEventListeners(eventName).size() > 0);
        return uuid;
    }

    void removeMockEventListener(UUID uuid) {
        mockEventRegistrar.unregisterEventListener(uuid);
    }

    /**
     * Creates a MAP representation of a event coming from JS side with the given name and data.
     *
     * @param TEST_EVENT_NAME {@link String}
     * @param data            {@link WritableMap}
     * @return WritableMap
     */
    WritableMap createTestEventMap(String TEST_EVENT_NAME, @Nullable WritableMap data) {
        WritableMap eventMap = Arguments.createMap();
        eventMap.putString(ElectrodeBridgeEvent.BRIDGE_MSG_ID, ElectrodeBridgeEvent.getUUID());
        eventMap.putString(ElectrodeBridgeEvent.BRIDGE_MSG_NAME, TEST_EVENT_NAME);
        eventMap.putString(ElectrodeBridgeEvent.BRIDGE_MSG_TYPE, BridgeArguments.Type.EVENT.getKey());
        if (data != null) {
            eventMap.putMap(ElectrodeBridgeEvent.BRIDGE_MSG_DATA, data);
        }
        return eventMap;
    }


    /**
     * This interface is a mock representation of JS side receiving an event.
     */
    interface MockElectrodeEventListener {
        /**
         * Mocks JS side receiving a request
         */
        void onRequest(ReadableMap request, @NonNull MockJsResponseDispatcher jsResponseDispatcher);

        /**
         * Mocks JS side receiving a response
         */
        void onResponse(ReadableMap response);

        /**
         * Mocks JS side receiving an event
         */
        void onEvent(ReadableMap event);
    }

    interface MockJsResponseDispatcher {
        void dispatchResponse(@NonNull final WritableMap response);
    }

}

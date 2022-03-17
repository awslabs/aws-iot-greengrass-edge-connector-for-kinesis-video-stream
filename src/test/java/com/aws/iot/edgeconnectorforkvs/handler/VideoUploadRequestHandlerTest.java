/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aws.iot.edgeconnectorforkvs.handler;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import com.aws.iot.edgeconnectorforkvs.util.IPCUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.SubscribeToIoTCoreResponseHandler;
import software.amazon.awssdk.aws.greengrass.model.IoTCoreMessage;
import software.amazon.awssdk.aws.greengrass.model.MQTTMessage;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToIoTCoreResponse;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.eventstreamrpc.StreamResponseHandler;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
public class VideoUploadRequestHandlerTest {

    @Mock
    private EventStreamRPCConnection eventStreamRPCConnection;
    @Mock
    private GreengrassCoreIPCClient greengrassCoreIPCClient;
    @Mock
    private SubscribeToIoTCoreResponseHandler subscribeToIoTCoreResponseHandler;
    @Mock
    private CompletableFuture<SubscribeToIoTCoreResponse> responseCompletableFuture;
    @Mock
    private VideoUploadRequestEvent videoUploadRequestEvent;
    @Mock
    EdgeConnectorForKVSConfiguration configuration;
    @Mock
    private ExecutorService recorderService;
    @Mock
    private ExecutorService liveStreamingExecutor;
    @Mock
    private ScheduledExecutorService stopLiveStreamingExecutor;

    private VideoUploadRequestHandler videoUploadRequestHandler;

    private static final String ERR_MSG = "Connection Error";
    private static final long START_TIME = 1630985820;
    private static final long END_TIME = 1630985920;
    private static final long EVENT_TIMESTAMP = 1630985924;
    private static final boolean IS_LIVE_TRUE = true;
    private static final boolean IS_LIVE_FALSE = false;
    private static final String MQTT_TOPIC_NAME = "/test";
    private static final String MQTT_PAYLOAD_ON_DEMAND = "{" +
            "    \"type\":\"PropertyValueUpdate\"," +
            "    \"payload\":{" +
            "        \"assetId\":\"32c4c2a3-6065-4aa4-9431-4b738565ee5e\"," +
            "        \"propertyId\":\"b72b50dd-e235-49c8-b67c-d0cc7856fcff\"," +
            "        \"values\":[" +
            "            {" +
            "                \"timestamp\":{\"timeInSeconds\":1630985924,\"offsetInNanos\":0}," +
            "                \"quality\":\"GOOD\"," +
            "                \"value\":{" +
            "                    \"stringValue\":\"1630985820-1630985920\"" +
            "                 }" +
            "             }" +
            "         ]" +
            "     }" +
            "}";
    private static final String MQTT_PAYLOAD_INVALID_VALUE_WITHOUT_HYPHEN = "{" +
            "    \"type\":\"PropertyValueUpdate\"," +
            "    \"payload\":{" +
            "        \"assetId\":\"32c4c2a3-6065-4aa4-9431-4b738565ee5e\"," +
            "        \"propertyId\":\"b72b50dd-e235-49c8-b67c-d0cc7856fcff\"," +
            "        \"values\":[" +
            "            {" +
            "                \"timestamp\":{\"timeInSeconds\":1630985924,\"offsetInNanos\":0}," +
            "                \"quality\":\"GOOD\"," +
            "                \"value\":{" +
            "                    \"stringValue\":\"foobar\"" +
            "                 }" +
            "             }" +
            "         ]" +
            "     }" +
            "}";
    private static final String MQTT_PAYLOAD_INVALID_VALUE_WITH_HYPHEN = "{" +
            "    \"type\":\"PropertyValueUpdate\"," +
            "    \"payload\":{" +
            "        \"assetId\":\"32c4c2a3-6065-4aa4-9431-4b738565ee5e\"," +
            "        \"propertyId\":\"b72b50dd-e235-49c8-b67c-d0cc7856fcff\"," +
            "        \"values\":[" +
            "            {" +
            "                \"timestamp\":{\"timeInSeconds\":1630985924,\"offsetInNanos\":0}," +
            "                \"quality\":\"GOOD\"," +
            "                \"value\":{" +
            "                    \"stringValue\":\"foo-bar\"" +
            "                 }" +
            "             }" +
            "         ]" +
            "     }" +
            "}";
    private static final String MQTT_PAYLOAD_LIVE = "{" +
            "    \"type\":\"PropertyValueUpdate\"," +
            "    \"payload\":{" +
            "        \"assetId\":\"32c4c2a3-6065-4aa4-9431-4b738565ee5e\"," +
            "        \"propertyId\":\"b72b50dd-e235-49c8-b67c-d0cc7856fcff\"," +
            "        \"values\":[" +
            "            {" +
            "                \"timestamp\":{\"timeInSeconds\":1630985924,\"offsetInNanos\":0}," +
            "                \"quality\":\"GOOD\"," +
            "                \"value\":{" +
            "                    \"stringValue\":\"live\"" +
            "                 }" +
            "             }" +
            "         ]" +
            "     }" +
            "}";
    private static final String MQTT_PAYLOAD_MULTIPLE_VALUES = "{" +
            "    \"type\":\"PropertyValueUpdate\"," +
            "    \"payload\":{" +
            "        \"assetId\":\"32c4c2a3-6065-4aa4-9431-4b738565ee5e\"," +
            "        \"propertyId\":\"b72b50dd-e235-49c8-b67c-d0cc7856fcff\"," +
            "        \"values\":[" +
            "            {" +
            "                \"timestamp\":{\"timeInSeconds\":1630985924,\"offsetInNanos\":0}," +
            "                \"quality\":\"GOOD\"," +
            "                \"value\":{" +
            "                    \"stringValue\":\"1630985820-1630985920\"" +
            "                 }" +
            "             }," +
            "             {" +
            "                \"timestamp\":{\"timeInSeconds\":1630995924,\"offsetInNanos\":0}," +
            "                \"quality\":\"GOOD\"," +
            "                \"value\":{" +
            "                    \"stringValue\":\"1630995820-1630995920\"" +
            "                 }" +
            "             }" +
            "         ]" +
            "     }" +
            "}";

    @BeforeEach
    public void setup() throws ExecutionException, InterruptedException {
        videoUploadRequestHandler = new VideoUploadRequestHandler(eventStreamRPCConnection, greengrassCoreIPCClient);
    }

    @Test
    public void subscribeToMqttTopic_receivesOnDemandEvent_onStart() throws ExecutionException, InterruptedException {
        MQTTMessage mqttMessage = new MQTTMessage();
        mqttMessage.setTopicName(MQTT_TOPIC_NAME);
        mqttMessage.setPayload(MQTT_PAYLOAD_ON_DEMAND.getBytes(StandardCharsets.US_ASCII));
        IoTCoreMessage ioTCoreMessage = new IoTCoreMessage();
        ioTCoreMessage.setMessage(mqttMessage);

        SubscribeToIoTCoreResponse response = new SubscribeToIoTCoreResponse();
        when(responseCompletableFuture.get()).thenReturn(response);
        when(subscribeToIoTCoreResponseHandler.getResponse()).thenReturn(responseCompletableFuture);
        doAnswer(invocationOnMock -> {
            Optional<StreamResponseHandler<IoTCoreMessage>> streamResponseHandler = invocationOnMock.getArgument(1);
            streamResponseHandler.get().onStreamEvent(ioTCoreMessage);
            return subscribeToIoTCoreResponseHandler;
        }).when(greengrassCoreIPCClient)
                .subscribeToIoTCore(any(), any());
        videoUploadRequestHandler.subscribeToMqttTopic(MQTT_TOPIC_NAME,
                videoUploadRequestEvent,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
        verify(videoUploadRequestEvent).onStart(IS_LIVE_FALSE,
                EVENT_TIMESTAMP,
                START_TIME,
                END_TIME,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
    }

    @Test
    public void subscribeToMqttTopic_receivesOnLiveEvent_onStart() throws ExecutionException, InterruptedException {
        MQTTMessage mqttMessage = new MQTTMessage();
        mqttMessage.setTopicName(MQTT_TOPIC_NAME);
        mqttMessage.setPayload(MQTT_PAYLOAD_LIVE.getBytes(StandardCharsets.US_ASCII));
        IoTCoreMessage ioTCoreMessage = new IoTCoreMessage();
        ioTCoreMessage.setMessage(mqttMessage);

        SubscribeToIoTCoreResponse response = new SubscribeToIoTCoreResponse();
        when(responseCompletableFuture.get()).thenReturn(response);
        when(subscribeToIoTCoreResponseHandler.getResponse()).thenReturn(responseCompletableFuture);
        doAnswer(invocationOnMock -> {
            Optional<StreamResponseHandler<IoTCoreMessage>> streamResponseHandler = invocationOnMock.getArgument(1);
            streamResponseHandler.get().onStreamEvent(ioTCoreMessage);
            return subscribeToIoTCoreResponseHandler;
        }).when(greengrassCoreIPCClient)
                .subscribeToIoTCore(any(), any());
        videoUploadRequestHandler.subscribeToMqttTopic(MQTT_TOPIC_NAME,
                videoUploadRequestEvent,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
        verify(videoUploadRequestEvent).onStart(IS_LIVE_TRUE,
                EVENT_TIMESTAMP,
                0,
                0,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
    }

    @Test
    public void subscribeToMqttTopic_receivesEmptyMessage_onStartNotCalled() throws ExecutionException,
            InterruptedException {
        IoTCoreMessage emptyIotCoreMessage = new IoTCoreMessage();

        SubscribeToIoTCoreResponse response = new SubscribeToIoTCoreResponse();
        when(responseCompletableFuture.get()).thenReturn(response);
        when(subscribeToIoTCoreResponseHandler.getResponse()).thenReturn(responseCompletableFuture);
        doAnswer(invocationOnMock -> {
            Optional<StreamResponseHandler<IoTCoreMessage>> streamResponseHandler = invocationOnMock.getArgument(1);
            streamResponseHandler.get().onStreamEvent(emptyIotCoreMessage);
            return subscribeToIoTCoreResponseHandler;
        }).when(greengrassCoreIPCClient)
                .subscribeToIoTCore(any(), any());
        videoUploadRequestHandler.subscribeToMqttTopic(MQTT_TOPIC_NAME,
                videoUploadRequestEvent,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
        verify(videoUploadRequestEvent, never()).onStart(IS_LIVE_TRUE,
                EVENT_TIMESTAMP,
                START_TIME,
                END_TIME,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
    }

    @Test
    public void subscribeToMqttTopic_receivesInvalidMessageWithoutHyphen_onStartNotCalled() throws ExecutionException,
            InterruptedException {
        MQTTMessage mqttMessage = new MQTTMessage();
        mqttMessage.setTopicName(MQTT_TOPIC_NAME);
        mqttMessage.setPayload(MQTT_PAYLOAD_INVALID_VALUE_WITHOUT_HYPHEN.getBytes(StandardCharsets.US_ASCII));
        IoTCoreMessage invalidIoTCoreMessage = new IoTCoreMessage();
        invalidIoTCoreMessage.setMessage(mqttMessage);

        SubscribeToIoTCoreResponse response = new SubscribeToIoTCoreResponse();
        when(responseCompletableFuture.get()).thenReturn(response);
        when(subscribeToIoTCoreResponseHandler.getResponse()).thenReturn(responseCompletableFuture);
        doAnswer(invocationOnMock -> {
            Optional<StreamResponseHandler<IoTCoreMessage>> streamResponseHandler = invocationOnMock.getArgument(1);
            streamResponseHandler.get().onStreamEvent(invalidIoTCoreMessage);
            return subscribeToIoTCoreResponseHandler;
        }).when(greengrassCoreIPCClient)
                .subscribeToIoTCore(any(), any());
        videoUploadRequestHandler.subscribeToMqttTopic(MQTT_TOPIC_NAME,
                videoUploadRequestEvent,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
        verify(videoUploadRequestEvent, never()).onStart(IS_LIVE_TRUE,
                EVENT_TIMESTAMP,
                START_TIME,
                END_TIME,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
    }

    @Test
    public void subscribeToMqttTopic_receivesInvalidMessageWithHyphen_onStartNotCalled() throws ExecutionException,
            InterruptedException {
        MQTTMessage mqttMessage = new MQTTMessage();
        mqttMessage.setTopicName(MQTT_TOPIC_NAME);
        mqttMessage.setPayload(MQTT_PAYLOAD_INVALID_VALUE_WITH_HYPHEN.getBytes(StandardCharsets.US_ASCII));
        IoTCoreMessage invalidIoTCoreMessage = new IoTCoreMessage();
        invalidIoTCoreMessage.setMessage(mqttMessage);

        SubscribeToIoTCoreResponse response = new SubscribeToIoTCoreResponse();
        when(responseCompletableFuture.get()).thenReturn(response);
        when(subscribeToIoTCoreResponseHandler.getResponse()).thenReturn(responseCompletableFuture);
        doAnswer(invocationOnMock -> {
            Optional<StreamResponseHandler<IoTCoreMessage>> streamResponseHandler = invocationOnMock.getArgument(1);
            streamResponseHandler.get().onStreamEvent(invalidIoTCoreMessage);
            return subscribeToIoTCoreResponseHandler;
        }).when(greengrassCoreIPCClient)
                .subscribeToIoTCore(any(), any());
        videoUploadRequestHandler.subscribeToMqttTopic(MQTT_TOPIC_NAME,
                videoUploadRequestEvent,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
        verify(videoUploadRequestEvent, never()).onStart(IS_LIVE_TRUE,
                EVENT_TIMESTAMP,
                START_TIME,
                END_TIME,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
    }

    @Test
    public void subscribeToMqttTopic_receivesMultipleValuesMessage_onStartNotCalled() throws ExecutionException,
            InterruptedException {
        MQTTMessage mqttMessage = new MQTTMessage();
        mqttMessage.setTopicName(MQTT_TOPIC_NAME);
        mqttMessage.setPayload(MQTT_PAYLOAD_MULTIPLE_VALUES.getBytes(StandardCharsets.US_ASCII));
        IoTCoreMessage multipleValuesIoTCoreMessage = new IoTCoreMessage();
        multipleValuesIoTCoreMessage.setMessage(mqttMessage);

        SubscribeToIoTCoreResponse response = new SubscribeToIoTCoreResponse();
        when(responseCompletableFuture.get()).thenReturn(response);
        when(subscribeToIoTCoreResponseHandler.getResponse()).thenReturn(responseCompletableFuture);
        doAnswer(invocationOnMock -> {
            Optional<StreamResponseHandler<IoTCoreMessage>> streamResponseHandler = invocationOnMock.getArgument(1);
            streamResponseHandler.get().onStreamEvent(multipleValuesIoTCoreMessage);
            return subscribeToIoTCoreResponseHandler;
        }).when(greengrassCoreIPCClient)
                .subscribeToIoTCore(any(), any());
        videoUploadRequestHandler.subscribeToMqttTopic(MQTT_TOPIC_NAME,
                videoUploadRequestEvent,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
        verify(videoUploadRequestEvent, never()).onStart(IS_LIVE_TRUE,
                EVENT_TIMESTAMP,
                START_TIME,
                END_TIME,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
    }

    @Test
    public void subscribeToMqttTopic_receivesEvent_onError() throws ExecutionException, InterruptedException {
        SubscribeToIoTCoreResponse response = new SubscribeToIoTCoreResponse();
        when(responseCompletableFuture.get()).thenReturn(response);
        when(subscribeToIoTCoreResponseHandler.getResponse()).thenReturn(responseCompletableFuture);
        doAnswer(invocationOnMock -> {
            Optional<StreamResponseHandler<IoTCoreMessage>> streamResponseHandler = invocationOnMock.getArgument(1);
            streamResponseHandler.get().onStreamError(new Exception(ERR_MSG));
            return subscribeToIoTCoreResponseHandler;
        }).when(greengrassCoreIPCClient)
                .subscribeToIoTCore(any(), any());
        videoUploadRequestHandler.subscribeToMqttTopic(MQTT_TOPIC_NAME,
                videoUploadRequestEvent,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
        verify(videoUploadRequestEvent, never()).onStart(IS_LIVE_TRUE,
                EVENT_TIMESTAMP,
                START_TIME,
                END_TIME,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
        verify(videoUploadRequestEvent).onError(ERR_MSG, configuration);
    }

    @Test
    public void subscribeToMqttTopic_noEvent_onClose() throws ExecutionException, InterruptedException {
        SubscribeToIoTCoreResponse response = new SubscribeToIoTCoreResponse();
        when(responseCompletableFuture.get()).thenReturn(response);
        when(subscribeToIoTCoreResponseHandler.getResponse()).thenReturn(responseCompletableFuture);
        doAnswer(invocationOnMock -> {
            Optional<StreamResponseHandler<IoTCoreMessage>> streamResponseHandler = invocationOnMock.getArgument(1);
            streamResponseHandler.get().onStreamClosed();
            return subscribeToIoTCoreResponseHandler;
        }).when(greengrassCoreIPCClient)
                .subscribeToIoTCore(any(), any());

        videoUploadRequestHandler.subscribeToMqttTopic(MQTT_TOPIC_NAME,
                videoUploadRequestEvent,
                configuration,
                recorderService,
                liveStreamingExecutor,
                stopLiveStreamingExecutor);
    }

    @Test
    public void subscribeToMqttTopic_getResponseThrowsExecutionException_throwsException()
            throws ExecutionException, InterruptedException {
        doThrow(ExecutionException.class).when(responseCompletableFuture).get();
        when(subscribeToIoTCoreResponseHandler.getResponse()).thenReturn(responseCompletableFuture);
        when(greengrassCoreIPCClient.subscribeToIoTCore(any(), any())).thenReturn(subscribeToIoTCoreResponseHandler);

        assertThrows(EdgeConnectorForKVSException.class, () -> videoUploadRequestHandler
                .subscribeToMqttTopic(MQTT_TOPIC_NAME,
                        videoUploadRequestEvent,
                        configuration,
                        recorderService,
                        liveStreamingExecutor,
                        stopLiveStreamingExecutor));
    }

    @Test
    public void subscribeToMqttTopic_getResponseThrowsInterruptedException_throwsException()
            throws ExecutionException, InterruptedException {
        doThrow(InterruptedException.class).when(responseCompletableFuture).get();
        when(subscribeToIoTCoreResponseHandler.getResponse()).thenReturn(responseCompletableFuture);
        when(greengrassCoreIPCClient.subscribeToIoTCore(any(), any())).thenReturn(subscribeToIoTCoreResponseHandler);

        assertThrows(EdgeConnectorForKVSException.class, () -> videoUploadRequestHandler
                .subscribeToMqttTopic(MQTT_TOPIC_NAME,
                        videoUploadRequestEvent,
                        configuration,
                        recorderService,
                        liveStreamingExecutor,
                        stopLiveStreamingExecutor));
    }

    @Test
    public void createInstance_goodRPCConnection_succeeds() {
        try (MockedStatic<IPCUtils> util = Mockito.mockStatic(IPCUtils.class)) {
            util.when(IPCUtils::getEventStreamRpcConnection).thenReturn(eventStreamRPCConnection);
            videoUploadRequestHandler = new VideoUploadRequestHandler();
        }
    }

    @Test
    public void createInstance_badRPCConnectionThrowsExecutionException_throwsException() {
        try (MockedStatic<IPCUtils> util = Mockito.mockStatic(IPCUtils.class)) {
            util.when(IPCUtils::getEventStreamRpcConnection).thenThrow(ExecutionException.class);
            assertThrows(EdgeConnectorForKVSException.class, () -> {
                new VideoUploadRequestHandler();
            });
        }
    }

    @Test
    public void createInstance_badRPCConnectionThrowsInterruptedException_throwsException() {
        try (MockedStatic<IPCUtils> util = Mockito.mockStatic(IPCUtils.class)) {
            util.when(IPCUtils::getEventStreamRpcConnection).thenThrow(InterruptedException.class);
            assertThrows(EdgeConnectorForKVSException.class, () -> {
                new VideoUploadRequestHandler();
            });
        }
    }
}

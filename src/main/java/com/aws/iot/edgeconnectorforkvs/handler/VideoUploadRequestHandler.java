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
import com.aws.iot.edgeconnectorforkvs.model.VideoUploadRequestMessage;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import com.aws.iot.edgeconnectorforkvs.util.IPCUtils;
import com.aws.iot.edgeconnectorforkvs.util.JSONUtils;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.aws.greengrass.GreengrassCoreIPCClient;
import software.amazon.awssdk.aws.greengrass.SubscribeToIoTCoreResponseHandler;
import software.amazon.awssdk.aws.greengrass.model.IoTCoreMessage;
import software.amazon.awssdk.aws.greengrass.model.MQTTMessage;
import software.amazon.awssdk.aws.greengrass.model.QOS;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToIoTCoreRequest;
import software.amazon.awssdk.aws.greengrass.model.SubscribeToIoTCoreResponse;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.eventstreamrpc.StreamResponseHandler;
import software.amazon.awssdk.services.iotsitewise.model.AssetPropertyValue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static com.aws.iot.edgeconnectorforkvs.util.Constants.MQTT_LIVE_VIDEO_UPLOAD_REQUEST_KEY;

/**
 * VideoUploadRequestHandler Class.
 */
@Slf4j
public class VideoUploadRequestHandler {
    private EventStreamRPCConnection eventStreamRPCConnection;
    private GreengrassCoreIPCClient greengrassCoreIPCClient;

    /**
     * No Args Constructor.
     */
    public VideoUploadRequestHandler() {
        try {
            this.eventStreamRPCConnection = IPCUtils.getEventStreamRpcConnection();
            this.greengrassCoreIPCClient = new GreengrassCoreIPCClient(this.eventStreamRPCConnection);
        } catch (ExecutionException ex) {
            final String errorMessage = String.format("Could not create VideoUploadRequestHandler Instance. %s",
                    ex.getMessage());
            log.error(errorMessage);
            throw new EdgeConnectorForKVSException(errorMessage, ex);
        } catch (InterruptedException ex) {
            final String errorMessage = String.format("Could not create VideoUploadRequestHandler Instance. %s",
                    ex.getMessage());
            log.error(errorMessage);
            // restore interrupted state
            Thread.currentThread().interrupt();
            throw new EdgeConnectorForKVSException(errorMessage, ex);
        }
    }

    /**
     * Constructor used for Unit Testing.
     *
     * @param eventStreamRPCConnection - RPC Connection Instance
     * @param greengrassCoreIPCClient  - Greengrass Core IPC Client Instance
     */
    public VideoUploadRequestHandler(EventStreamRPCConnection eventStreamRPCConnection,
                                     GreengrassCoreIPCClient greengrassCoreIPCClient) {
        this.eventStreamRPCConnection = eventStreamRPCConnection;
        this.greengrassCoreIPCClient = greengrassCoreIPCClient;
    }

    /**
     * Subscribes to Specified MQTT Topic for Video Upload Request Events.
     *
     * @param mqttTopic - Name of the MQTT topic where video upload request will be sent
     * @param event     - Callback event for onStart or onError
     */
    public void subscribeToMqttTopic(String mqttTopic,
                                     VideoUploadRequestEvent event,
                                     EdgeConnectorForKVSConfiguration configuration,
                                     ExecutorService recorderService,
                                     ExecutorService liveStreamingExecutor,
                                     ScheduledExecutorService stopLiveStreamingExecutor) {
        StreamResponseHandler<IoTCoreMessage> streamResponseHandler;
        streamResponseHandler = new StreamResponseHandler<IoTCoreMessage>() {
            @Override
            public void onStreamEvent(IoTCoreMessage ioTCoreMessage) {
                log.info("onStreamEvent");
                MQTTMessage mqttMessage = ioTCoreMessage.getMessage();
                if (mqttMessage == null) {
                    log.error("Empty MQTT Message Received");
                    return;
                }
                String payload = new String(mqttMessage.getPayload(), StandardCharsets.UTF_8);
                VideoUploadRequestMessage videoUploadRequestMessage = JSONUtils
                        .jsonToVideoUplaodRequestMessage(payload);

                List<AssetPropertyValue> propertyValueEntry = videoUploadRequestMessage.getPayload().getValues();
                if (propertyValueEntry.size() == 1) {
                    AssetPropertyValue assetPropertyValue = propertyValueEntry.get(0);
                    long propertyUpdateTimestamp = assetPropertyValue.timestamp().timeInSeconds();
                    String value = assetPropertyValue.value().stringValue();
                    long startTimestamp = 0;
                    long endTimestamp = 0;
                    boolean isLive = false;
                    if (value.equalsIgnoreCase(MQTT_LIVE_VIDEO_UPLOAD_REQUEST_KEY)) {
                        // Live Uploading Request
                        isLive = true;
                        log.info("Live Streaming Request Received");
                    } else {
                        // On-Demand Video Uploading Request
                        String[] timestamps = value.split("-");
                        if (timestamps.length == 2) {
                            try {
                                startTimestamp = Long.parseLong(timestamps[0]);
                                endTimestamp = Long.parseLong(timestamps[1]);
                                log.info("On-Demand Streaming Request Received for Time Range "
                                        + timestamps[0] + "-" + timestamps[1]);
                            } catch (NumberFormatException ex) {
                                log.error("Invalid VideoUploadRequest Event Received. " + ex.getMessage());
                            }
                        } else {
                            log.error("Invalid VideoUploadRequest Event Received. Single hyphen required");
                        }
                    }
                    event.onStart(isLive, propertyUpdateTimestamp, startTimestamp, endTimestamp,
                            configuration, recorderService, liveStreamingExecutor, stopLiveStreamingExecutor);
                } else {
                    log.error("Invalid VideoUploadRequest Event Received. Single value required");
                }
            }

            @Override
            public boolean onStreamError(Throwable throwable) {
                log.info("onStream Error: " + throwable.getMessage());
                event.onError(throwable.getMessage(), configuration);
                // Handle error.
                return false;
            }

            @Override
            public void onStreamClosed() {
                log.info("onStream Closed Called");
            }
        };
        try {
            SubscribeToIoTCoreRequest subscribeToIoTCoreRequest = new SubscribeToIoTCoreRequest();
            subscribeToIoTCoreRequest.setTopicName(mqttTopic);
            subscribeToIoTCoreRequest.setQos(QOS.AT_MOST_ONCE);
            SubscribeToIoTCoreResponseHandler operationResponseHandler = greengrassCoreIPCClient
                    .subscribeToIoTCore(subscribeToIoTCoreRequest, Optional.of(streamResponseHandler));
            SubscribeToIoTCoreResponse resp = operationResponseHandler.getResponse().get();
            log.info("Subscribe to MQTT Response: " + resp.toString());
        } catch (ExecutionException ex) {
            final String errorMessage = String.format("Could not Subscribe to MQTT topic %s. %s", mqttTopic,
                    ex.getMessage());
            log.error(errorMessage);
            throw new EdgeConnectorForKVSException(errorMessage, ex);
        } catch (InterruptedException ex) {
            final String errorMessage = String.format("Could not Subscribe to MQTT topic %s. %s", mqttTopic,
                    ex.getMessage());
            log.error(errorMessage);
            // restore interrupted state
            Thread.currentThread().interrupt();
            throw new EdgeConnectorForKVSException(errorMessage, ex);
        }
    }

}

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

package com.aws.iot.edgeconnectorforkvs.dataaccessor;

import static com.aws.iot.edgeconnectorforkvs.util.Constants.STREAM_MANAGER_MAX_STREAM_SIZE;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.STREAM_MANAGER_SITEWISE_BATCH_SIZE;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.STREAM_MANAGER_STREAM_SEGMENT_SIZE;

import com.amazonaws.greengrass.streammanager.client.StreamManagerClient;
import com.amazonaws.greengrass.streammanager.client.StreamManagerClientFactory;
import com.amazonaws.greengrass.streammanager.client.exception.StreamManagerException;
import com.amazonaws.greengrass.streammanager.client.utils.ValidateAndSerialize;
import com.amazonaws.greengrass.streammanager.model.MessageStreamDefinition;
import com.amazonaws.greengrass.streammanager.model.Persistence;
import com.amazonaws.greengrass.streammanager.model.StrategyOnFull;
import com.amazonaws.greengrass.streammanager.model.export.ExportDefinition;
import com.amazonaws.greengrass.streammanager.model.export.IoTSiteWiseConfig;
import com.amazonaws.greengrass.streammanager.model.sitewise.AssetPropertyValue;
import com.amazonaws.greengrass.streammanager.model.sitewise.PutAssetPropertyValueEntry;
import com.amazonaws.greengrass.streammanager.model.sitewise.Quality;
import com.amazonaws.greengrass.streammanager.model.sitewise.TimeInNanos;
import com.amazonaws.greengrass.streammanager.model.sitewise.Variant;

import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Builder
@AllArgsConstructor
public class StreamManager {
    private StreamManagerClient streamManagerClient;
    private String msgStreamName;

    /**
     * Creates a message stream where data is written to.
     *
     * @param msgStreamName - Name of the stream manager message stream
     */
    public void createMessageStream(@NonNull String msgStreamName) {
        this.msgStreamName = msgStreamName;
        IoTSiteWiseConfig ioTSiteWiseConfig = new IoTSiteWiseConfig();
        ioTSiteWiseConfig.setIdentifier(UUID.randomUUID().toString());
        ioTSiteWiseConfig.setBatchSize(STREAM_MANAGER_SITEWISE_BATCH_SIZE);
        List<IoTSiteWiseConfig> ioTSiteWiseConfigs = new ArrayList<IoTSiteWiseConfig>();
        ioTSiteWiseConfigs.add(ioTSiteWiseConfig);
        try {
            if (streamManagerClient == null) {
                streamManagerClient = StreamManagerClientFactory.standard().build();
            }
            streamManagerClient.createMessageStream(
                    new MessageStreamDefinition()
                            .withName(msgStreamName) // Required.
                            .withMaxSize(STREAM_MANAGER_MAX_STREAM_SIZE) // Default is 256 MB.
                            .withStreamSegmentSize(STREAM_MANAGER_STREAM_SEGMENT_SIZE) // Default is 16 MB.
                            .withTimeToLiveMillis(null) // By default, no TTL is enabled.
                            .withStrategyOnFull(StrategyOnFull.OverwriteOldestData) // Required.
                            .withPersistence(Persistence.File) // Default is File.
                            .withFlushOnWrite(false) // Default is false.
                            .withExportDefinition(new ExportDefinition()
                                    .withIotSitewise(ioTSiteWiseConfigs))
            );
        } catch (Exception ex) {
            final String errorMsg = String.format("Error Creating Stream %s: %s", msgStreamName, ex.getMessage());
            log.error(errorMsg);
            throw new EdgeConnectorForKVSException(errorMsg, ex);
        }
    }

    /**
     * Pushes data to an existing stream.
     *
     * @param assetId         - SiteWise asset id
     * @param propertyId      - SiteWise property id
     * @param val             - value to be pushed (Long, String, Double or Boolean)
     * @param updateTimeStamp - update time stamp
     * @return - sequence number
     */
    public long pushData(@NonNull String assetId, @NonNull String propertyId, @NonNull Object val,
                         Optional<Date> updateTimeStamp) {
        try {
            if (streamManagerClient == null || msgStreamName == null) {
                final String errorMsg = String.format("Error pushing data to Stream. Property Id: %s." +
                        "Please create a message stream first.", propertyId);
                log.error(errorMsg);
                throw new EdgeConnectorForKVSException(errorMsg);
            }
            Variant variant = null;
            if (val instanceof Integer) {
                variant = new Variant().withIntegerValue(Long.valueOf((Integer) val));
            } else if (val instanceof String) {
                variant = new Variant().withStringValue((String) val);
            } else if (val instanceof Double) {
                variant = new Variant().withDoubleValue((Double) val);
            } else if (val instanceof Boolean) {
                variant = new Variant().withBooleanValue((Boolean) val);
            } else {
                final String errorMsg = String.format("Trying to push invalid val type",
                        msgStreamName, propertyId);
                log.error(errorMsg);
                throw new EdgeConnectorForKVSException(errorMsg);
            }
            List<AssetPropertyValue> entries = new ArrayList<>();
            long epochSecond = Instant.now().getEpochSecond();
            if (updateTimeStamp.isPresent()) {
                epochSecond = updateTimeStamp.get().toInstant().getEpochSecond();
            }
            TimeInNanos timestamp = new TimeInNanos().withTimeInSeconds(epochSecond);

            AssetPropertyValue entry = new AssetPropertyValue()
                    .withValue(variant)
                    .withQuality(Quality.GOOD)
                    .withTimestamp(timestamp);
            entries.add(entry);
            PutAssetPropertyValueEntry putAssetPropertyValueEntry = new PutAssetPropertyValueEntry()
                    .withEntryId(UUID.randomUUID().toString())
                    .withAssetId(assetId)
                    .withPropertyId(propertyId)
                    .withPropertyValues(entries);
            log.debug("Pushing data to Message Stream: " + msgStreamName);
            log.trace("For SiteWise Asset ID: " + assetId);
            log.trace("For SiteWise Property ID: " + propertyId);
            log.trace("Value: " + entries.get(0).toString());
            return streamManagerClient.appendMessage(msgStreamName,
                    ValidateAndSerialize.validateAndSerializeToJsonBytes(putAssetPropertyValueEntry));
        } catch (StreamManagerException | JsonProcessingException ex) {
            final String errorMsg = String.format("Error pushing data to Stream: %s, Property Id: %s",
                    msgStreamName, propertyId);
            log.error(errorMsg);
            return 0L;
        }
    }
}

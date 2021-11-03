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

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSUnrecoverableException;
import com.aws.iot.edgeconnectorforkvs.util.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.iotsitewise.model.AssetProperty;
import software.amazon.awssdk.services.iotsitewise.model.DescribeAssetResponse;
import software.amazon.awssdk.services.iotsitewise.model.GetAssetPropertyValueResponse;
import software.amazon.awssdk.services.iotsitewise.model.PropertyNotificationState;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Builder
@AllArgsConstructor
public class SiteWiseManager {

    private SiteWiseClient siteWiseClient;

    /***
     * Init EdgeConnectorForKVS Service configuration.
     * This method will call SiteWise service, query the asset property value to init the EdgeConnectorForKVS config.
     * @param hubDeviceSiteWiseAssetId The SiteWise asset Id for EdgeConnectorForKVS hub device.
     * @throws EdgeConnectorForKVSUnrecoverableException when there are issues querying SiteWise service
     * @return List of EdgeConnectorForKVSConfiguration
     */
    public List<EdgeConnectorForKVSConfiguration> initEdgeConnectorForKVSServiceConfiguration(
            String hubDeviceSiteWiseAssetId) {
        try {
            verifyConfiguration(hubDeviceSiteWiseAssetId);
            List<String> cameraSiteWiseAssetIdList = siteWiseClient.listAssociatedAssets(hubDeviceSiteWiseAssetId);
            if (cameraSiteWiseAssetIdList.size() > 0) {
                return cameraSiteWiseAssetIdList
                        .stream()
                        .map(cameraSiteWiseAssetId -> {
                            try {
                                return buildEdgeConnectorForKVSConfiguration(cameraSiteWiseAssetId);
                            } catch (Exception e) {
                                throw new EdgeConnectorForKVSException(e);
                            }
                        })
                        .collect(Collectors.toList());
            } else {
                final String warnMessage = String.format("Could not find any camera asset under the given hub assetId."
                        + " Please check the provided hub device SiteWise assetId: %s", hubDeviceSiteWiseAssetId);
                log.warn(warnMessage);
                return new ArrayList<>();
            }

        } catch (Exception e) {
            final String errorMessage = String.format("Failed to init EdgeConnectorForKVS component. " +
                    "Please check the provided hub device SiteWise assetId: %s", hubDeviceSiteWiseAssetId);
            log.error(errorMessage, e);
            throw new EdgeConnectorForKVSUnrecoverableException(errorMessage, e);
        }
    }

    private void verifyConfiguration(String hubDeviceSiteWiseAssetId) {
        if (!siteWiseClient.isAssetInheritedFromAssetModel(hubDeviceSiteWiseAssetId,
                Constants.HUB_DEVICE_SITE_WISE_ASSET_MODEL_PREFIX)) {
            throw new EdgeConnectorForKVSUnrecoverableException(String.format("Provided hub SiteWise Asset Id " +
                    "not inherited from EdgeConnectorForKVS provided model. " +
                    "SiteWiseAssetId: %s", hubDeviceSiteWiseAssetId));
        }
    }

    private EdgeConnectorForKVSConfiguration buildEdgeConnectorForKVSConfiguration(String cameraSiteWiseAssetId)
            throws IllegalAccessException {
        DescribeAssetResponse assetResponse = siteWiseClient.describeAsset(cameraSiteWiseAssetId);
        EdgeConnectorForKVSConfiguration edgeConnectorForKVSConfiguration = new EdgeConnectorForKVSConfiguration();
        edgeConnectorForKVSConfiguration.setSiteWiseAssetId(cameraSiteWiseAssetId);

        for (AssetProperty assetProperty : assetResponse.assetProperties()) {
            GetAssetPropertyValueResponse result;
            Field[] fields = edgeConnectorForKVSConfiguration.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.getName().equalsIgnoreCase(assetProperty.name())) {
                    result = siteWiseClient
                            .getAssetPropertyValue(cameraSiteWiseAssetId, assetProperty.id());
                    if (result != null && result.propertyValue() != null) {
                        if (field.getType() == String.class) {
                            field.set(edgeConnectorForKVSConfiguration, result.propertyValue().value().stringValue());
                            break;
                        } else if (field.getType() == Integer.TYPE || field.getType() == Integer.class) {
                            field.set(edgeConnectorForKVSConfiguration, result.propertyValue().value().integerValue());
                            break;
                        }
                    }
                }
            }

            if (assetProperty.name().equalsIgnoreCase(Constants.SITE_WISE_VIDEO_UPLOADED_TIME_RANGE_MEASUREMENT_NAME)) {
                edgeConnectorForKVSConfiguration.setVideoUploadedTimeRangePropertyId(assetProperty.id());
            }
            if (assetProperty.name().equalsIgnoreCase(Constants.SITE_WISE_VIDEO_RECORDED_TIME_RANGE_MEASUREMENT_NAME)) {
                edgeConnectorForKVSConfiguration.setVideoRecordedTimeRangePropertyId(assetProperty.id());
            }
            if (assetProperty.name().equalsIgnoreCase(Constants.
                    SITE_WISE_CACHED_VIDEO_AGE_OUT_ON_EDGE_MEASUREMENT_NAME)) {
                edgeConnectorForKVSConfiguration.setCachedVideoAgeOutOnEdgePropertyId(assetProperty.id());
            }
            if (assetProperty.name()
                    .equalsIgnoreCase(Constants.SITE_WISE_VIDEO_UPLOAD_REQUEST_MEASUREMENT_NAME)) {
                if (assetProperty.notification().state() == PropertyNotificationState.ENABLED) {
                    edgeConnectorForKVSConfiguration.setVideoUploadRequestMqttTopic(
                            assetProperty.notification().topic());
                } else {
                    log.warn("Video Upload Request MQTT Notification Disabled for Asset Id " + cameraSiteWiseAssetId);
                }
            }
        }
        return edgeConnectorForKVSConfiguration;
    }
}

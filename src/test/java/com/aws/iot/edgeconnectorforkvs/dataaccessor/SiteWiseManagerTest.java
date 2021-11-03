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

import static com.aws.iot.edgeconnectorforkvs.util.Constants.SITE_WISE_CACHED_VIDEO_AGE_OUT_ON_EDGE_MEASUREMENT_NAME;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.SITE_WISE_VIDEO_RECORDED_TIME_RANGE_MEASUREMENT_NAME;
import static com.aws.iot.edgeconnectorforkvs.util.Constants.SITE_WISE_VIDEO_UPLOADED_TIME_RANGE_MEASUREMENT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.aws.iot.edgeconnectorforkvs.model.EdgeConnectorForKVSConfiguration;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSUnrecoverableException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.iotsitewise.model.AssetProperty;
import software.amazon.awssdk.services.iotsitewise.model.AssetPropertyValue;
import software.amazon.awssdk.services.iotsitewise.model.DescribeAssetResponse;
import software.amazon.awssdk.services.iotsitewise.model.GetAssetPropertyValueResponse;
import software.amazon.awssdk.services.iotsitewise.model.Variant;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SiteWiseManagerTest {
    @Mock
    SiteWiseClient siteWiseClient;

    private SiteWiseManager siteWiseManager;

    private DescribeAssetResponse describeAssetResponse;

    public static final String MOCK_ID_SUFFIX = "Id";
    public static final String MOCK_VALUE_SUFFIX = "Value";

    public static final String SITE_WISE_CAMERA_MODEL_KINESIS_VIDEO_STREAM_NAME = "KinesisVideoStreamName";
    public static final String SITE_WISE_CAMERA_MODEL_RTSP_STREAM_URL = "RTSPStreamURL";
    public static final String SITE_WISE_CAMERA_MODEL_LIVE_STREAMING_START_TIME = "LiveStreamingStartTime";
    public static final String SITE_WISE_CAMERA_MODEL_LIVE_STREAMING_DURATION_IN_MINUTES =
            "LiveStreamingDurationInMinutes";
    public static final String SITE_WISE_CAMERA_MODEL_LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES =
            "LocalDataRetentionPeriodInMinutes";
    public static final String SITE_WISE_CAMERA_MODEL_CAPTURE_START_TIME = "CaptureStartTime";
    public static final String SITE_WISE_CAMERA_MODEL_CAPTURE_DURATION_IN_MINUTES =
            "CaptureDurationInMinutes";

    private AssetProperty assetProperty_KinesisVideoStreamName;
    private AssetProperty assetProperty_RTSPStreamURL;
    private AssetProperty assetProperty_LiveStreamingStartTime;
    private AssetProperty assetProperty_LiveStreamingDurationInMinutes;
    private AssetProperty assetProperty_LocalDataRetentionPeriodInMinutes;
    private AssetProperty assetProperty_CaptureStartTime;
    private AssetProperty assetProperty_CaptureDurationInMinutes;

    private AssetProperty assetProperty_VideoUploadedTimeRange;
    private AssetProperty assetProperty_VideoRecordedTimeRange;
    private AssetProperty assetProperty_CachedVideoAgeOutOnEdge;

    List<String> cameraSiteWiseAssetIdList = Arrays.asList("mockcameraSiteWiseAssetId");

    private static final String MOCK_HUB_DEVICE_SITE_WISE_ASSET_ID = "mockHubDeviceSiteWiseAssetId";

    private static final int liveStreamingDurationInMinutes = 10;
    private static final int localDataRetentionPeriodInMinutes = 10;
    private static final int captureDurationInMinutes = 10;

    @BeforeEach
    public void setUp() {
        this.siteWiseManager = SiteWiseManager.builder().siteWiseClient(siteWiseClient).build();

        assetProperty_KinesisVideoStreamName = AssetProperty.builder()
                .name(SITE_WISE_CAMERA_MODEL_KINESIS_VIDEO_STREAM_NAME)
                .id(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_KINESIS_VIDEO_STREAM_NAME))
                .build();

        assetProperty_RTSPStreamURL = AssetProperty.builder()
                .name(SITE_WISE_CAMERA_MODEL_RTSP_STREAM_URL)
                .id(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_RTSP_STREAM_URL))
                .build();

        assetProperty_LiveStreamingStartTime = AssetProperty.builder()
                .name(SITE_WISE_CAMERA_MODEL_LIVE_STREAMING_START_TIME)
                .id(generateSiteWisePropertyId((SITE_WISE_CAMERA_MODEL_LIVE_STREAMING_START_TIME)))
                .build();

        assetProperty_LiveStreamingDurationInMinutes = AssetProperty.builder()
                .name(SITE_WISE_CAMERA_MODEL_LIVE_STREAMING_DURATION_IN_MINUTES)
                .id(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_LIVE_STREAMING_DURATION_IN_MINUTES))
                .build();

        assetProperty_LocalDataRetentionPeriodInMinutes = AssetProperty.builder()
                .name(SITE_WISE_CAMERA_MODEL_LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES)
                .id(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES))
                .build();

        assetProperty_CaptureStartTime = AssetProperty.builder()
                .name(SITE_WISE_CAMERA_MODEL_CAPTURE_START_TIME)
                .id(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_CAPTURE_START_TIME))
                .build();

        assetProperty_CaptureDurationInMinutes = AssetProperty.builder()
                .name(SITE_WISE_CAMERA_MODEL_CAPTURE_DURATION_IN_MINUTES)
                .id(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_CAPTURE_DURATION_IN_MINUTES))
                .build();

        assetProperty_VideoUploadedTimeRange = AssetProperty.builder()
                .name(SITE_WISE_VIDEO_UPLOADED_TIME_RANGE_MEASUREMENT_NAME)
                .id(generateSiteWisePropertyId(SITE_WISE_VIDEO_UPLOADED_TIME_RANGE_MEASUREMENT_NAME))
                .build();

        assetProperty_VideoRecordedTimeRange = AssetProperty.builder()
                .name(SITE_WISE_VIDEO_RECORDED_TIME_RANGE_MEASUREMENT_NAME)
                .id(generateSiteWisePropertyId(SITE_WISE_VIDEO_RECORDED_TIME_RANGE_MEASUREMENT_NAME))
                .build();

        assetProperty_CachedVideoAgeOutOnEdge = AssetProperty.builder()
                .name(SITE_WISE_CACHED_VIDEO_AGE_OUT_ON_EDGE_MEASUREMENT_NAME)
                .id(generateSiteWisePropertyId(SITE_WISE_CACHED_VIDEO_AGE_OUT_ON_EDGE_MEASUREMENT_NAME))
                .build();

        describeAssetResponse = DescribeAssetResponse.builder()
                .assetProperties(Arrays.asList(
                        assetProperty_KinesisVideoStreamName,
                        assetProperty_RTSPStreamURL,
                        assetProperty_LiveStreamingStartTime,
                        assetProperty_LiveStreamingDurationInMinutes,
                        assetProperty_LocalDataRetentionPeriodInMinutes,
                        assetProperty_CaptureStartTime,
                        assetProperty_CaptureDurationInMinutes,
                        assetProperty_VideoUploadedTimeRange,
                        assetProperty_VideoRecordedTimeRange,
                        assetProperty_CachedVideoAgeOutOnEdge
                ))
                .build();
    }

    @Test
    public void testInitEdgeConnectorForKVSServiceConfiguration() {
        //when
        when(siteWiseClient.isAssetInheritedFromAssetModel(anyString(), anyString())).thenReturn(true);

        when(siteWiseClient.describeAsset(anyString())).thenReturn(describeAssetResponse);

        when(siteWiseClient.listAssociatedAssets(anyString())).thenReturn(cameraSiteWiseAssetIdList);

        when(siteWiseClient.getAssetPropertyValue(anyString(),
                eq(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_KINESIS_VIDEO_STREAM_NAME))))
                .thenReturn(GetAssetPropertyValueResponse.builder()
                        .propertyValue(AssetPropertyValue.builder()
                                .value(Variant.builder()
                                        .stringValue(generateSiteWisePropertyValue(
                                                SITE_WISE_CAMERA_MODEL_KINESIS_VIDEO_STREAM_NAME))
                                        .build())
                                .build())
                        .build());

        when(siteWiseClient.getAssetPropertyValue(anyString(),
                eq(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_RTSP_STREAM_URL))))
                .thenReturn(GetAssetPropertyValueResponse.builder()
                        .propertyValue(AssetPropertyValue.builder()
                                .value(Variant.builder()
                                        .stringValue(generateSiteWisePropertyValue(
                                                SITE_WISE_CAMERA_MODEL_RTSP_STREAM_URL))
                                        .build())
                                .build())
                        .build());

        when(siteWiseClient.getAssetPropertyValue(anyString(),
                eq(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_LIVE_STREAMING_START_TIME))))
                .thenReturn(GetAssetPropertyValueResponse.builder()
                        .propertyValue(AssetPropertyValue.builder()
                                .value(Variant.builder()
                                        .stringValue(generateSiteWisePropertyValue(
                                                SITE_WISE_CAMERA_MODEL_LIVE_STREAMING_START_TIME))
                                        .build())
                                .build())
                        .build());

        when(siteWiseClient.getAssetPropertyValue(anyString(),
                eq(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_LIVE_STREAMING_DURATION_IN_MINUTES))))
                .thenReturn(GetAssetPropertyValueResponse.builder()
                        .propertyValue(AssetPropertyValue.builder()
                                .value(Variant.builder()
                                        .integerValue(liveStreamingDurationInMinutes)
                                        .build())
                                .build())
                        .build());

        when(siteWiseClient.getAssetPropertyValue(anyString(),
                eq(generateSiteWisePropertyId(
                        SITE_WISE_CAMERA_MODEL_LOCAL_DATA_RETENTION_PERIOD_IN_MINUTES))))
                .thenReturn(GetAssetPropertyValueResponse.builder()
                        .propertyValue(AssetPropertyValue.builder()
                                .value(Variant.builder()
                                        .integerValue(localDataRetentionPeriodInMinutes)
                                        .build())
                                .build())
                        .build());

        when(siteWiseClient.getAssetPropertyValue(anyString(),
                eq(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_CAPTURE_START_TIME))))
                .thenReturn(GetAssetPropertyValueResponse.builder()
                        .propertyValue(AssetPropertyValue.builder()
                                .value(Variant.builder()
                                        .stringValue(generateSiteWisePropertyValue(
                                                SITE_WISE_CAMERA_MODEL_CAPTURE_START_TIME))
                                        .build())
                                .build())
                        .build());

        when(siteWiseClient.getAssetPropertyValue(anyString(),
                eq(generateSiteWisePropertyId(SITE_WISE_CAMERA_MODEL_CAPTURE_DURATION_IN_MINUTES))))
                .thenReturn(GetAssetPropertyValueResponse.builder()
                        .propertyValue(AssetPropertyValue.builder()
                                .value(Variant.builder()
                                        .integerValue(captureDurationInMinutes)
                                        .build())
                                .build())
                        .build());

        //then
        List<EdgeConnectorForKVSConfiguration> result = siteWiseManager.
                initEdgeConnectorForKVSServiceConfiguration(MOCK_HUB_DEVICE_SITE_WISE_ASSET_ID);
        //verify
        EdgeConnectorForKVSConfiguration configuration = result.get(0);
        assertEquals(configuration.getKinesisVideoStreamName(),
                generateSiteWisePropertyValue(SITE_WISE_CAMERA_MODEL_KINESIS_VIDEO_STREAM_NAME));
        assertEquals(configuration.getRtspStreamURL(),
                generateSiteWisePropertyValue(SITE_WISE_CAMERA_MODEL_RTSP_STREAM_URL));
        assertEquals(configuration.getLiveStreamingStartTime(),
                generateSiteWisePropertyValue(SITE_WISE_CAMERA_MODEL_LIVE_STREAMING_START_TIME));
        assertEquals(configuration.getLiveStreamingDurationInMinutes(), liveStreamingDurationInMinutes);
        assertEquals(configuration.getLocalDataRetentionPeriodInMinutes(), localDataRetentionPeriodInMinutes);
        assertEquals(configuration.getCaptureStartTime(),
                generateSiteWisePropertyValue(SITE_WISE_CAMERA_MODEL_CAPTURE_START_TIME));
        assertEquals(configuration.getCaptureDurationInMinutes(), captureDurationInMinutes);
        assertEquals(configuration.getVideoUploadedTimeRangePropertyId(), generateSiteWisePropertyId(SITE_WISE_VIDEO_UPLOADED_TIME_RANGE_MEASUREMENT_NAME));
        assertEquals(configuration.getVideoRecordedTimeRangePropertyId(), generateSiteWisePropertyId(SITE_WISE_VIDEO_RECORDED_TIME_RANGE_MEASUREMENT_NAME));
        assertEquals(configuration.getCachedVideoAgeOutOnEdgePropertyId(), generateSiteWisePropertyId(SITE_WISE_CACHED_VIDEO_AGE_OUT_ON_EDGE_MEASUREMENT_NAME));
    }

    @Test
    public void testInitEdgeConnectorForKVSServiceConfiguration_Exception() {
        //when
        when(siteWiseClient.isAssetInheritedFromAssetModel(
                anyString(), anyString())).thenThrow(SdkClientException.builder().build());
        //then and verify
        assertThrows(EdgeConnectorForKVSUnrecoverableException.class, () ->
                siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(MOCK_HUB_DEVICE_SITE_WISE_ASSET_ID));
    }

    @Test
    public void testInitEdgeConnectorForKVSServiceConfiguration_describeAsset_Exception() {
        //when
        when(siteWiseClient.isAssetInheritedFromAssetModel(
                anyString(), anyString())).thenReturn(true);
        when(siteWiseClient.listAssociatedAssets(anyString())).thenReturn(
                Collections.singletonList(MOCK_HUB_DEVICE_SITE_WISE_ASSET_ID));

        when(siteWiseClient.describeAsset(
                anyString())).thenThrow(SdkClientException.builder().build());
        //then and verify
        assertThrows(EdgeConnectorForKVSUnrecoverableException.class, () ->
                siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(MOCK_HUB_DEVICE_SITE_WISE_ASSET_ID));
    }


    @Test
    public void testInitEdgeConnectorForKVSServiceConfiguration_False_Case() {
        //when
        when(siteWiseClient.isAssetInheritedFromAssetModel(
                anyString(), anyString())).thenReturn(false);
        //then and verify
        assertThrows(EdgeConnectorForKVSUnrecoverableException.class, () ->
                siteWiseManager.initEdgeConnectorForKVSServiceConfiguration(MOCK_HUB_DEVICE_SITE_WISE_ASSET_ID));
    }

    @Test
    public void testInitEdgeConnectorForKVSServiceConfiguration_Empty_AssetIdList_Case() {
        //when
        when(siteWiseClient.isAssetInheritedFromAssetModel(anyString(), anyString())).thenReturn(true);
        when(siteWiseClient.listAssociatedAssets(anyString())).thenReturn(Collections.emptyList());
        //then
        List<EdgeConnectorForKVSConfiguration> result = siteWiseManager.
                initEdgeConnectorForKVSServiceConfiguration(MOCK_HUB_DEVICE_SITE_WISE_ASSET_ID);
        //verify
        assertEquals(result.size(), 0);
    }



    private static String generateSiteWisePropertyId(String configurationField) {
        return configurationField + MOCK_ID_SUFFIX;
    }

    private static String generateSiteWisePropertyValue(String configurationField) {
        return configurationField + MOCK_VALUE_SUFFIX;
    }
}
package com.aws.iot.edgeconnectorforkvs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.iotsitewise.model.AssetPropertyValue;
import software.amazon.awssdk.services.iotsitewise.model.Variant;

import java.util.ArrayList;
import java.util.List;

public class VideoUploadRequestPayloadTest {

    private static final String ASSET_ID = "mockAssetId";
    private static final String PROPERTY_ID = "mockPropertyId";
    private static final String SITE_WISE_PROPERTY_VALUE = "siteWisePropertyValue";
    private List<AssetPropertyValue> values = new ArrayList<>();

    Gson gson = new Gson();

    @Test
    public void testVideoUploadRequestPayload() {
        //when
        AssetPropertyValue assetPropertyValue = AssetPropertyValue.builder()
                .value(Variant.builder()
                        .stringValue(SITE_WISE_PROPERTY_VALUE)
                        .build())
                .build();
        values.add(assetPropertyValue);

        VideoUploadRequestPayload videoUploadRequestPayload = new VideoUploadRequestPayload();
        videoUploadRequestPayload.setAssetId(ASSET_ID);
        videoUploadRequestPayload.setPropertyId(PROPERTY_ID);
        videoUploadRequestPayload.setValues(values);

        //then
        String jsonString = gson.toJson(videoUploadRequestPayload);
        VideoUploadRequestPayload result = gson.fromJson(jsonString, VideoUploadRequestPayload.class);

        //verify
        assertEquals(result.getAssetId(), ASSET_ID);
        assertEquals(result.getPropertyId(), PROPERTY_ID);
        assertEquals(result.getValues().get(0).value().stringValue(), SITE_WISE_PROPERTY_VALUE);
    }
}

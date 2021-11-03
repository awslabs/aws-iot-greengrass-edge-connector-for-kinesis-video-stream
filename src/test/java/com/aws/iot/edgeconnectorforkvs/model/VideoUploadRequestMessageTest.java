package com.aws.iot.edgeconnectorforkvs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.iotsitewise.model.AssetPropertyValue;
import software.amazon.awssdk.services.iotsitewise.model.Variant;

import java.util.ArrayList;
import java.util.List;

public class VideoUploadRequestMessageTest {
    private static final String TYPE = "mockType";

    private static final String ASSET_ID = "mockAssetId";
    private static final String PROPERTY_ID = "mockPropertyId";
    private static final String SITE_WISE_PROPERTY_VALUE = "siteWisePropertyValue";
    private List<AssetPropertyValue> values = new ArrayList<>();

    Gson gson = new Gson();

    @Test
    public void testVideoUploadRequestMessage() {
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

        VideoUploadRequestMessage message = new VideoUploadRequestMessage();
        message.setType(TYPE);
        message.setPayload(videoUploadRequestPayload);

        //then
        String jsonString = gson.toJson(message);
        VideoUploadRequestMessage result = gson.fromJson(jsonString, VideoUploadRequestMessage.class);

        //verify
        assertEquals(result.getType(), TYPE);
        assertEquals(result.getPayload().getAssetId(), ASSET_ID);
        assertEquals(result.getPayload().getPropertyId(), PROPERTY_ID);
        assertEquals(result.getPayload().getValues().get(0).value().stringValue(), SITE_WISE_PROPERTY_VALUE);
    }
}

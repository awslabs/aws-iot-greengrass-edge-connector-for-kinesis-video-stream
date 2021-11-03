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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iotsitewise.IoTSiteWiseClient;
import software.amazon.awssdk.services.iotsitewise.model.AssetHierarchy;
import software.amazon.awssdk.services.iotsitewise.model.AssetPropertyValue;
import software.amazon.awssdk.services.iotsitewise.model.AssociatedAssetsSummary;
import software.amazon.awssdk.services.iotsitewise.model.DescribeAssetModelRequest;
import software.amazon.awssdk.services.iotsitewise.model.DescribeAssetModelResponse;
import software.amazon.awssdk.services.iotsitewise.model.DescribeAssetRequest;
import software.amazon.awssdk.services.iotsitewise.model.DescribeAssetResponse;
import software.amazon.awssdk.services.iotsitewise.model.GetAssetPropertyValueRequest;
import software.amazon.awssdk.services.iotsitewise.model.GetAssetPropertyValueResponse;
import software.amazon.awssdk.services.iotsitewise.model.ListAssociatedAssetsRequest;
import software.amazon.awssdk.services.iotsitewise.model.ListAssociatedAssetsResponse;
import software.amazon.awssdk.services.iotsitewise.model.Variant;

import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class SiteWiseClientTest {
    private static final String SITE_WISE_ASSET_ID = "siteWiseAssetId";
    private static final String SITE_WISE_ASSET_HIERARCHY_ID = "siteWiseAssetHierarchyId";
    private static final String SITE_WISE_PROPERTY_ID = "siteWisePropertyId";
    private static final String SITE_WISE_ASSET_MODEL_ID = "siteWiseAssetModelId";
    private static final String SITE_WISE_ASSET_MODEL_NAME = "siteWiseAssetModelName-onawertja";
    private static final String SITE_WISE_ASSET_MODEL_NAME_PREFIX = "siteWiseAssetModelName";
    private static final String SITE_WISE_ASSET_MODEL_NAME_PREFIX_WRONG = "asdattasd244ad4";
    private static final String SITE_WISE_PROPERTY_VALUE = "siteWisePropertyValue";
    private static final String SITE_WISE_ASSOCIATED_ASSETS_SUMMARY_ID = "siteWiseAssociatedAssetsSummaryId";

    private SiteWiseClient siteWiseClient;

    private DescribeAssetResponse describeAssetResponse;
    private DescribeAssetModelResponse describeAssetModelResponse;
    private GetAssetPropertyValueResponse getAssetPropertyValueResponse;
    private ListAssociatedAssetsResponse listAssociatedAssetsResponse;

    @Mock
    private IoTSiteWiseClient ioTSiteWiseClient;

    private AwsCredentialsProvider credentialProvider = DefaultCredentialsProvider.create();
    private Region region = Region.US_EAST_1;

    @BeforeEach
    public void setUp() {
        this.siteWiseClient = SiteWiseClient.builder().siteWiseClient(ioTSiteWiseClient).build();

        describeAssetResponse = DescribeAssetResponse.builder()
                .assetModelId(SITE_WISE_ASSET_MODEL_ID)
                .assetHierarchies(
                        AssetHierarchy.builder()
                                .id(SITE_WISE_ASSET_HIERARCHY_ID)
                                .build()
                )
                .build();

        describeAssetModelResponse = DescribeAssetModelResponse.builder()
                .assetModelName(SITE_WISE_ASSET_MODEL_NAME)
                .build();

        getAssetPropertyValueResponse = GetAssetPropertyValueResponse.builder()
                .propertyValue(AssetPropertyValue.builder()
                        .value(Variant.builder()
                                .stringValue(SITE_WISE_PROPERTY_VALUE)
                                .build())
                        .build())
                .build();

        listAssociatedAssetsResponse = ListAssociatedAssetsResponse.builder()
                .assetSummaries(
                        Collections.singletonList(
                                AssociatedAssetsSummary.builder()
                                        .id(SITE_WISE_ASSOCIATED_ASSETS_SUMMARY_ID)
                                        .build()
                        )
                ).build();
    }

    @Test
    public void testInit() {
        SiteWiseClient siteWiseClient = new SiteWiseClient(credentialProvider, region);
        assertNotNull(siteWiseClient);
    }

    /********DescribeAssetModel API tests***********/

    @Test
    public void testDescribeAssetModel() {
        //when
        when(ioTSiteWiseClient.describeAssetModel((DescribeAssetModelRequest) any())).thenReturn(describeAssetModelResponse);
        //then
        DescribeAssetModelResponse describeAssetModelResponse = siteWiseClient.describeAssetModel(SITE_WISE_ASSET_MODEL_ID);
        //verify
        assertEquals(describeAssetModelResponse.assetModelName(), SITE_WISE_ASSET_MODEL_NAME);
    }

    @Test
    public void testDescribeAssetModel_NullParameters() {
        //verify
        assertThrows(NullPointerException.class, () ->
                siteWiseClient.describeAssetModel(null));
    }

    @Test
    public void testDescribeAssetModel_SiteWiseThrowsException() {
        //when
        when(ioTSiteWiseClient.describeAssetModel((DescribeAssetModelRequest) any())).thenThrow(SdkClientException.builder().build());
        //then and verify
        assertThrows(EdgeConnectorForKVSException.class, () ->
                siteWiseClient.describeAssetModel(SITE_WISE_ASSET_ID));
    }

    /********DescribeAsset API tests***********/

    @Test
    public void testDescribeAsset() {
        //when
        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any())).thenReturn(describeAssetResponse);
        //then
        DescribeAssetResponse describeAssetResponse = siteWiseClient.describeAsset(SITE_WISE_ASSET_ID);
        //verify
        assertEquals(describeAssetResponse.assetModelId(), SITE_WISE_ASSET_MODEL_ID);

    }

    @Test
    public void testDescribeAsset_NullParameters() {
        //verify
        assertThrows(NullPointerException.class, () ->
                siteWiseClient.describeAsset(null));
    }

    @Test
    public void testDescribeAsset_SiteWiseThrowsException() {
        //when
        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any())).thenThrow(SdkClientException.builder().build());
        //then and verify
        assertThrows(EdgeConnectorForKVSException.class, () ->
                siteWiseClient.describeAsset(SITE_WISE_ASSET_ID));
    }

    /********GetAssetHierarchiesIdList API tests***********/

    @Test
    public void testGetAssetHierarchiesIdList() {
        //when
        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any())).thenReturn(describeAssetResponse);
        //then
        List<String> result = siteWiseClient.getAssetHierarchiesIdList(SITE_WISE_ASSET_ID);
        //verify
        assertEquals(result.size(), 1);
        assertEquals(describeAssetResponse.assetHierarchies().get(0).id(), SITE_WISE_ASSET_HIERARCHY_ID);
    }

    @Test
    public void testGetAssetHierarchiesIdList_Empty_Case() {
        //when
        describeAssetResponse = DescribeAssetResponse.builder()
                .assetModelId(SITE_WISE_ASSET_MODEL_ID)
                .build();
        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any())).thenReturn(describeAssetResponse);
        //then and verify
        assertThrows(EdgeConnectorForKVSException.class, () ->
                siteWiseClient.getAssetHierarchiesIdList(SITE_WISE_ASSET_ID));
    }

    @Test
    public void testGetAssetHierarchiesIdList_NullParameters() {
        //verify
        assertThrows(NullPointerException.class, () ->
                siteWiseClient.getAssetHierarchiesIdList(null));
    }

    @Test
    public void testGetAssetHierarchiesIdList_SiteWiseThrowsException() {
        //when
        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any()))
                .thenThrow(SdkClientException.builder().build());
        //then and verify
        assertThrows(EdgeConnectorForKVSException.class, () ->
                siteWiseClient.getAssetHierarchiesIdList(SITE_WISE_ASSET_ID));
    }

    /********ListAssociatedAssets API tests***********/

    @Test
    public void testListAssociatedAssets_Success_Case() {
        //when
        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any())).thenReturn(describeAssetResponse);
        when(ioTSiteWiseClient.listAssociatedAssets((ListAssociatedAssetsRequest) any()))
                .thenReturn(listAssociatedAssetsResponse);
        //then
        List<String> result = siteWiseClient.listAssociatedAssets(SITE_WISE_ASSET_ID);
        //verify
        assertEquals(result.size(), 1);
        assertEquals(result.get(0), SITE_WISE_ASSOCIATED_ASSETS_SUMMARY_ID);
    }


    @Test
    public void testListAssociatedAssets_NullParameters() {
        //verify
        assertThrows(NullPointerException.class, () ->
                siteWiseClient.listAssociatedAssets(null));
    }

    @Test
    public void testListAssociatedAssets_SiteWiseThrowsException() {
        //when
        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any()))
                .thenThrow(SdkClientException.builder().build());

        //then and verify
        assertThrows(EdgeConnectorForKVSException.class, () ->
                siteWiseClient.listAssociatedAssets(SITE_WISE_ASSET_ID));
    }

    /********IsAssetInheritedFromAssetModel API tests***********/

    @Test
    public void testIsAssetInheritedFromAssetModel() {
        //when
        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any())).thenReturn(describeAssetResponse);
        when(ioTSiteWiseClient.describeAssetModel((DescribeAssetModelRequest) any())).thenReturn(describeAssetModelResponse);

        //then
        boolean result = siteWiseClient.isAssetInheritedFromAssetModel(SITE_WISE_ASSET_ID, SITE_WISE_ASSET_MODEL_NAME_PREFIX);
        //verify
        assertTrue(result);

        //then
        result = siteWiseClient.isAssetInheritedFromAssetModel(SITE_WISE_ASSET_ID, SITE_WISE_ASSET_MODEL_NAME_PREFIX_WRONG);
        //verify
        assertFalse(result);

        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any())).thenReturn(describeAssetResponse);
        //then
        DescribeAssetResponse describeAssetResponse = siteWiseClient.describeAsset(SITE_WISE_ASSET_ID);
        //verify
        assertEquals(describeAssetResponse.assetModelId(), SITE_WISE_ASSET_MODEL_ID);
    }

    @Test
    public void testIsAssetInheritedFromAssetModel_Null_AssetModelName_Case() {
        //when
        describeAssetModelResponse = DescribeAssetModelResponse.builder().build();
        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any())).thenReturn(describeAssetResponse);
        when(ioTSiteWiseClient.describeAssetModel((DescribeAssetModelRequest) any())).thenReturn(describeAssetModelResponse);

        //then
        boolean result = siteWiseClient.isAssetInheritedFromAssetModel(SITE_WISE_ASSET_ID, SITE_WISE_ASSET_MODEL_NAME_PREFIX);
        //verify
        assertFalse(result);
    }

    @Test
    public void testIsAssetInheritedFromAssetModel_Null_AssetModelId_Case() {
        //when
        describeAssetResponse = DescribeAssetResponse.builder().build();
        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any())).thenReturn(describeAssetResponse);
        //then
        boolean result = siteWiseClient.isAssetInheritedFromAssetModel(SITE_WISE_ASSET_ID, SITE_WISE_ASSET_MODEL_NAME_PREFIX);
        //verify
        assertFalse(result);
    }

    @Test
    public void testIsAssetInheritedFromAssetModel_NullParameters() {
        //verify
        assertThrows(NullPointerException.class, () ->
                siteWiseClient.isAssetInheritedFromAssetModel(null, SITE_WISE_ASSET_MODEL_NAME_PREFIX));
        assertThrows(NullPointerException.class, () ->
                siteWiseClient.isAssetInheritedFromAssetModel(SITE_WISE_ASSET_ID, null));
    }

    @Test
    public void testIsAssetInheritedFromAssetModel_SiteWiseThrowsException() {
        //when
        when(ioTSiteWiseClient.describeAsset((DescribeAssetRequest) any())).thenThrow(SdkClientException.builder().build());
        //then and verify
        assertThrows(EdgeConnectorForKVSException.class, () ->
                siteWiseClient.isAssetInheritedFromAssetModel(SITE_WISE_ASSET_ID, SITE_WISE_ASSET_MODEL_NAME_PREFIX));
    }

    /********GetAssetHierarchiesIdList API tests***********/

    @Test
    public void testGetAssetPropertyValue() {
        //when
        when(ioTSiteWiseClient.getAssetPropertyValue((GetAssetPropertyValueRequest) any()))
                .thenReturn(getAssetPropertyValueResponse);
        //then
        GetAssetPropertyValueResponse response =
                siteWiseClient.getAssetPropertyValue(SITE_WISE_ASSET_ID, SITE_WISE_PROPERTY_ID);
        //verify
        assertEquals(response.propertyValue().value().stringValue(), SITE_WISE_PROPERTY_VALUE);
    }

    @Test
    public void testGetAssetPropertyValue_NullParameters() {
        //verify
        assertThrows(NullPointerException.class, () ->
                siteWiseClient.getAssetPropertyValue(null, SITE_WISE_PROPERTY_ID));
        assertThrows(NullPointerException.class, () ->
                siteWiseClient.getAssetPropertyValue(SITE_WISE_ASSET_ID, null));
    }

    @Test
    public void testGetAssetPropertyValue_SiteWiseThrowsException() {
        //when
        when(ioTSiteWiseClient.getAssetPropertyValue((GetAssetPropertyValueRequest) any()))
                .thenThrow(SdkClientException.builder().build());
        //then and verify
        assertThrows(EdgeConnectorForKVSException.class, () ->
                siteWiseClient.getAssetPropertyValue(SITE_WISE_ASSET_ID, SITE_WISE_PROPERTY_ID));
    }
}

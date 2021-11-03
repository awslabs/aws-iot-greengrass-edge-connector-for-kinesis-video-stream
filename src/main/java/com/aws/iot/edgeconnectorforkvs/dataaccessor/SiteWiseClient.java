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

import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iotsitewise.IoTSiteWiseClient;
import software.amazon.awssdk.services.iotsitewise.model.AssetHierarchy;
import software.amazon.awssdk.services.iotsitewise.model.AssociatedAssetsSummary;
import software.amazon.awssdk.services.iotsitewise.model.DescribeAssetModelRequest;
import software.amazon.awssdk.services.iotsitewise.model.DescribeAssetModelResponse;
import software.amazon.awssdk.services.iotsitewise.model.DescribeAssetRequest;
import software.amazon.awssdk.services.iotsitewise.model.DescribeAssetResponse;
import software.amazon.awssdk.services.iotsitewise.model.GetAssetPropertyValueRequest;
import software.amazon.awssdk.services.iotsitewise.model.GetAssetPropertyValueResponse;
import software.amazon.awssdk.services.iotsitewise.model.ListAssociatedAssetsRequest;
import software.amazon.awssdk.services.iotsitewise.model.ListAssociatedAssetsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

@Slf4j
@Builder
@AllArgsConstructor
public class SiteWiseClient {

    private IoTSiteWiseClient siteWiseClient;

    @Inject
    public SiteWiseClient(AwsCredentialsProvider credentialProvider, Region region) {
        this.siteWiseClient = IoTSiteWiseClient.builder()
                .credentialsProvider(credentialProvider)
                .region(region)
                .build();
    }

    /***
     * describeAssetModel wrapper.
     * @param siteWiseAssetModelId siteWiseAssetModelId
     * @throws EdgeConnectorForKVSException - EdgeConnectorForKVS generic exception
     * @return DescribeAssetModelResponse
     */
    public DescribeAssetModelResponse describeAssetModel(@NonNull String siteWiseAssetModelId)
            throws EdgeConnectorForKVSException {
        try {
            final DescribeAssetModelRequest describeAssetModelRequest = DescribeAssetModelRequest.builder()
                    .assetModelId(siteWiseAssetModelId)
                    .build();
            return siteWiseClient.describeAssetModel(describeAssetModelRequest);
        } catch (Exception e) {
            final String errorMessage = String.format("Could not describeAssetModel for siteWiseAssetModelId : %s !",
                    siteWiseAssetModelId);
            log.error(errorMessage);
            throw new EdgeConnectorForKVSException(errorMessage, e);
        }
    }

    /***
     * DescribeAsset wrapper.
     * @param siteWiseAssetId siteWiseAssetId
     * @return DescribeAssetResponse
     * @throws EdgeConnectorForKVSException - EdgeConnectorForKVS generic exception
     */
    public DescribeAssetResponse describeAsset(@NonNull String siteWiseAssetId)
            throws EdgeConnectorForKVSException {
        try {
            final DescribeAssetRequest describeAssetRequest = DescribeAssetRequest.builder()
                    .assetId(siteWiseAssetId)
                    .build();
            return siteWiseClient.describeAsset(describeAssetRequest);
        } catch (Exception e) {
            final String errorMessage = String.format("Failed to query SiteWise service describeAsset API. "
                    + " siteWiseAssetId : %s", siteWiseAssetId);
            log.error(errorMessage, e);
            throw new EdgeConnectorForKVSException(errorMessage, e);
        }
    }

    /***
     * Return all AssetHierarchies Id as String list
     * @param siteWiseAssetId siteWiseAssetId
     * @return List which contains asset hierarchiesId
     * @throws EdgeConnectorForKVSException - EdgeConnectorForKVS generic exception
     */
    public List<String> getAssetHierarchiesIdList(@NonNull String siteWiseAssetId)
            throws EdgeConnectorForKVSException {
        DescribeAssetResponse describeAssetResponse = describeAsset(siteWiseAssetId);
        if (describeAssetResponse.hasAssetHierarchies()) {
            return describeAssetResponse.assetHierarchies().stream()
                    .map(AssetHierarchy::id)
                    .collect(Collectors.toList());
        } else {
            final String errorMessage = String.format("Failed to getAssetHierarchiesIdList from given siteWiseAssetId, "
                    + "please check EdgeConnectorForKVS configuration and ensure given provide SiteWise property " +
                    "generated from EdgeConnectorForKVSHubDeviceModel. siteWiseAssetId : %s", siteWiseAssetId);
            log.error(errorMessage);
            throw new EdgeConnectorForKVSException(errorMessage);
        }
    }

    /***
     * Return all Associated asset Id as String list
     * @param siteWiseAssetId siteWiseAssetId
     * @return List which contains associated asset id
     * @throws EdgeConnectorForKVSException - EdgeConnectorForKVS generic exception
     */
    public List<String> listAssociatedAssets(@NonNull String siteWiseAssetId)
            throws EdgeConnectorForKVSException {
        try {
            List<String> assetHierarchiesIdList = getAssetHierarchiesIdList(siteWiseAssetId);
            List<String> result = new ArrayList<>();
            for (String hierarchiesId: assetHierarchiesIdList) {
                final ListAssociatedAssetsRequest listAssociatedAssetsRequest = ListAssociatedAssetsRequest.builder()
                        .assetId(siteWiseAssetId)
                        .hierarchyId(hierarchiesId)
                        .build();
                ListAssociatedAssetsResponse listAssociatedAssetsResponse =
                        siteWiseClient.listAssociatedAssets(listAssociatedAssetsRequest);
                result.addAll(listAssociatedAssetsResponse.assetSummaries().stream()
                        .map(AssociatedAssetsSummary::id)
                        .collect(Collectors.toList()));
            }
            return result;
        } catch (Exception e) {
            final String errorMessage = String.format("Failed to getAssetHierarchiesIdList from given siteWiseAssetId, "
                    + "please check EdgeConnectorForKVS configuration and ensure given provide SiteWise property " +
                    "generated from EdgeConnectorForKVSHubDeviceModel. siteWiseAssetId : %s", siteWiseAssetId);
            log.error(errorMessage);
            throw new EdgeConnectorForKVSException(errorMessage);
        }
    }

    /***
     * Check whether given siteWiseAssetId belongs to given siteWiseAssetModel. Return true if asset's model contains
     * given siteWiseAssetModelPrefix, otherwise return false.
     * @param siteWiseAssetId siteWiseAssetId
     * @param siteWiseAssetModelPrefix siteWiseAssetModelPrefix
     * @return true|false
     * @throws EdgeConnectorForKVSException - EdgeConnectorForKVS generic exception
     */
    public boolean isAssetInheritedFromAssetModel(@NonNull String siteWiseAssetId,
                                                  @NonNull String siteWiseAssetModelPrefix)
            throws EdgeConnectorForKVSException {

        final DescribeAssetResponse describeAssetResponse;
        final String assetModelId;
        try {
            describeAssetResponse = describeAsset(siteWiseAssetId);
            assetModelId = describeAssetResponse.assetModelId();

            if (assetModelId != null) {
                DescribeAssetModelResponse describeAssetModelResponse = describeAssetModel(assetModelId);
                if (describeAssetModelResponse.assetModelName() != null) {
                    return describeAssetModelResponse.assetModelName().startsWith(siteWiseAssetModelPrefix);
                } else {
                    log.error(String.format("Could not find the model name for given siteWise assetId and siteWise " +
                                    "assetModel Id. Return false. siteWiseAssetId : %s, siteWiseAssetModelId : %s",
                            siteWiseAssetId, assetModelId));
                    return false;
                }
            } else {
                final String errorMessage = String.format("Could not find the model ID for given siteWiseAssetId. " +
                        "Return false. siteWiseAssetId : %s", siteWiseAssetId);
                log.error(errorMessage);
                return false;
            }
        } catch (Exception e) {
            final String errorMessage = String.format("Failed to in isAssetBelongsAssetModel API call." +
                    " siteWiseAssetId: %s, siteWiseAssetModelPrefix : %s", siteWiseAssetId, siteWiseAssetModelPrefix);
            log.error(errorMessage, e);
            throw new EdgeConnectorForKVSException(errorMessage, e);
        }
    }

    /***
     * Get SiteWise asset property value
     * @param siteWiseAssetId siteWiseAssetId
     * @param siteWisePropertyId siteWisePropertyId
     * @return GetAssetPropertyValueResponse
     * @throws EdgeConnectorForKVSException - EdgeConnectorForKVS generic exception
     */
    public GetAssetPropertyValueResponse getAssetPropertyValue(@NonNull String siteWiseAssetId,
                                                               @NonNull String siteWisePropertyId)
            throws EdgeConnectorForKVSException {

        try {
            final GetAssetPropertyValueRequest getAssetPropertyValueRequest = GetAssetPropertyValueRequest.builder()
                    .assetId(siteWiseAssetId)
                    .propertyId(siteWisePropertyId)
                    .build();
            return siteWiseClient.getAssetPropertyValue(getAssetPropertyValueRequest);
        } catch (Exception e) {
            final String errorMessage = String.format("Failed to query SiteWise service getAssetPropertyValue API." +
                    " siteWiseAssetId: %s, siteWisePropertyId: %s", siteWiseAssetId, siteWisePropertyId);
            log.error(errorMessage, e);
            throw new EdgeConnectorForKVSException(errorMessage, e);
        }
    }
}

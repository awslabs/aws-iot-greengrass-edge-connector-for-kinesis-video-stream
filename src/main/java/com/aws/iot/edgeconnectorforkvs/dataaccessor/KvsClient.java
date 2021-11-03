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

import static com.aws.iot.edgeconnectorforkvs.util.Constants.DEFAULT_CREATED_KVS_DATA_RETENTION_TIME_IN_HOURS;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoAsyncClient;
import com.amazonaws.services.kinesisvideo.model.CreateStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamRequest;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.amazonaws.services.kinesisvideo.model.ResourceNotFoundException;
import com.amazonaws.services.kinesisvideo.model.StreamInfo;

import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@AllArgsConstructor
public class KvsClient {
    private AmazonKinesisVideo amazonKinesisVideoClient;

    public KvsClient(@NonNull AWSCredentialsProvider awsCredentialsProvider,
                     @NonNull Region region) {
        this.amazonKinesisVideoClient = AmazonKinesisVideoAsyncClient.builder()
                .withCredentials(awsCredentialsProvider)
                .withRegion(region.getName())
                .build();
    }

    /**
     * Given a KVS Stream Name, describe the stream details like stream ARN, data retention in hours, etc
     * If the given KVS Stream Name does not exist, then return Optional.empty()
     *
     * @param kvsStreamName KVS Stream Name to be described
     * @return StreamInfo
     */
    public Optional<StreamInfo> describeStream(@NonNull String kvsStreamName) throws EdgeConnectorForKVSException {

        final DescribeStreamRequest request = new DescribeStreamRequest()
                .withStreamName(kvsStreamName);
        try {
            final DescribeStreamResult result = amazonKinesisVideoClient.describeStream(request);
            return Optional.of(result.getStreamInfo());
        } catch (ResourceNotFoundException ex) {
            String errMessage = String.format("kvsStreamName : %s does not exist!", kvsStreamName);
            log.warn(errMessage, ex);
            return Optional.empty();
        } catch (Exception ex) {
            String errMessage = String.format("Failed to describe Stream." +
                    " kvsStreamName : %s", kvsStreamName);
            throw new EdgeConnectorForKVSException(errMessage, ex);
        }
    }

    /**
     * Create a KVS Stream based on the given Name
     *
     * @param kvsStreamName KVS Stream Name to be described
     */
    public void createStream(@NonNull String kvsStreamName) throws EdgeConnectorForKVSException {

        final CreateStreamRequest request = new CreateStreamRequest()
                .withStreamName(kvsStreamName)
                .withDataRetentionInHours(DEFAULT_CREATED_KVS_DATA_RETENTION_TIME_IN_HOURS);
        try {
            amazonKinesisVideoClient.createStream(request);
        } catch (Exception ex) {
            String errMessage = String.format("Failed to create Stream with name: " +
                    " kvsStreamName : %s", kvsStreamName);
            throw new EdgeConnectorForKVSException(errMessage, ex);
        }
    }
}

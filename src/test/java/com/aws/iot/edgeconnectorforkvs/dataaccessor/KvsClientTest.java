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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.model.CreateStreamRequest;
import com.amazonaws.services.kinesisvideo.model.CreateStreamResult;
import com.amazonaws.services.kinesisvideo.model.DescribeStreamResult;
import com.amazonaws.services.kinesisvideo.model.ResourceNotFoundException;
import com.amazonaws.services.kinesisvideo.model.StreamInfo;

import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class KvsClientTest {
    private static final String STREAM_NAME = "test-video-stream";

    private KvsClient kvsClient;

    @Mock
    private AmazonKinesisVideo amazonKinesisVideoClient;

    @Mock
    private AWSCredentialsProvider awsCredentialsProvider;

    private Region region = com.amazonaws.regions.Region.getRegion(Regions.fromName("us-east-1"));

    @Captor
    private ArgumentCaptor<CreateStreamRequest> createStreamRequestCaptor;

    @BeforeEach
    public void setUp() {
        kvsClient = new KvsClient(amazonKinesisVideoClient);
    }

    @Test
    public void testRegionOverride() {
        //when
        this.kvsClient = new KvsClient(awsCredentialsProvider, region);
        DescribeStreamResult describeStreamResult = new DescribeStreamResult().withStreamInfo(
                new StreamInfo().withStreamName(STREAM_NAME)
        );
        when(amazonKinesisVideoClient.describeStream(any())).thenReturn(describeStreamResult);
        //then and verify
        assertThrows(EdgeConnectorForKVSException.class, () -> {
            kvsClient.describeStream(STREAM_NAME);
        });
    }

    @Test
    public void describeStream_Success() {
        //when
        DescribeStreamResult describeStreamResult = new DescribeStreamResult().withStreamInfo(
                new StreamInfo().withStreamName(STREAM_NAME)
        );
        when(amazonKinesisVideoClient.describeStream(any())).thenReturn(describeStreamResult);
        //then
        Optional<StreamInfo> result = kvsClient.describeStream(STREAM_NAME);
        //verify
        assertEquals(result.get().getStreamName(), STREAM_NAME);
    }

    @Test
    public void describeStream_ResourceNotFound() {
        //when
        when(amazonKinesisVideoClient.describeStream(any())).thenThrow(new ResourceNotFoundException(""));
        //then
        Optional<StreamInfo> result = kvsClient.describeStream(STREAM_NAME);
        //verify
        assertFalse(result.isPresent());
    }

    @Test
    public void describeStream_Exception() {
        //when
        when(amazonKinesisVideoClient.describeStream(any())).thenThrow(SdkClientException.builder().build());
        //then and verify
        assertThrows(EdgeConnectorForKVSException.class, () ->
                kvsClient.describeStream(STREAM_NAME));
    }

    @Test
    public void createStream_Success() {
        //when
        CreateStreamResult result = new CreateStreamResult();
        when(amazonKinesisVideoClient.createStream(any())).thenReturn(result);
        //then
        kvsClient.createStream(STREAM_NAME);
        //verify
        verify(amazonKinesisVideoClient).createStream(createStreamRequestCaptor.capture());
        assertEquals(createStreamRequestCaptor.getValue().getStreamName(), STREAM_NAME);
    }

    @Test
    public void createStream_Exception() {
        //when
        when(amazonKinesisVideoClient.createStream(any())).thenThrow(SdkClientException.builder().build());
        //then and verify
        assertThrows(EdgeConnectorForKVSException.class, () ->
                kvsClient.createStream(STREAM_NAME));
    }
}

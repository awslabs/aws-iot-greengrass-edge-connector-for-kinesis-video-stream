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

import com.amazonaws.greengrass.streammanager.client.StreamManagerClient;
import com.amazonaws.greengrass.streammanager.client.exception.StreamManagerException;

import com.aws.iot.edgeconnectorforkvs.model.exceptions.EdgeConnectorForKVSException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class StreamManagerTest {
    private StreamManager streamManager;
    private static final String STREAM_NAME = "streamName";
    private static final String ASSET_ID = "eab9d387-4b49-49e1-be30-0574949e4f6a";
    private static final String PROPERTY_ID = "c09c71a5-9908-4e9c-b768-28f9d82a5952";
    private static final boolean BOOLEAN_VAL = true;

    private static final int INT_VAL = 15;
    private static final long LONG_VAL = 15;
    private static final String STRING_VAL = "testval";
    private static final double DOUBLE_VAL = 1.234;
    private static final long SEQUENCE_NUM = 12345;

    @Mock
    StreamManagerClient streamManagerClient;

    @Test
    public void createMessageStream_validInput_streamCreated() throws StreamManagerException {
        streamManager = StreamManager.builder()
                .streamManagerClient(streamManagerClient).build();
        doNothing().when(streamManagerClient).createMessageStream(any());
        streamManager.createMessageStream(STREAM_NAME);
    }

    @Test
    public void createMessageStream_throwsStreamManagerException_throwsException() throws StreamManagerException {
        streamManager = StreamManager.builder()
                .streamManagerClient(streamManagerClient).build();
        doThrow(StreamManagerException.class).when(streamManagerClient).createMessageStream(any());
        assertThrows(EdgeConnectorForKVSException.class, () -> streamManager.createMessageStream(STREAM_NAME));
    }

    @Test
    public void createMessageStream_withoutStreamManagerClient_throwsException() {
        streamManager = StreamManager.builder().build();
        assertThrows(EdgeConnectorForKVSException.class, () -> streamManager.createMessageStream(STREAM_NAME));
    }

    @Test
    public void putBooleanValue_validInput_returnsSequenceNumber()
            throws StreamManagerException {
        streamManager = StreamManager.builder()
                .streamManagerClient(streamManagerClient).build();
        when(streamManagerClient.appendMessage(any(), any())).thenReturn(SEQUENCE_NUM);
        streamManager.createMessageStream(STREAM_NAME);
        long seqNum = streamManager.pushData(ASSET_ID, PROPERTY_ID, BOOLEAN_VAL, Optional.empty());
        assertEquals(SEQUENCE_NUM, seqNum);
    }

    @Test
    public void pushData_booleanValue_returnsSequenceNumber()
            throws StreamManagerException {
        streamManager = StreamManager.builder()
                .streamManagerClient(streamManagerClient).build();
        when(streamManagerClient.appendMessage(any(), any())).thenReturn(SEQUENCE_NUM);
        streamManager.createMessageStream(STREAM_NAME);
        long seqNum = streamManager.pushData(ASSET_ID, PROPERTY_ID, BOOLEAN_VAL, Optional.empty());
        assertEquals(SEQUENCE_NUM, seqNum);
    }

    @Test
    public void pushData_booleanValue_UpdateTimeStampExists()
            throws StreamManagerException {
        streamManager = StreamManager.builder()
                .streamManagerClient(streamManagerClient).build();
        when(streamManagerClient.appendMessage(any(), any())).thenReturn(SEQUENCE_NUM);
        streamManager.createMessageStream(STREAM_NAME);
        Date date = new Date();
        long seqNum = streamManager.pushData(ASSET_ID, PROPERTY_ID, BOOLEAN_VAL, Optional.of(date));
        assertEquals(SEQUENCE_NUM, seqNum);
    }


    @Test
    public void putIntValue_validInput_returnsSequenceNumber()
            throws StreamManagerException {
        streamManager = StreamManager.builder()
                .streamManagerClient(streamManagerClient).build();
        when(streamManagerClient.appendMessage(any(), any())).thenReturn(SEQUENCE_NUM);
        streamManager.createMessageStream(STREAM_NAME);
        long seqNum = streamManager.pushData(ASSET_ID, PROPERTY_ID, INT_VAL, Optional.empty());
        assertEquals(SEQUENCE_NUM, seqNum);
    }

    @Test
    public void pushData_stringValue_returnsSequenceNumber()
            throws StreamManagerException {
        streamManager = StreamManager.builder()
                .streamManagerClient(streamManagerClient).build();
        when(streamManagerClient.appendMessage(any(), any())).thenReturn(SEQUENCE_NUM);
        streamManager.createMessageStream(STREAM_NAME);
        long seqNum = streamManager.pushData(ASSET_ID, PROPERTY_ID, STRING_VAL, Optional.empty());
        assertEquals(SEQUENCE_NUM, seqNum);
    }

    @Test
    public void pushData_doubleValue_returnsSequenceNumber()
            throws StreamManagerException {
        streamManager = StreamManager.builder()
                .streamManagerClient(streamManagerClient).build();
        when(streamManagerClient.appendMessage(any(), any())).thenReturn(SEQUENCE_NUM);
        streamManager.createMessageStream(STREAM_NAME);
        long seqNum = streamManager.pushData(ASSET_ID, PROPERTY_ID, DOUBLE_VAL, Optional.empty());
        assertEquals(SEQUENCE_NUM, seqNum);
    }

    @Test
    public void pushData_streamManagerClientThrowsException_throwsException()
            throws StreamManagerException {
        streamManager = StreamManager.builder()
                .streamManagerClient(streamManagerClient).build();
        when(streamManagerClient.appendMessage(any(), any())).thenThrow(StreamManagerException.class);
        streamManager.createMessageStream(STREAM_NAME);
        long seqNum = streamManager.pushData(ASSET_ID, PROPERTY_ID, DOUBLE_VAL, Optional.empty());
        assertEquals(0, seqNum);
    }

    @Test
    public void pushData_invalidInputType_throwsException() {
        streamManager = StreamManager.builder()
                .streamManagerClient(streamManagerClient).build();
        streamManager.createMessageStream(STREAM_NAME);
        long seqNum = streamManager.pushData(ASSET_ID, PROPERTY_ID, DOUBLE_VAL, Optional.empty());
        assertEquals(0, seqNum);
    }

    @Test
    public void pushData_withoutCreateStream_throwsException() {
        streamManager = StreamManager.builder()
                .streamManagerClient(streamManagerClient).build();
        assertThrows(EdgeConnectorForKVSException.class, () -> {
            streamManager.pushData(ASSET_ID, PROPERTY_ID, BOOLEAN_VAL
                    , Optional.empty());
        });
    }

    @Test
    public void pushData_bothNull_throwsException() {
        streamManager = StreamManager.builder().build();
        assertThrows(EdgeConnectorForKVSException.class, () -> {
            streamManager.pushData(ASSET_ID, PROPERTY_ID, BOOLEAN_VAL
                    , Optional.empty());
        });
    }
}

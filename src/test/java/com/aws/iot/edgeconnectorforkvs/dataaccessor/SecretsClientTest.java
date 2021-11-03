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
import com.aws.iot.edgeconnectorforkvs.util.JSONUtils;
import com.aws.iot.edgeconnectorforkvs.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class SecretsClientTest {
    private static final String SECRET_ID = "arn:aws:secretsmanager:us-west-2:12345678123:secret:camera1-eMfpke";
    private static final String SECRET_VALUE = "{\"RTSPStreamURL\": \"rtsp://admin:admin123@192.168.1.176:554\"}";
    private static final String INVALID_ACCESS_KEY_ID = "foo";
    private static final String INVALID_SECRET_ACCESS_KEY = "bar";
    private static final String RTSP_URL = "rtsp://admin:admin123@192.168.1.176:554";

    private SecretsClient secretsClient;
    @Mock
    private SecretsManagerClient secretsManagerClient;

    @BeforeEach
    public void setup() {
        this.secretsClient = SecretsClient.builder().secretsManagerClient(secretsManagerClient).build();
    }

    @Test
    public void getSecretValue_validSecretId_returnsSecretValue() {
        GetSecretValueResponse secretValueResponse = GetSecretValueResponse.builder()
                .secretString(SECRET_VALUE).build();
        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any())).thenReturn(secretValueResponse);
        String response = secretsClient.getSecretValue(SECRET_ID);
        assertEquals(response, SECRET_VALUE);
    }

    @Test
    public void getSecretValue_inValidSecretId_throwsException() {
        when(secretsManagerClient.getSecretValue((GetSecretValueRequest) any()))
                .thenThrow(ResourceNotFoundException.builder().build());
        assertThrows(EdgeConnectorForKVSException.class, () -> secretsClient.getSecretValue(SECRET_ID));
    }

    @Test
    public void createClient_invalidCredentials_throwsException() {
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(INVALID_ACCESS_KEY_ID, INVALID_SECRET_ACCESS_KEY);
        this.secretsClient = new SecretsClient(StaticCredentialsProvider.create(awsCreds), Region.US_WEST_2);
        assertThrows(EdgeConnectorForKVSException.class, () -> secretsClient.getSecretValue(SECRET_ID));
    }

    @Test
    public void jsonToMap_validPayload_returnsMap() {
        Map<String, Object> jsonMap = JSONUtils.jsonToMap(SECRET_VALUE);
        assertTrue(jsonMap.containsKey(Constants.SECRETS_MANAGER_SECRET_KEY));
        assertEquals(jsonMap.get(Constants.SECRETS_MANAGER_SECRET_KEY), RTSP_URL);
    }

}

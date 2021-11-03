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
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.inject.Inject;

/**
 * Wrapper class for SecretsManagerClient
 */
@Slf4j
@Builder
@AllArgsConstructor
public class SecretsClient {

    private SecretsManagerClient secretsManagerClient;
    private static final String VERSION_STAGE = "AWSCURRENT";

    /**
     * Constructor.
     * @param credentialProvider - AWS Credential Provider
     * @param region - AWS Region
     */
    @Inject
    public SecretsClient(AwsCredentialsProvider credentialProvider, Region region) {
        this.secretsManagerClient = SecretsManagerClient.builder()
                .credentialsProvider(credentialProvider)
                .region(region)
                .build();
    }

    /**
     * Wrapper for getSecretValue function in SecretsManagerClient.
     * @param secretId - secretArn for the secret
     * @return secret value as String
     * @throws EdgeConnectorForKVSException - Wrapper to all exception thrown by SecretsManagerClient
     */
    public String getSecretValue(@NonNull String secretId) throws EdgeConnectorForKVSException {
        try {
            log.info("Retrieving Secret Value for " + secretId);
            GetSecretValueRequest secretValueRequest = GetSecretValueRequest.builder()
                    .secretId(secretId)
                    .versionStage(VERSION_STAGE)
                    .build();
            GetSecretValueResponse secretValueResponse = this.secretsManagerClient.getSecretValue(secretValueRequest);
            return secretValueResponse.secretString();
        } catch (Exception e) {
            final String errorMessage = String.format("Could not getSecretValue for secretId: %s", secretId);
            log.error(errorMessage);
            throw new EdgeConnectorForKVSException(errorMessage, e);
        }
    }
}

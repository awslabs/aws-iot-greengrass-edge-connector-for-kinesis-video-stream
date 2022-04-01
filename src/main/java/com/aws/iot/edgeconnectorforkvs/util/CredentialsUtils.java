package com.aws.iot.edgeconnectorforkvs.util;

import com.amazonaws.internal.CredentialsEndpointProvider;

import com.google.common.annotations.VisibleForTesting;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CredentialsUtils {
    @VisibleForTesting
    static String getEnv(String key) {
        return System.getenv(key);
    }

    public static com.amazonaws.auth.ContainerCredentialsProvider getContainerCredentialsProviderV1() {
        return new com.amazonaws.auth.ContainerCredentialsProvider(
            new CredentialsEndpointProvider() {
                @Override
                public URI getCredentialsEndpoint() {
                    return URI.create(getEnv("AWS_CONTAINER_CREDENTIALS_FULL_URI"));
                }

                @Override
                public Map<String, String> getHeaders() {
                    if (getEnv("AWS_CONTAINER_AUTHORIZATION_TOKEN") != null) {
                        return Collections.singletonMap("Authorization",
                                getEnv("AWS_CONTAINER_AUTHORIZATION_TOKEN"));
                    }
                    return new HashMap<String, String>();
                }
            });
    }
}

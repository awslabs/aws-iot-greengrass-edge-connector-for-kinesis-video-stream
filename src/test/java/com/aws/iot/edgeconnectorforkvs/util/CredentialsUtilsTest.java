package com.aws.iot.edgeconnectorforkvs.util;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class CredentialsUtilsTest {
    @Test
    public void testGetContainerCredentialsProviderV1() {
        //when
        CredentialsUtils credentialsUtils = spy(CredentialsUtils.class);
        try (MockedStatic<CredentialsUtils> mockCredentialsUtils =
                     mockStatic(CredentialsUtils.class, Mockito.CALLS_REAL_METHODS)) {
            when(credentialsUtils.getEnv("AWS_CONTAINER_CREDENTIALS_FULL_URI")).thenReturn("mock_URL");
            when(credentialsUtils.getEnv("AWS_CONTAINER_AUTHORIZATION_TOKEN")).thenReturn("mock_Token");

            //then
            com.amazonaws.auth.ContainerCredentialsProvider credentialsProvider =
                    CredentialsUtils.getContainerCredentialsProviderV1();
            //verify
            //Throwed excption is expacted when use mock url and Token
            assertThrows(Exception.class, () -> credentialsProvider.getCredentials());
        }
    }

    @Test
    public void testGetContainerCredentialsProviderV1_Null_Token() {
        //when
        CredentialsUtils credentialsUtils = spy(CredentialsUtils.class);
        try (MockedStatic<CredentialsUtils> mockCredentialsUtils =
                     mockStatic(CredentialsUtils.class, Mockito.CALLS_REAL_METHODS)) {
            when(credentialsUtils.getEnv("AWS_CONTAINER_CREDENTIALS_FULL_URI")).thenReturn("mock_URL");
            when(credentialsUtils.getEnv("AWS_CONTAINER_AUTHORIZATION_TOKEN")).thenReturn(null);

            //then
            com.amazonaws.auth.ContainerCredentialsProvider credentialsProvider =
                    CredentialsUtils.getContainerCredentialsProviderV1();
            //verify
            //Throwed excption is expacted when use mock url and Token
            assertThrows(Exception.class, () -> credentialsProvider.getCredentials());
        }
    }
}

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

package com.aws.iot.edgeconnectorforkvs.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.crt.CrtRuntimeException;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
@ExtendWith(SystemStubsExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IPCUtilsTest {

    private static final String IPC_SOCKET_PATH = "AWS_GG_NUCLEUS_DOMAIN_SOCKET_FILEPATH_FOR_COMPONENT";
    private static final String SECRET_AUTH_TOKEN = "SVCUID";

    @SystemStub
    private EnvironmentVariables environment;

    @Mock
    private static EventStreamRPCConnection connection;

    @BeforeEach
    public void setup() {
        environment.set(IPC_SOCKET_PATH, "foo");
        environment.set(SECRET_AUTH_TOKEN, "bar");
    }

    @Test
    @Order(1)
    public void getEventStreamRpcConnection_noMocks_throwsCrtRuntimeException() {
        assertThrows(CrtRuntimeException.class, () -> {
            IPCUtils.getEventStreamRpcConnection();
        });
    }

    @Test
    @Order(2)
    public void getEventStreamRpcConnection_onConnectionError_throwsExecutionException() {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        doAnswer(invocationOnMock -> {
            EventStreamRPCConnection.LifecycleHandler lifecycleHandler = invocationOnMock.getArgument(0);
            lifecycleHandler.onError(new Exception());
            return completableFuture;
        }).when(connection).connect(any());

        try (MockedStatic<IPCUtils> util = Mockito.mockStatic(IPCUtils.class, invocationOnMock -> {
            Method method = invocationOnMock.getMethod();
            if ("getConnectionInstance".equals(method.getName())) {
                return connection;
            }
            return invocationOnMock.callRealMethod();
        })) {
            assertThrows(ExecutionException.class, () -> {
                IPCUtils.getEventStreamRpcConnection();
            });
        }
    }

    @Test
    @Order(3)
    public void getEventStreamRpcConnection_onConnectDisconnect_returnsConnection()
            throws ExecutionException, InterruptedException {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        doAnswer(invocationOnMock -> {
            EventStreamRPCConnection.LifecycleHandler lifecycleHandler = invocationOnMock.getArgument(0);
            lifecycleHandler.onConnect();
            // To ensure clientConnection is reset to null before next test is executed
            lifecycleHandler.onDisconnect(0);
            return completableFuture;
        }).when(connection).connect(any());

        try (MockedStatic<IPCUtils> util = Mockito.mockStatic(IPCUtils.class, invocationOnMock -> {
            Method method = invocationOnMock.getMethod();
            if ("getConnectionInstance".equals(method.getName())) {
                return connection;
            }
            return invocationOnMock.callRealMethod();
        })) {
            EventStreamRPCConnection result = IPCUtils.getEventStreamRpcConnection();
            assertEquals(connection, result);
        }
    }

    @Test
    @Order(4)
    public void getEventStreamRpcConnection_onClientNotNull_returnsConnection()
            throws ExecutionException, InterruptedException {
        EventStreamRPCConnection result = IPCUtils.getEventStreamRpcConnection();
        assertEquals(EventStreamRPCConnection.class, result.getClass());
    }


}

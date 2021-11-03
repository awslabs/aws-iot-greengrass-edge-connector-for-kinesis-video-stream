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

import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnection;
import software.amazon.awssdk.eventstreamrpc.EventStreamRPCConnectionConfig;
import software.amazon.awssdk.eventstreamrpc.GreengrassConnectMessageSupplier;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


public final class IPCUtils {
    private static final int DEFAULT_PORT_NUMBER = 8033;
    private static final int CONNECTION_TIMEOUT_IN_MS = 3000;
    private static final String IPC_SOCKET_PATH = "AWS_GG_NUCLEUS_DOMAIN_SOCKET_FILEPATH_FOR_COMPONENT";
    private static final String SECRET_AUTH_TOKEN = "SVCUID";
    private static volatile EventStreamRPCConnection clientConnection = null;

    private IPCUtils() {
    }

    public static EventStreamRPCConnection getEventStreamRpcConnection()
            throws ExecutionException, InterruptedException {
        String ipcServerSocketPath = System.getenv(IPC_SOCKET_PATH);
        String authToken = System.getenv(SECRET_AUTH_TOKEN);

        if (clientConnection == null) {
            clientConnection = connectToGGCOverEventStreamIPC(authToken, ipcServerSocketPath);
        }
        return clientConnection;
    }

    private static EventStreamRPCConnection connectToGGCOverEventStreamIPC(String authToken,
                                                                           String ipcServerSocketPath)
            throws ExecutionException, InterruptedException {

        try (EventLoopGroup elGroup = new EventLoopGroup(1);
             ClientBootstrap clientBootstrap = new ClientBootstrap(elGroup, null)) {

            SocketOptions socketOptions = getSocketOptionsForIPC();
            final EventStreamRPCConnectionConfig config =
                    new EventStreamRPCConnectionConfig(clientBootstrap, elGroup, socketOptions, null,
                            ipcServerSocketPath, DEFAULT_PORT_NUMBER,
                            GreengrassConnectMessageSupplier.connectMessageSupplier(authToken));
            final CompletableFuture<Void> connected = new CompletableFuture<>();
            final EventStreamRPCConnection connection = getConnectionInstance(config);
            final boolean[] disconnected = {false};
            final int[] disconnectedCode = {-1};

            connection.connect(new EventStreamRPCConnection.LifecycleHandler() {
                @Override
                public void onConnect() {
                    connected.complete(null);
                }

                @Override
                public void onDisconnect(int errorCode) {
                    disconnected[0] = true;
                    disconnectedCode[0] = errorCode;
                    clientConnection = null;
                }

                @Override
                public boolean onError(Throwable t) {
                    connected.completeExceptionally(t);
                    clientConnection = null;
                    return true;
                }
            });
            // waits for future to complete
            connected.get();
            return connection;
        }
    }

    private static EventStreamRPCConnection getConnectionInstance(EventStreamRPCConnectionConfig config) {
        return new EventStreamRPCConnection(config);
    }

    private static SocketOptions getSocketOptionsForIPC() {
        SocketOptions socketOptions = new SocketOptions();
        socketOptions.connectTimeoutMs = CONNECTION_TIMEOUT_IN_MS;
        socketOptions.domain = SocketOptions.SocketDomain.LOCAL;
        socketOptions.type = SocketOptions.SocketType.STREAM;
        return socketOptions;
    }

}

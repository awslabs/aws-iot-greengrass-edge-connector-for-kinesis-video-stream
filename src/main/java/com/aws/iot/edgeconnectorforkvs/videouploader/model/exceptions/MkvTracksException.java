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

package com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions;

/**
 * An exception for parsing MKV tracks.
 */
public class MkvTracksException extends RuntimeException {
    /**
     * This exception is thrown it fails to parse MKV tracks.
     *
     * @param message The failure message
     */
    public MkvTracksException(String message) {
        super(message);
    }

    /**
     * This exception is thrown it fails to parse MKV tracks.
     *
     * @param message The failure message
     * @param cause   The failure cause
     */
    public MkvTracksException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * This exception is thrown it fails to parse MKV tracks.
     *
     * @param cause The failure cause
     */
    public MkvTracksException(Throwable cause) {
        super(cause);
    }
}

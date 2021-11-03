/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.aws.iot.edgeconnectorforkvs.videouploader.model.exceptions;

/**
 * An exception for failures from KVS library or service.
 */
public class VideoUploaderException extends RuntimeException {

    /**
     * This exception is thrown when there is video uploading failure caused from KVS library or service.
     *
     * @param message The failure message
     */
    public VideoUploaderException(String message) {
        super(message);
    }

    /**
     * This exception is thrown when there is video uploading failure caused from KVS library or service.
     *
     * @param message The failure message
     * @param cause   The failure cause
     */
    public VideoUploaderException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * This exception is thrown when there is video uploading failure caused from KVS library or service.
     *
     * @param cause The failure cause
     */
    public VideoUploaderException(Throwable cause) {
        super(cause);
    }
}

package com.polar.sdk.api

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import java.io.ByteArrayOutputStream

interface PolarBleLowLevelApi {

    /**
     * Read any file over PFtp BLE client. API user must know the exact path to the desired file.
     * API user must also take care of parsing the returned ByteArray payload to the desired data object.
     * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
     * @param identifier Polar device ID or BT address
     * @param filePath Path to the desired file in a Polar device.
     * @return Maybe (ByteArray or empty)
     */
    @OptIn
    fun readFile(
        identifier: String,
        filePath: String
    ): Maybe<ByteArray>

    /**
     * Write any file over PFtp BLE client. API user must know the exact path to the desired file.
     * API user must also take care of parsing the returned ByteArray payload to the desired data object.
     * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
     * @param identifier Polar device ID or BT address
     * @param filePath Path to the directory in device  in a Polar device.
     * @param fileData, file data in already serialized into ByteArray format.
     * @return Completable or error
     */
    @OptIn
    fun writeFile(
        identifier: String,
        filePath: String,
        fileData: ByteArray
    ): Completable

    /**
     * Delete any file or directory over PFtp BLE client. API user must know the exact path to the desired file.
     * API user must also take care of parsing the returned ByteArray payload to the desired data object.
     * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
     * @param identifier Polar device ID or BT address
     * @param filePath Path of the file or directory to be deleted at a Polar device.
     * @return Completable or error
     */
    @OptIn
    fun deleteFileOrDirectory(
        identifier: String,
        filePath: String
    ): Completable

    /**
     * List all files in the given path
     *
     * @param identifier Polar device ID or BT address
     * @param directoryPath Path to the desired directory in a Polar device from which to list all files.
     * @param recurseDeep Recursion goes to the bottom of the file tree when true.
     * NOTE: this is an experimental API intended for Polar internal use only. Polar will not support 3rd party users with this API.
     * @return List of files or error
     */
    @OptIn
    fun getFileList(
        identifier: String,
        filePath: String,
        recurseDeep: Boolean
    ): Single<List<String>>
}
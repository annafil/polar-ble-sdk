package com.polar.sdk.impl.utils

import com.google.protobuf.ByteString
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.PftpResponseError
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.getFileSystemType
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.FileSystemType
import com.polar.sdk.api.errors.PolarDeviceDisconnected
import com.polar.sdk.api.errors.PolarOperationNotSupported
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.impl.BDBleApiImpl
import com.polar.sdk.impl.BDBleApiImpl.Companion
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import protocol.PftpError.PbPFtpError
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import com.polar.sdk.impl.utils.PolarServiceClientUtils.sessionPsFtpClientReady
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

internal object  PolarFileUtils {

    internal fun removeSingleFile(
        identifier: String,
        filePath: String,
        listener: BleDeviceListener?,
        tag: String
    ): Single<ByteArrayOutputStream> {
        val session = try {
            sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.error(PolarServiceNotAvailable())

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
        builder.path = filePath
        return client.request(builder.build().toByteArray()).onErrorResumeNext { error: Throwable ->
            BleLogger.d(tag, "An error occurred while trying to remove $filePath, error: $error")
            Single.error(handleError(error))
        }
    }

    fun listFiles(
        identifier: String,
        folderPath: String = "/",
        condition: FetchRecursiveCondition,
        listener: BleDeviceListener?,
        tag: String
    ): Flowable<String> {
        val session = try {
            sessionPsFtpClientReady(identifier, listener = listener)
        } catch (error: Throwable) {
            return Flowable.error(error)
        }

        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Flowable.error(PolarServiceNotAvailable())
        return when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.POLAR_FILE_SYSTEM_V2 -> {
                var path = folderPath.ifEmpty { "/" }
                path = if (path.first() != '/') "/$path" else path
                path = if (path.last() != '/') "$path/" else path
                fetchRecursively(
                    client = client,
                    path = path,
                    recurseDeep = true,
                    condition = condition,
                    tag = tag)
                    .map {
                        it.first
                    }.onErrorResumeNext { error: Throwable ->
                        BleLogger.e(tag, "An error occurred while listing files in path '$folderPath' in device $identifier, error: ${error}.")
                        Flowable.error(handleError(error))
                    }
            }
            else -> Flowable.error(PolarOperationNotSupported())
        }
    }

    internal fun interface FetchRecursiveCondition {
        fun include(entry: String): Boolean
    }

    internal fun fetchRecursively(
        client: BlePsFtpClient,
        path: String,
        condition: FetchRecursiveCondition?,
        recurseDeep: Boolean?,
        tag: String
    ): Flowable<Pair<String, Long>> {
        BleLogger.d(tag, "fetchRecursively: Starting fetch for path: $path")

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = path

        return client.request(builder.build().toByteArray())
            .toFlowable()
            .flatMap { byteArrayOutputStream ->

                val dir = PbPFtpDirectory.parseFrom(byteArrayOutputStream.toByteArray())
                val entries = mutableMapOf<String, Long>()

                if (condition != null) {
                    for (entry in dir.entriesList) {
                        BleLogger.d(tag, "fetchRecursively: Found entry, name: ${entry.name}, size: ${entry.size}")
                        if (condition.include(entry.name)) {
                            entries[path + entry.name] = entry.size
                        }
                    }
                } else {
                    // Do not use matching
                    for (entry in dir.entriesList) {
                        entries[path + entry.name] = entry.size
                    }
                }

                if (entries.isNotEmpty()) {
                    return@flatMap Flowable.fromIterable(entries.toList())
                        .flatMap { entry ->
                            if (entry.first.endsWith("/") && recurseDeep == true) {
                                fetchRecursively(client, entry.first, condition, recurseDeep, tag)
                            } else {
                                Flowable.just(entry)
                            }
                        }
                }

                BleLogger.d(tag, "fetchRecursively: No entries found for path: $path")
                Flowable.empty()
            }
            .doOnError { error ->
                BleLogger.e(tag, "fetchRecursively: Error occurred for path: $path, error: $error")
            }
    }

    internal fun pFtpWriteOperation(identifier: String,
                                    listener: BleDeviceListener?,
                                    data: ByteArray,
                                    path: String,
                                    tag: String
    ): Completable {
        return Completable.create { emitter ->
            try {
                val session = sessionPsFtpClientReady(identifier, listener)
                val client =
                    session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
                        ?: throw PolarServiceNotAvailable()
                val builder = PftpRequest.PbPFtpOperation.newBuilder()
                builder.command = PftpRequest.PbPFtpOperation.Command.PUT
                builder.path = path
                val dataInputStream = ByteArrayInputStream(data)

                client.write(builder.build().toByteArray(), dataInputStream)
                    .subscribe({
                        BleLogger.d(tag, "pFtpWriteOperation client write progress $it: $path")
                    },{ error ->
                        BleLogger.e(tag, "pFtpWriteOperation() client write $path error: $error")
                        emitter.onError(error)
                    },{
                        BleLogger.d(tag, "pFtpWriteOperation client write completed for $path")
                        emitter.onComplete()
                    })
            } catch (error: Throwable) {
                BleLogger.e(tag, "pFtpWriteOperation() $path error: $error")
                emitter.onError(error)
            }
        }
    }

    private fun handleError(throwable: Throwable): Exception {
        if (throwable is BleDisconnected) {
            return PolarDeviceDisconnected()
        } else if (throwable is PftpResponseError) {
            val errorId = throwable.error
            val pftpError = PbPFtpError.forNumber(errorId)
            if (pftpError != null) return Exception(pftpError.toString())
        }
        return Exception(throwable)
    }

    /*
    * BLE Low Level methods. These are experimental methods. Usage is heavily discouraged,
    * use SDK APIs from PolarBleApi instead.
    */

    // Low level API method
    fun readFile(identifier: String, filePath: String, listener: BleDeviceListener?, tag: String): Maybe<ByteArray> {
        val session = try {
            sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return Maybe.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Maybe.error(PolarServiceNotAvailable())

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.GET
        builder.path = filePath
        return client.request(builder.build().toByteArray()).toMaybe().onErrorResumeNext { throwable: Throwable ->
            Maybe.error(handleError(throwable))
        }.map { data ->
            BleLogger.d(tag, "readFile at path filePath $filePath")
            data.toByteArray()
        }
    }

    // Low level API method
    fun writeFile(identifier: String, filePath: String, listener: BleDeviceListener?, fileData: ByteArray, tag: String): Completable {
        return pFtpWriteOperation(identifier, listener, fileData, filePath, tag)
    }

    // Low level API method
    fun getFileList(identifier: String, filePath: String, recurseDeep: Boolean, listener: BleDeviceListener?, tag: String): Single<List<String>> {
        val session = try {
            sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return Single.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient? ?: return Single.error(PolarServiceNotAvailable())

        return when (getFileSystemType(session.polarDeviceType)) {
            FileSystemType.POLAR_FILE_SYSTEM_V2 -> {
                    var path = filePath.ifEmpty { "/" }
                    path = if (path.first() != '/') "/$path" else path
                    path = if (path.last() != '/') "$path/" else path
                    fetchRecursively(
                        client = client,
                        path = path,
                        condition = null,
                        recurseDeep = recurseDeep,
                        tag = tag
                    ).map {
                        it.first
                    }.onErrorResumeNext { throwable: Throwable ->
                        Flowable.error(handleError(throwable))
                    }.toList()
            }
            else -> Single.error(PolarOperationNotSupported())
        }
    }

    // Low level API method
    fun removeFileOrDirectory(
        identifier: String,
        filePath: String,
        listener: BleDeviceListener?,
        tag: String
    ):  Completable = Completable.defer {
        val session = try {
            sessionPsFtpClientReady(identifier, listener)
        } catch (error: Throwable) {
            return@defer Completable.error(error)
        }
        val client = session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) as BlePsFtpClient?
            ?: return@defer Completable.error(PolarServiceNotAvailable())

        val builder = PftpRequest.PbPFtpOperation.newBuilder()
        builder.command = PftpRequest.PbPFtpOperation.Command.REMOVE
        builder.path = filePath
        Completable.create { emitter ->
            client.request(builder.build().toByteArray())
                .onErrorResumeNext { error: Throwable ->
                    BleLogger.d(tag, "An error occurred while trying to remove $filePath, error: $error")
                    Single.error(handleError(error))
                }.subscribe({
                    BleLogger.d(tag, "All items successfully removed from filePath $filePath from device $identifier.")
                    emitter.onComplete()
                },{ error ->
                    BleLogger.d(tag, "Error while trying to remove item from filePath $filePath from device $identifier, error: $error.")
                    emitter.onError(error)
                })
        }
    }
}
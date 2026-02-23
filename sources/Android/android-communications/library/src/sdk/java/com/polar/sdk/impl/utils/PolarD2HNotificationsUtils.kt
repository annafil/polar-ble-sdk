// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl.utils

import com.google.protobuf.InvalidProtocolBufferException
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarD2HNotificationData
import com.polar.sdk.api.PolarDeviceToHostNotification
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import protocol.PftpNotification.*

private const val TAG = "PolarD2HNotificationsUtils"

/**
 * Extension function for BlePsFtpClient to observe device-to-host notifications.
 *
 * This function filters the raw notification stream from the device and maps
 * the notifications to [PolarD2HNotificationData] objects with parsed parameters.
 *
 * @param identifier Polar device ID or BT address (used for logging)
 * @return Flowable stream of parsed device-to-host notifications
 */
fun BlePsFtpClient.observeDeviceToHostNotifications(identifier: String): Flowable<PolarD2HNotificationData> {
    return waitForNotification(Schedulers.newThread())
        .flatMap { notification: BlePsFtpUtils.PftpNotificationMessage ->
            val notificationType = PolarDeviceToHostNotification.fromValue(notification.id)
            if (notificationType == null) {
                BleLogger.w(TAG, "Unknown notification type: ${notification.id}")
                Flowable.empty()
            } else {
                val parameters = notification.byteArrayOutputStream.toByteArray()
                val parsedParameters = parseD2HNotificationParameters(notificationType, parameters)
                Flowable.just(PolarD2HNotificationData(notificationType, parameters, parsedParameters))
            }
        }
        .doOnNext { data ->
            BleLogger.d(TAG, "Received D2H notification for $identifier: ${data.notificationType}")
        }
        .doOnError { error ->
            BleLogger.e(TAG, "D2H notification error for $identifier: ${error.message}")
        }
}

/**
 * Parses the raw parameter data based on the notification type.
 *
 * @param notificationType The type of notification
 * @param data Raw parameter data
 * @return Parsed parameter object or null if parsing fails or no parameters expected
 */
private fun parseD2HNotificationParameters(
    notificationType: PolarDeviceToHostNotification,
    data: ByteArray
): Any? {
    if (data.isEmpty()) {
        return null
    }

    return try {
        when (notificationType) {
            PolarDeviceToHostNotification.SYNC_REQUIRED -> {
                PbPFtpSyncRequiredParams.parseFrom(data)
            }
            PolarDeviceToHostNotification.FILESYSTEM_MODIFIED -> {
                PbPFtpFilesystemModifiedParams.parseFrom(data)
            }
            PolarDeviceToHostNotification.INACTIVITY_ALERT -> {
                PbPFtpInactivityAlert.parseFrom(data)
            }
            PolarDeviceToHostNotification.TRAINING_SESSION_STATUS -> {
                PbPFtpTrainingSessionStatus.parseFrom(data)
            }
            PolarDeviceToHostNotification.AUTOSYNC_STATUS -> {
                PbPFtpAutoSyncStatusParams.parseFrom(data)
            }
            PolarDeviceToHostNotification.PNS_DH_NOTIFICATION_RESPONSE -> {
                PbPftpPnsDHNotificationResponse.parseFrom(data)
            }
            PolarDeviceToHostNotification.PNS_SETTINGS -> {
                PbPftpPnsState.parseFrom(data)
            }
            PolarDeviceToHostNotification.START_GPS_MEASUREMENT -> {
                PbPftpStartGPSMeasurement.parseFrom(data)
            }
            PolarDeviceToHostNotification.POLAR_SHELL_DH_DATA -> {
                PbPFtpPolarShellMessageParams.parseFrom(data)
            }
            PolarDeviceToHostNotification.MEDIA_CONTROL_REQUEST_DH -> {
                PbPftpDHMediaControlRequest.parseFrom(data)
            }
            PolarDeviceToHostNotification.MEDIA_CONTROL_COMMAND_DH -> {
                PbPftpDHMediaControlCommand.parseFrom(data)
            }
            PolarDeviceToHostNotification.MEDIA_CONTROL_ENABLED -> {
                PbPftpDHMediaControlEnabled.parseFrom(data)
            }
            PolarDeviceToHostNotification.REST_API_EVENT -> {
                PbPftpDHRestApiEvent.parseFrom(data)
            }
            PolarDeviceToHostNotification.EXERCISE_STATUS -> {
                PbPftpDHExerciseStatus.parseFrom(data)
            }
            // Notifications without parameters or not yet implemented
            else -> {
                BleLogger.d(TAG, "No parameter parsing implemented for: $notificationType")
                null
            }
        }
    } catch (e: InvalidProtocolBufferException) {
        BleLogger.e(TAG, "Failed to parse parameters for $notificationType: ${e.message}")
        null
    }
}

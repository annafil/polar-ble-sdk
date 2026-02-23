// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarD2HNotificationData
import com.polar.sdk.api.PolarDeviceToHostNotification
import com.polar.sdk.impl.utils.observeDeviceToHostNotifications
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import protocol.PftpNotification.*
import java.io.ByteArrayOutputStream
import java.util.*

class PolarD2HNotificationsUtilsTest {

    private lateinit var mockClient: BlePsFtpClient

    @Before
    fun setUp() {
        mockClient = mockk(relaxed = true)
    }

    @Test
    fun `test receives sync required notification`() {
        // Arrange
        val syncRequiredNotificationId = PbPFtpDevToHostNotification.SYNC_REQUIRED.number
        val syncRequiredNotificationParameter = PbPFtpSyncRequiredParams.newBuilder().apply {
            addSyncTriggers(
                PbPFtpSyncTrigger.newBuilder()
                    .setSource(PbPFtpSyncTriggerSource.TIMED)
                    .build()
            )
        }.build()
        val syncRequiredNotificationParamsData = syncRequiredNotificationParameter.toByteArray()

        val keepAliveNotificationId = PbPFtpDevToHostNotification.KEEP_BACKGROUND_ALIVE.number

        val notifications = listOf(
            createMockNotification(syncRequiredNotificationId, syncRequiredNotificationParamsData),
            createMockNotification(keepAliveNotificationId, ByteArray(0))
        )

        every { mockClient.waitForNotification(any()) } returns Flowable.fromIterable(notifications)

        // Act
        val results = mockClient.observeDeviceToHostNotifications("test-device-id")
            .toList()
            .blockingGet()

        // Assert
        assertNotNull(results)
        assertEquals(2, results.size)

        // Check first notification (SYNC_REQUIRED)
        assertEquals(PolarDeviceToHostNotification.SYNC_REQUIRED, results[0].notificationType)
        assertArrayEquals(syncRequiredNotificationParamsData, results[0].parameters)
        assertNotNull(results[0].parsedParameters)
        assertTrue(results[0].parsedParameters is PbPFtpSyncRequiredParams)
        val parsedParams = results[0].parsedParameters as PbPFtpSyncRequiredParams
        assertEquals(syncRequiredNotificationParameter, parsedParams)

        // Check second notification (KEEP_BACKGROUND_ALIVE)
        assertEquals(PolarDeviceToHostNotification.KEEP_BACKGROUND_ALIVE, results[1].notificationType)
        assertEquals(0, results[1].parameters.size)
    }

    @Test
    fun `test receives filesystem modified notification`() {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.FILESYSTEM_MODIFIED.number

        val fileSystemModifiedParams = PbPFtpFilesystemModifiedParams.newBuilder()
            .setAction(Action.CREATED)
            .setPath("/U/0/")
            .build()
        val serializedData = fileSystemModifiedParams.toByteArray()

        val notification = createMockNotification(notificationId, serializedData)

        every { mockClient.waitForNotification(any()) } returns Flowable.just(notification)

        // Act
        val result = mockClient.observeDeviceToHostNotifications("test-device-id")
            .blockingFirst()

        // Assert
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.FILESYSTEM_MODIFIED, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPFtpFilesystemModifiedParams)
        val parsedParams = result.parsedParameters as PbPFtpFilesystemModifiedParams
        assertEquals(fileSystemModifiedParams, parsedParams)
    }

    @Test
    fun `test receives inactivity alert notification`() {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.INACTIVITY_ALERT.number

        val inactivityAlertParams = PbPFtpInactivityAlert.newBuilder()
            .setCountdown(5)
            .build()
        val serializedData = inactivityAlertParams.toByteArray()

        val notification = createMockNotification(notificationId, serializedData)

        every { mockClient.waitForNotification(any()) } returns Flowable.just(notification)

        // Act
        val result = mockClient.observeDeviceToHostNotifications("test-device-id")
            .blockingFirst()

        // Assert
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.INACTIVITY_ALERT, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPFtpInactivityAlert)
        val parsedParams = result.parsedParameters as PbPFtpInactivityAlert
        assertEquals(5, parsedParams.countdown)
    }

    @Test
    fun `test receives training session status notification`() {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.TRAINING_SESSION_STATUS.number

        val trainingSessionStatus = PbPFtpTrainingSessionStatus.newBuilder()
            .setInprogress(true)
            .build()
        val serializedData = trainingSessionStatus.toByteArray()

        val notification = createMockNotification(notificationId, serializedData)

        every { mockClient.waitForNotification(any()) } returns Flowable.just(notification)

        // Act
        val result = mockClient.observeDeviceToHostNotifications("test-device-id")
            .blockingFirst()

        // Assert
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.TRAINING_SESSION_STATUS, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPFtpTrainingSessionStatus)
        val parsedParams = result.parsedParameters as PbPFtpTrainingSessionStatus
        assertTrue(parsedParams.inprogress)
    }

    @Test
    fun `test receives autosync status notification`() {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.AUTOSYNC_STATUS.number

        val autoSyncStatus = PbPFtpAutoSyncStatusParams.newBuilder()
            .setSucceeded(true)
            .setDescription("Sync completed successfully")
            .build()
        val serializedData = autoSyncStatus.toByteArray()

        val notification = createMockNotification(notificationId, serializedData)

        every { mockClient.waitForNotification(any()) } returns Flowable.just(notification)

        // Act
        val result = mockClient.observeDeviceToHostNotifications("test-device-id")
            .blockingFirst()

        // Assert
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.AUTOSYNC_STATUS, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPFtpAutoSyncStatusParams)
        val parsedParams = result.parsedParameters as PbPFtpAutoSyncStatusParams
        assertTrue(parsedParams.succeeded)
        assertEquals("Sync completed successfully", parsedParams.description)
    }

    @Test
    fun `test receives notification without parameters`() {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.STOP_GPS_MEASUREMENT.number

        val notification = createMockNotification(notificationId, ByteArray(0))

        every { mockClient.waitForNotification(any()) } returns Flowable.just(notification)

        // Act
        val result = mockClient.observeDeviceToHostNotifications("test-device-id")
            .blockingFirst()

        // Assert
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.STOP_GPS_MEASUREMENT, result.notificationType)
        assertEquals(0, result.parameters.size)
        assertNull(result.parsedParameters)
    }

    @Test
    fun `test filters unknown notification types`() {
        // Arrange
        val unknownNotificationId = 999
        val validNotificationId = PbPFtpDevToHostNotification.IDLING.number

        val notifications = listOf(
            createMockNotification(unknownNotificationId, ByteArray(0)),
            createMockNotification(validNotificationId, ByteArray(0))
        )

        every { mockClient.waitForNotification(any()) } returns Flowable.fromIterable(notifications)

        // Act
        val results = mockClient.observeDeviceToHostNotifications("test-device-id")
            .toList()
            .blockingGet()

        // Assert
        // Unknown notification should be filtered out
        assertEquals(1, results.size)
        assertEquals(PolarDeviceToHostNotification.IDLING, results[0].notificationType)
    }

    @Test
    fun `test handles invalid protobuf data gracefully`() {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.SYNC_REQUIRED.number
        val invalidData = "invalid protobuf data".toByteArray()

        val notification = createMockNotification(notificationId, invalidData)

        every { mockClient.waitForNotification(any()) } returns Flowable.just(notification)

        // Act
        val result = mockClient.observeDeviceToHostNotifications("test-device-id")
            .blockingFirst()

        // Assert
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.SYNC_REQUIRED, result.notificationType)
        assertArrayEquals(invalidData, result.parameters)
        // parsedParameters should be null for invalid data
        assertNull(result.parsedParameters)
    }

    @Test
    fun `test receives media control request notification`() {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.MEDIA_CONTROL_REQUEST_DH.number

        val mediaControlRequest = PbPftpDHMediaControlRequest.newBuilder()
            .setRequest(MediaControlRequest.GET_MEDIA_DATA)
            .build()
        val serializedData = mediaControlRequest.toByteArray()

        val notification = createMockNotification(notificationId, serializedData)

        every { mockClient.waitForNotification(any()) } returns Flowable.just(notification)

        // Act
        val result = mockClient.observeDeviceToHostNotifications("test-device-id")
            .blockingFirst()

        // Assert
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.MEDIA_CONTROL_REQUEST_DH, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPftpDHMediaControlRequest)
        val parsedParams = result.parsedParameters as PbPftpDHMediaControlRequest
        assertEquals(MediaControlRequest.GET_MEDIA_DATA, parsedParams.request)
    }

    @Test
    fun `test receives media control command notification`() {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.MEDIA_CONTROL_COMMAND_DH.number

        val mediaControlCommand = PbPftpDHMediaControlCommand.newBuilder()
            .setCommand(MediaControlCommand.PLAY)
            .build()
        val serializedData = mediaControlCommand.toByteArray()

        val notification = createMockNotification(notificationId, serializedData)

        every { mockClient.waitForNotification(any()) } returns Flowable.just(notification)

        // Act
        val result = mockClient.observeDeviceToHostNotifications("test-device-id")
            .blockingFirst()

        // Assert
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.MEDIA_CONTROL_COMMAND_DH, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPftpDHMediaControlCommand)
        val parsedParams = result.parsedParameters as PbPftpDHMediaControlCommand
        assertEquals(MediaControlCommand.PLAY, parsedParams.command)
    }

    @Test
    fun `test receives start GPS measurement notification`() {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.START_GPS_MEASUREMENT.number

        val startGpsMeasurement = PbPftpStartGPSMeasurement.newBuilder()
            .setMinimumInterval(1000)
            .setAccuracy(2)
            .setLatitude(60.1695)
            .setLongitude(24.9354)
            .build()
        val serializedData = startGpsMeasurement.toByteArray()

        val notification = createMockNotification(notificationId, serializedData)

        every { mockClient.waitForNotification(any()) } returns Flowable.just(notification)

        // Act
        val result = mockClient.observeDeviceToHostNotifications("test-device-id")
            .blockingFirst()

        // Assert
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.START_GPS_MEASUREMENT, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPftpStartGPSMeasurement)
        val parsedParams = result.parsedParameters as PbPftpStartGPSMeasurement
        assertEquals(1000, parsedParams.minimumInterval)
        assertEquals(2, parsedParams.accuracy)
        assertEquals(60.1695, parsedParams.latitude, 0.0001)
        assertEquals(24.9354, parsedParams.longitude, 0.0001)
    }

    /**
     * Helper function to create a mock notification message
     */
    private fun createMockNotification(id: Int, data: ByteArray): BlePsFtpUtils.PftpNotificationMessage {
        val notification = BlePsFtpUtils.PftpNotificationMessage()
        notification.id = id
        notification.byteArrayOutputStream = ByteArrayOutputStream().apply {
            write(data)
        }
        return notification
    }
}

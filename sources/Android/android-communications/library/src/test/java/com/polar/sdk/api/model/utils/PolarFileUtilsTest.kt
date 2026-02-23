package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.getFileSystemType
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarOperationNotSupported
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.impl.utils.PolarFileUtils
import fi.polar.remote.representation.protobuf.DailySummary
import fi.polar.remote.representation.protobuf.DailySummary.PbActivityGoalSummary
import fi.polar.remote.representation.protobuf.DailySummary.PbDailySummary
import fi.polar.remote.representation.protobuf.Types
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbDuration
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import io.reactivex.rxjava3.core.Single
import org.junit.Assert
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

class PolarFileUtilsTest {

    @Test
    fun testListFilesRecurseShallowSuccess() {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener, _) = mockBleConnection(deviceId)

        val dateDirectories = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("20250101/").setSize(8192L).build(),
                        PbPFtpEntry.newBuilder().setName("20250202/").setSize(8192L).build()
                    )
                ).build().writeTo(this)
        }

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any())  } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        every { client.request(any<ByteArray>()) } returns Single.just(dateDirectories)

        val expectedPaths = mutableListOf("/U/0/20250101/", "/U/0/20250202/")
        // Act
        val testObserver = PolarFileUtils.getFileList(deviceId,"/U/0/", false,  listener, "").test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(expectedPaths)
        verify(exactly = 1) { client.isServiceDiscovered }
        verify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testListFilesRecurseDeepSuccess() {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener, _) = mockBleConnection(deviceId)

        val dateDirectories = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("20250101/").setSize(8192L).build(),
                        PbPFtpEntry.newBuilder().setName("20250202/").setSize(8192L).build()
                    )
                ).build().writeTo(this)
        }

        val actDirectory = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("ACT/").setSize(8192L).build(),
                    )
                ).build().writeTo(this)
        }

        val actFile = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("ASAMPL0.BPB").setSize(333L).build(),
                    )
                ).build().writeTo(this)
        }

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any())  } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        every { client.request(any<ByteArray>()) } returns Single.just(dateDirectories) andThen Single.just(actDirectory) andThen Single.just(actFile) andThen Single.just(actDirectory) andThen Single.just(actFile)

        val expectedPaths = mutableListOf("/U/0/20250101/ACT/ASAMPL0.BPB", "/U/0/20250202/ACT/ASAMPL0.BPB")
        // Act
        val testObserver = PolarFileUtils.getFileList(deviceId,"/U/0/", true, listener, "").test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(expectedPaths)
        verify(exactly = 1) { client.isServiceDiscovered }
        verify(exactly = 5) { client.request(any()) }
    }

    @Test
    fun testListFiles_Throws_When_NoSession() {
        // Arrange
        val deviceId = "E123456F"

        val listener = mockk<BleDeviceListener>()
        val sessions = mockk<Set<BleDeviceSession>>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns false

        // Act
        val testObserver = PolarFileUtils.getFileList(deviceId,"/U/0/", true, listener, "").test()

        // Assert
        testObserver.assertNotComplete()
        testObserver.assertError(PolarDeviceNotFound::class.java)
    }

    @Test
    fun testListFiles_Throws_When_FileSystemNotSupported() {
        // Arrange
        val deviceId = "E123456F"

        val (client, listener, session) = mockBleConnection(deviceId)
        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any())  } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.H10_FILE_SYSTEM
        every { session.polarDeviceType } returns "h10"

        // Act
        val testObserver = PolarFileUtils.getFileList(deviceId,"/U/0/", true, listener, "").test()

        // Assert
        testObserver.assertNotComplete()
        testObserver.assertError(PolarOperationNotSupported::class.java)
        verify(exactly = 1) { client.isServiceDiscovered }
        verify(exactly = 0) { client.request(any()) }
    }

    @Test
    fun testWriteFile() {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener) = mockBleConnection(deviceId)

        val proto = PbDailySummary.newBuilder()
            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
            .setActivityDistance(1234.56f)
            .setActivityCalories(100)
            .setBmrCalories(2000)
            .setTrainingCalories(500)
            .setActivityClassTimes(
                DailySummary.PbActivityClassTimes.newBuilder()
                    .setTimeLightActivity(PbDuration.newBuilder().setHours(5).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeSleep(PbDuration.newBuilder().setHours(8).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeSedentary(PbDuration.newBuilder().setHours(7).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeContinuousModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeContinuousVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeIntermittentModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeIntermittentVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeNonWear(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0) )
            )
            .setActivityGoalSummary(
                PbActivityGoalSummary.newBuilder()
                    .setActivityGoal(100f)
                    .setAchievedActivity(50f)
            )
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
            .build()
        val outputStream = ByteArrayOutputStream()
        proto.writeTo(outputStream)

        // Act
        val testObserver = PolarFileUtils.writeFile(deviceId,"/U/0/20000101/DSUM/", listener, outputStream.toByteArray(), "").test()

        val verifyWriteBuilder = PftpRequest.PbPFtpOperation.newBuilder()
        verifyWriteBuilder.command = PftpRequest.PbPFtpOperation.Command.PUT
        verifyWriteBuilder.path = "/U/0/20000101/DSUM/"

        // Assert
        testObserver.assertNoValues()
        verify(exactly = 1) { client.isServiceDiscovered }
        verify(exactly = 1) { client.write(verifyWriteBuilder.build().toByteArray(), any()) }
    }

    @Test
    fun testReadFile() {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener) = mockBleConnection(deviceId)

        val proto = PbDailySummary.newBuilder()
            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
            .setActivityDistance(1234.56f)
            .setActivityCalories(100)
            .setBmrCalories(2000)
            .setTrainingCalories(500)
            .setActivityClassTimes(
                DailySummary.PbActivityClassTimes.newBuilder()
                    .setTimeLightActivity(PbDuration.newBuilder().setHours(5).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeSleep(PbDuration.newBuilder().setHours(8).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeSedentary(PbDuration.newBuilder().setHours(7).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeContinuousModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeContinuousVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeIntermittentModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeIntermittentVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0) )
                    .setTimeNonWear(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0) )
            )
            .setActivityGoalSummary(
                PbActivityGoalSummary.newBuilder()
                    .setActivityGoal(100f)
                    .setAchievedActivity(50f)
            )
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
            .build()

        val outputStream = ByteArrayOutputStream()
        proto.writeTo(outputStream)
        every { client.request(any<ByteArray>()) } returns Single.just(outputStream)

        // Act
        val testObserver = PolarFileUtils.readFile(deviceId,"/U/0/20000101/DSUM/DSUM.BPB", listener, "").test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        Assert.assertEquals(true, proto.toByteArray().contentEquals(outputStream.toByteArray()))
        verify(exactly = 1) { client.isServiceDiscovered }
        verify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testReadFile_Throws_When_NoSession() {
        // Arrange
        val deviceId = "E123456F"

        val listener = mockk<BleDeviceListener>()
        val sessions = mockk<Set<BleDeviceSession>>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns false

        // Act
        val testObserver = PolarFileUtils.readFile(deviceId,"/U/0/", listener, "").test()

        // Assert
        testObserver.assertNotComplete()
        testObserver.assertError(PolarDeviceNotFound::class.java)
    }

    @Test
    fun testReadFile_Throws_When_NoFtpClient() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        val client = mockk<BlePsFtpClient>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns false

        // Act
        val testObserver = PolarFileUtils.readFile(deviceId,"/U/0/", listener, "").test()

        // Assert
        testObserver.assertNotComplete()
        testObserver.assertError(PolarServiceNotAvailable::class.java)
        verify(exactly = 0) { client.request(any()) }
    }

    @Test
    fun testReadFile_Throws_When_FtpRequestFails() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        val client = mockk<BlePsFtpClient>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS
        every { client.request(any()) } throws BlePsFtpUtils.PftpResponseError("All is lost!", 0)

        // Act & Assert
        try {
            PolarFileUtils.readFile(deviceId, "/U/0/", listener, "")
        } catch (e: Exception) {
            Assert.assertEquals(true, BlePsFtpUtils.PftpResponseError("All is lost!", 0).toString().contentEquals(e.toString()))
        }
        verify(exactly = 1) { client.isServiceDiscovered }
        verify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testRemoveSingleFile() {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener) = mockBleConnection(deviceId)

        val outputStream = ByteArrayOutputStream()
        every { client.request(any<ByteArray>()) } returns Single.just(outputStream)

        // Act
        val testObserver = PolarFileUtils.removeSingleFile(deviceId,"/U/0/20000101/DSUM/DSUM.BPB", listener, "").test()

        // Assert
        testObserver.assertValue(outputStream)
        verify(exactly = 1) { client.isServiceDiscovered }
        verify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testRemoveSingleFile_Throws_When_NoFtpClient() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        val client = mockk<BlePsFtpClient>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns false

        // Act
        val testObserver = PolarFileUtils.removeSingleFile(deviceId,"/U/0/", listener, "").test()

        // Assert
        testObserver.assertNotComplete()
        testObserver.assertError(PolarServiceNotAvailable::class.java)
        verify(exactly = 0) { client.request(any()) }
    }

    @Test
    fun testRemoveSingleFile_Throws_When_FtpRequestFails() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        val client = mockk<BlePsFtpClient>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS
        every { client.request(any()) } throws BlePsFtpUtils.PftpResponseError("All is lost!", 0)

        // Act & Assert
        try {
            PolarFileUtils.removeSingleFile(deviceId, "/U/0/", listener, "")
        } catch (e: Exception) {
            Assert.assertEquals(true, BlePsFtpUtils.PftpResponseError("All is lost!", 0).toString().contentEquals(e.toString()))
        }
        verify(exactly = 1) { client.isServiceDiscovered }
        verify(exactly = 1) { client.request(any()) }
    }

    private fun mockBleConnection(deviceId: String): Triple<BlePsFtpClient, BleDeviceListener, BleDeviceSession> {

        val client = mockk<BlePsFtpClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        return Triple(client, listener, session)
    }
}
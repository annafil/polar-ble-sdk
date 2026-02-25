// Copyright © 2024 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.*
import com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingData
import com.polar.sdk.api.errors.PolarOfflineRecordingError
import com.polar.sdk.api.model.*
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientOfflineHrDataToPolarHrData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientOfflineTemperatureDataToPolarTemperatureData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientPpgDataToPolarPpg
import com.polar.sdk.impl.utils.PolarDataUtils.mapPMDClientPpiDataToPolarPpiData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientAccDataToPolarAcc
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientGyroDataToPolarGyro
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientMagDataToPolarMagnetometer
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdClientSkinTemperatureDataToPolarTemperatureData
import com.polar.sdk.impl.utils.PolarDataUtils.mapPmdSettingsToPolarSettings
import java.time.LocalDateTime

/**
 * Drop-in replacement for OfflineRecordingAccumulator that appends samples
 * to a single mutable list instead of re-copying the entire list on every
 * sub-recording. Turns O(n²) accumulation into O(n).
 */
internal class EfficientOfflineAccumulator {

    // ACC
    private var accSamples: MutableList<PolarAccelerometerData.PolarAccelerometerDataSample>? = null
    private var accSettings: PolarSensorSetting? = null
    private var accStartTime: LocalDateTime? = null

    // GYRO
    private var gyroSamples: MutableList<PolarGyroData.PolarGyroDataSample>? = null
    private var gyroSettings: PolarSensorSetting? = null
    private var gyroStartTime: LocalDateTime? = null

    // MAG
    private var magSamples: MutableList<PolarMagnetometerData.PolarMagnetometerDataSample>? = null
    private var magSettings: PolarSensorSetting? = null
    private var magStartTime: LocalDateTime? = null

    // PPG
    private var ppgSamples: MutableList<PolarPpgData.PolarPpgSample>? = null
    private var ppgType: PolarPpgData.PpgDataType? = null
    private var ppgSettings: PolarSensorSetting? = null
    private var ppgStartTime: LocalDateTime? = null

    // PPI
    private var ppiSamples: MutableList<PolarPpiData.PolarPpiSample>? = null
    private var ppiStartTime: LocalDateTime? = null

    // HR
    private var hrSamples: MutableList<PolarHrData.PolarHrSample>? = null
    private var hrStartTime: LocalDateTime? = null

    // Temperature
    private var tempSamples: MutableList<PolarTemperatureData.PolarTemperatureDataSample>? = null
    private var tempStartTime: LocalDateTime? = null

    // Skin temperature
    private var skinTempSamples: MutableList<PolarTemperatureData.PolarTemperatureDataSample>? = null
    private var skinTempStartTime: LocalDateTime? = null

    fun accumulate(offlineRecData: OfflineRecordingData<*>) {
        when (val data = offlineRecData.data) {
            is AccData -> {
                val polar = mapPmdClientAccDataToPolarAcc(data)
                if (accSamples == null) {
                    accSamples = polar.samples.toMutableList()
                    accSettings = offlineRecData.recordingSettings?.let {
                        mapPmdSettingsToPolarSettings(it, fromSelected = false)
                    } ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Acc data is missing settings")
                    accStartTime = offlineRecData.startTime
                } else {
                    accSamples!!.addAll(polar.samples)
                }
            }

            is GyrData -> {
                val polar = mapPmdClientGyroDataToPolarGyro(data)
                if (gyroSamples == null) {
                    gyroSamples = polar.samples.toMutableList()
                    gyroSettings = offlineRecData.recordingSettings?.let {
                        mapPmdSettingsToPolarSettings(it, fromSelected = false)
                    } ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Gyro data is missing settings")
                    gyroStartTime = offlineRecData.startTime
                } else {
                    gyroSamples!!.addAll(polar.samples)
                }
            }

            is MagData -> {
                val polar = mapPmdClientMagDataToPolarMagnetometer(data)
                if (magSamples == null) {
                    magSamples = polar.samples.toMutableList()
                    magSettings = offlineRecData.recordingSettings?.let {
                        mapPmdSettingsToPolarSettings(it, fromSelected = false)
                    } ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Magnetometer data is missing settings")
                    magStartTime = offlineRecData.startTime
                } else {
                    magSamples!!.addAll(polar.samples)
                }
            }

            is PpgData -> {
                val polar = mapPMDClientPpgDataToPolarPpg(data)
                if (ppgSamples == null) {
                    ppgSamples = polar.samples.toMutableList()
                    ppgType = polar.type
                    ppgSettings = offlineRecData.recordingSettings?.let {
                        mapPmdSettingsToPolarSettings(it, fromSelected = false)
                    } ?: throw PolarOfflineRecordingError("getOfflineRecord failed. Ppg data is missing settings")
                    ppgStartTime = offlineRecData.startTime
                } else {
                    ppgSamples!!.addAll(polar.samples)
                    ppgType = polar.type
                }
            }

            is PpiData -> {
                val polar = mapPMDClientPpiDataToPolarPpiData(data)
                if (ppiSamples == null) {
                    ppiSamples = polar.samples.toMutableList()
                    ppiStartTime = offlineRecData.startTime
                } else {
                    ppiSamples!!.addAll(polar.samples)
                }
            }

            is OfflineHrData -> {
                val polar = mapPMDClientOfflineHrDataToPolarHrData(data)
                if (hrSamples == null) {
                    hrSamples = polar.samples.toMutableList()
                    hrStartTime = offlineRecData.startTime
                } else {
                    hrSamples!!.addAll(polar.samples)
                }
            }

            is TemperatureData -> {
                val polar = mapPMDClientOfflineTemperatureDataToPolarTemperatureData(data)
                if (tempSamples == null) {
                    tempSamples = polar.samples.toMutableList()
                    tempStartTime = offlineRecData.startTime
                } else {
                    tempSamples!!.addAll(polar.samples)
                }
            }

            is SkinTemperatureData -> {
                val polar = mapPmdClientSkinTemperatureDataToPolarTemperatureData(data)
                if (skinTempSamples == null) {
                    skinTempSamples = polar.samples.toMutableList()
                    skinTempStartTime = offlineRecData.startTime
                } else {
                    skinTempSamples!!.addAll(polar.samples)
                }
            }

            else -> throw PolarOfflineRecordingError("Data type is not supported.")
        }
    }

    fun getResult(): PolarOfflineRecordingData? {
        ppiSamples?.let {
            return PolarOfflineRecordingData.PpiOfflineRecording(PolarPpiData(it), ppiStartTime!!)
        }
        ppgSamples?.let {
            return PolarOfflineRecordingData.PpgOfflineRecording(PolarPpgData(it, ppgType!!), ppgStartTime!!, ppgSettings)
        }
        accSamples?.let {
            return PolarOfflineRecordingData.AccOfflineRecording(PolarAccelerometerData(it), accStartTime!!, accSettings!!)
        }
        gyroSamples?.let {
            return PolarOfflineRecordingData.GyroOfflineRecording(PolarGyroData(it), gyroStartTime!!, gyroSettings!!)
        }
        magSamples?.let {
            return PolarOfflineRecordingData.MagOfflineRecording(PolarMagnetometerData(it), magStartTime!!, magSettings)
        }
        hrSamples?.let {
            return PolarOfflineRecordingData.HrOfflineRecording(PolarHrData(it), hrStartTime!!)
        }
        tempSamples?.let {
            return PolarOfflineRecordingData.TemperatureOfflineRecording(PolarTemperatureData(it), tempStartTime!!)
        }
        skinTempSamples?.let {
            return PolarOfflineRecordingData.SkinTemperatureOfflineRecording(PolarTemperatureData(it), skinTempStartTime!!)
        }
        return null
    }
}

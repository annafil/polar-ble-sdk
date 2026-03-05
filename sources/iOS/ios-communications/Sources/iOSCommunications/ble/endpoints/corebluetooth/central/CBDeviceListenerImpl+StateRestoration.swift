
import Foundation
import CoreBluetooth

extension CBDeviceListenerImpl {

    /// Enable CoreBluetooth state restoration. Must be called before any
    /// BLE operations trigger the lazy `manager` property.
    public func enableStateRestoration(identifier: String) {
        manager = SDKCBCentralManager(
            delegate: self,
            queue: queueBle,
            options: [CBCentralManagerOptionRestoreIdentifierKey: identifier]
        )
    }

    public func centralManager(
        _ central: CBCentralManager,
        willRestoreState dict: [String: Any]
    ) {
        BleLogger.trace("willRestoreState called")
        guard let peripherals = dict[CBCentralManagerRestoredStatePeripheralsKey]
                as? [CBPeripheral] else { return }
        for peripheral in peripherals {
            handleDeviceDiscovered(
                central,
                didDiscover: peripheral,
                advertisementData: [:],
                rssi: -50
            )
        }
    }
}

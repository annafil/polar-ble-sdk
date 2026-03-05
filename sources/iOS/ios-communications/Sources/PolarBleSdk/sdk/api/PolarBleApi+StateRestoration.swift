import Foundation

extension PolarBleApiDefaultImpl {
    /// New instance of Polar Ble API implementation with CoreBluetooth state restoration enabled.
    ///
    /// - Parameter queue: context of where the API is used
    /// - Parameter features: set of SDK features to enable
    /// - Parameter restoreIdentifier: CoreBluetooth state restoration identifier
    /// - Returns: api instance with state restoration enabled
    public static func polarImplementation(
        _ queue: DispatchQueue,
        features: Set<PolarBleSdkFeature>,
        restoreIdentifier: String
    ) -> PolarBleApi {
        let impl = PolarBleApiImpl(queue, features: features)
        impl.listener.enableStateRestoration(identifier: restoreIdentifier)
        return impl
    }
}

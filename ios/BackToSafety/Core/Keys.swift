import Foundation

// The core exports enum keys and constants as functions; these give the call sites the
// properties and globals they read.

extension CountdownAlert { var key: String { countdownAlertKey(alert: self) } }
extension VehicleCheckKind { var key: String { vehicleCheckKindKey(kind: self) } }

/// constants/Mobility: the profile's mobility choices, and each one's translation key.
let MOBILITY_OPTIONS: [String] = mobilityOptions().map(\.label)
let MOBILITY_OPTION_KEYS: [String: String] = Dictionary(
    uniqueKeysWithValues: mobilityOptions().map { ($0.label, $0.key) }
)
extension DialTarget { var key: String { dialTargetKey(target: self) } }

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

/// The emergency checklist's step count (Home's "n of 11 steps").
let EMERGENCY_STEP_COUNT = Int(emergencyStepCount())

/// The database schema version (Settings shows it in developer mode).
let DATABASE_VERSION = Int(databaseVersion())

/// The profile fields edited as text, in the form's order.
let PROFILE_TEXT_FIELDS: [ProfileField] = profileTextFields()

/// The settings keys, shared with the Android app.
let settingKey = settingKeys()

extension ActiveEmergency {
    /// The start in epoch ms, parsed once from its ISO time; nil if it doesn't parse.
    var startedAtMs: Int64? { activeEmergencyStartedAtMs(emergency: self) }
}

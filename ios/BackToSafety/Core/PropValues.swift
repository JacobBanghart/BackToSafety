import CoreGraphics
import Foundation

/// Property bags between the app's Swift dictionaries (PostHog's `[String: Any]`) and the
/// Rust core's `PropValue`.
func propValues(_ properties: [String: Any]) -> [String: PropValue] {
    properties.mapValues(propValue)
}

func propValue(_ value: Any) -> PropValue {
    switch value {
    case let v as PropValue: return v
    case let v as Bool: return .bool(value: v)
    case let v as Int: return .integer(value: Int64(v))
    case let v as Int32: return .integer(value: Int64(v))
    case let v as Int64: return .integer(value: v)
    case let v as Double: return .decimal(value: v)
    case let v as Float: return .decimal(value: Double(v))
    case let v as CGFloat: return .decimal(value: Double(v))
    case let v as String: return .text(value: v)
    case is NSNull: return .null
    default:
        // An optional boxed in Any (nil, or a value the casts above didn't see through).
        let mirror = Mirror(reflecting: value)
        if mirror.displayStyle == .optional {
            return mirror.children.first.map { propValue($0.value) } ?? .null
        }
        return .text(value: String(describing: value))
    }
}

/// What PostHog takes. Nulls are dropped, as the app's sink always did.
func anyProperties(_ properties: [String: PropValue]) -> [String: Any] {
    var out: [String: Any] = [:]
    for (key, value) in properties {
        switch value {
        case .null: continue
        case let .bool(v): out[key] = v
        case let .integer(v): out[key] = v
        case let .decimal(v): out[key] = v
        case let .text(v): out[key] = v
        }
    }
    return out
}

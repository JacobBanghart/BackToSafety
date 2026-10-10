import Darwin
import Foundation
import Shared

/// app_ready, once per process: how long a cold start took, from the process starting to the
/// first frame of the first screen (home or welcome), the field measure of load time. With it,
/// how ready the app is for an emergency (`readiness`, gathered after the time is taken).
///
/// iOS can prewarm the process well before the user opens the app (ActivePrewarm=1); then
/// the start is when the app itself initialised, and the event says prewarmed.
@MainActor
enum AppReady {
    private static var reported = false
    private static let appInit = Date()

    /// Call from the app's init, so the prewarm fallback has its starting point.
    static func noteAppInit() { _ = appInit }

    static func report(readiness: @escaping () async -> [String: Any]) {
        guard !reported else { return }
        reported = true
        let prewarmed = ProcessInfo.processInfo.environment["ActivePrewarm"] == "1"
        let start = prewarmed ? appInit : (processStart() ?? appInit)
        let startupMs = Int(Date().timeIntervalSince(start) * 1000)
        Task {
            let properties = await readiness().merging(["startup_ms": startupMs, "prewarmed": prewarmed]) { _, timing in timing }
            Analytics.shared.track(event: .appReady, properties: properties)
        }
    }

    /// When the kernel started this process.
    private static func processStart() -> Date? {
        var info = kinfo_proc()
        var size = MemoryLayout<kinfo_proc>.stride
        var mib: [Int32] = [CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()]
        guard sysctl(&mib, u_int(mib.count), &info, &size, nil, 0) == 0 else { return nil }
        let started = info.kp_proc.p_un.__p_starttime
        return Date(timeIntervalSince1970: Double(started.tv_sec) + Double(started.tv_usec) / 1_000_000)
    }
}

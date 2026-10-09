import Foundation
import PostHog
import Shared

/// utils/posthog.ts: the same PostHog options as the RN app. The key comes from
/// EXPO_PUBLIC_POSTHOG_KEY at build time; without one (dev and test builds) nothing is set up
/// and every event goes nowhere.
func setUpAnalytics() {
    let info = Bundle.main.infoDictionary ?? [:]
    guard let key = info["PostHogKey"] as? String, !key.isEmpty else { return }
    let host = (info["PostHogHost"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "https://us.i.posthog.com"
    let config = PostHogConfig(apiKey: key, host: host)
    config.captureApplicationLifecycleEvents = true
    config.captureScreenViews = false // screens are reported by route, as the RN app does
    config.preloadFeatureFlags = true
    config.personProfiles = .identifiedOnly
    config.flushAt = 20
    config.flushIntervalSeconds = 10
    config.maxBatchSize = 100
    config.maxQueueSize = 1000
    config.sessionReplay = true
    config.sessionReplayConfig.maskAllTextInputs = true
    config.sessionReplayConfig.maskAllImages = true
    PostHogSDK.shared.setup(config)
    Analytics.shared.sink = { name, properties in
        if name == Analytics.shared.SCREEN {
            var props = properties
            let screen = props.removeValue(forKey: "$screen_name") as? String ?? ""
            PostHogSDK.shared.screen(screen, properties: props)
        } else {
            PostHogSDK.shared.capture(name, properties: properties)
        }
    }
}

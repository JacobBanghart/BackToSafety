import Foundation

/// Looks up a translated string: `t("key")` or `t("key", ["name": value])` (the core's
/// Translator, i18n.rs).
struct Translate {
    let translator: Translator
    func callAsFunction(_ key: String, _ vars: [String: Any] = [:]) -> String {
        vars.isEmpty ? translator.t(key: key) : translator.tv(key: key, vars: propValues(vars))
    }
}

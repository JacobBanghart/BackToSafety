//! The same i18n/locales JSON the RN app shipped, with the subset of i18next behavior the
//! app uses: nested keys by dots, English fallback (also for empty strings, as the RN app
//! set returnEmptyString: false), `{{var}}` interpolation and English/Spanish plurals.
//! A key found nowhere comes back as its defaultValue, or else the key itself.

use serde_json::{Map, Value};
use std::collections::HashMap;
use std::sync::{Arc, OnceLock};

/// Replaces `{{ name }}` placeholders (the pattern `\{\{[ \t]*([A-Za-z0-9_]+)[ \t]*\}\}`, by hand:
/// the regex crate cost 500 KB of the library). `value` gives a name's text; an unknown name is
/// left as written.
#[doc(hidden)]
pub fn interpolate(template: &str, mut value: impl FnMut(&str) -> Option<String>) -> String {
    let blank = |c: char| c == ' ' || c == '\t';
    let mut out = String::with_capacity(template.len());
    let mut pos = 0;
    while let Some(open) = template[pos..].find("{{").map(|i| pos + i) {
        out.push_str(&template[pos..open]);
        let inner = &template[open + 2..];
        let trimmed = inner.trim_start_matches(blank);
        let name_len = trimmed
            .find(|c: char| !(c.is_ascii_alphanumeric() || c == '_'))
            .unwrap_or(trimmed.len());
        let after_name = trimmed[name_len..].trim_start_matches(blank);
        if name_len > 0 && after_name.starts_with("}}") {
            let end = template.len() - after_name.len() + 2;
            match value(&trimmed[..name_len]) {
                Some(v) => out.push_str(&v),
                None => out.push_str(&template[open..end]),
            }
            pos = end;
        } else {
            out.push('{');
            pos = open + 1;
        }
    }
    out.push_str(&template[pos..]);
    out
}

/// Variables for a lookup. `count` picks the plural form, `ns` reads from another
/// namespace and `defaultValue` stands in for a missing key.
pub type Vars<'a> = &'a [(&'a str, Value)];

/// One namespace's JSON, parsed the first time a string is looked up in it: a launch reads
/// two or three of the fourteen files (the language and the screens shown), not all of them.
struct Namespace {
    text: String,
    parsed: OnceLock<Option<Map<String, Value>>>,
}

impl Namespace {
    fn get(&self) -> Option<&Map<String, Value>> {
        self.parsed
            .get_or_init(|| match serde_json::from_str(&self.text) {
                Ok(Value::Object(o)) => Some(o),
                _ => None,
            })
            .as_ref()
    }
}

/// locale -> namespace -> JSON (parsed lazily)
#[derive(Default, uniffi::Object)]
pub struct Translations {
    resources: HashMap<String, HashMap<String, Namespace>>,
}

impl Translations {
    /// From parsed JSON.
    pub fn new(resources: HashMap<String, HashMap<String, Map<String, Value>>>) -> Self {
        Self {
            resources: resources
                .into_iter()
                .map(|(locale, namespaces)| {
                    let namespaces = namespaces
                        .into_iter()
                        .map(|(ns, map)| {
                            let parsed = OnceLock::new();
                            let _ = parsed.set(Some(map));
                            (
                                ns,
                                Namespace {
                                    text: String::new(),
                                    parsed,
                                },
                            )
                        })
                        .collect();
                    (locale, namespaces)
                })
                .collect(),
        }
    }

    /// From raw locale JSON (locale -> namespace -> text). Text that isn't a JSON object gives
    /// a namespace with no strings.
    pub fn from_json<'a>(files: impl IntoIterator<Item = (&'a str, &'a str, &'a str)>) -> Self {
        let mut resources: HashMap<String, HashMap<String, Namespace>> = HashMap::new();
        for (locale, ns, text) in files {
            resources.entry(locale.into()).or_default().insert(
                ns.into(),
                Namespace {
                    text: text.into(),
                    parsed: OnceLock::new(),
                },
            );
        }
        Self { resources }
    }

    pub fn translator(self: &Arc<Self>, locale: &str, namespace: &str) -> Translator {
        Translator {
            translations: self.clone(),
            locale: locale.into(),
            namespace: namespace.into(),
        }
    }

    fn lookup(&self, locale: &str, ns: &str, key: &str) -> Option<&str> {
        let mut node = self.resources.get(locale)?.get(ns)?.get()?;
        let mut parts = key.split('.').peekable();
        while let Some(part) = parts.next() {
            let next = node.get(part)?;
            if parts.peek().is_none() {
                return next.as_str().filter(|s| !s.is_empty());
            }
            node = next.as_object()?;
        }
        None
    }
}

/// `t` bound to a locale and a default namespace.
#[derive(Clone, uniffi::Object)]
pub struct Translator {
    translations: Arc<Translations>,
    pub locale: String,
    namespace: String,
}

const FALLBACK: &str = "en";

impl Translator {
    pub fn t(&self, key: &str) -> String {
        self.tv(key, &[])
    }

    pub fn tv(&self, key: &str, vars: Vars) -> String {
        let var = |name: &str| vars.iter().find(|(k, _)| *k == name).map(|(_, v)| v);
        let ns = var("ns").and_then(Value::as_str).unwrap_or(&self.namespace);
        let key = self.plural_key(ns, key, var("count"));
        let tr = &self.translations;
        let template = tr
            .lookup(&self.locale, ns, &key)
            .or_else(|| tr.lookup(FALLBACK, ns, &key))
            .or_else(|| var("defaultValue").and_then(Value::as_str))
            .unwrap_or(&key);
        interpolate(template, |name| var(name).map(format_value))
    }

    fn plural_key(&self, ns: &str, key: &str, count: Option<&Value>) -> String {
        let Some(n) = count.and_then(Value::as_f64) else {
            return key.to_string();
        };
        // English and Spanish: one for exactly 1, other otherwise.
        let form = format!("{key}_{}", if n == 1.0 { "one" } else { "other" });
        let tr = &self.translations;
        if tr.lookup(&self.locale, ns, &form).is_some() || tr.lookup(FALLBACK, ns, &form).is_some()
        {
            form
        } else {
            key.to_string()
        }
    }
}

fn format_value(v: &Value) -> String {
    match v {
        Value::Null => String::new(),
        Value::String(s) => s.clone(),
        Value::Number(n) => match n.as_f64() {
            Some(f) if n.is_f64() && f.fract() == 0.0 => (f as i64).to_string(),
            _ => n.to_string(),
        },
        other => other.to_string(),
    }
}

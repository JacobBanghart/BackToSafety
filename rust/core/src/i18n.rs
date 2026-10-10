//! The same i18n/locales JSON the RN app shipped, with the subset of i18next behavior the
//! app uses: nested keys by dots, English fallback (also for empty strings, as the RN app
//! set returnEmptyString: false), `{{var}}` interpolation and English/Spanish plurals.
//! A key found nowhere comes back as its defaultValue, or else the key itself.

use regex::{Captures, Regex};
use serde_json::{Map, Value};
use std::collections::HashMap;
use std::sync::{Arc, LazyLock};

static PLACEHOLDER: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"\{\{[ \t]*([A-Za-z0-9_]+)[ \t]*\}\}").unwrap());

/// Variables for a lookup. `count` picks the plural form, `ns` reads from another
/// namespace and `defaultValue` stands in for a missing key.
pub type Vars<'a> = &'a [(&'a str, Value)];

/// locale -> namespace -> parsed JSON
#[derive(Default)]
pub struct Translations {
    resources: HashMap<String, HashMap<String, Map<String, Value>>>,
}

impl Translations {
    pub fn new(resources: HashMap<String, HashMap<String, Map<String, Value>>>) -> Self {
        Self { resources }
    }

    /// From raw locale JSON (locale -> namespace -> text).
    pub fn from_json<'a>(files: impl IntoIterator<Item = (&'a str, &'a str, &'a str)>) -> Self {
        let mut resources: HashMap<String, HashMap<String, Map<String, Value>>> = HashMap::new();
        for (locale, ns, text) in files {
            if let Ok(Value::Object(o)) = serde_json::from_str(text) {
                resources
                    .entry(locale.into())
                    .or_default()
                    .insert(ns.into(), o);
            }
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
        let mut node = self.resources.get(locale)?.get(ns)?;
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
#[derive(Clone)]
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
        PLACEHOLDER
            .replace_all(template, |c: &Captures| match var(&c[1]) {
                Some(v) => format_value(v),
                None => c[0].to_string(),
            })
            .into_owned()
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

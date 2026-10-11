//! UniFFI's recommended per-workspace bindgen binary (library mode):
//! `uniffi-bindgen generate --library <libnijii_core.so> --language kotlin --out-dir <dir>`.

fn main() {
    uniffi::uniffi_bindgen_main()
}

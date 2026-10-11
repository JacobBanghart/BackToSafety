/// The file name at the end of a stored photo URI, or None if there isn't one.
/// Readers resolve the photo by this name in the current documents directory, since
/// iOS moves the app container on updates (spec/storage.md, F-21).
pub fn photo_file_name(uri: &str) -> Option<String> {
    let path = uri.split(['?', '#']).next().unwrap_or("");
    let name = &path[path.rfind('/').map_or(0, |i| i + 1)..];
    (!name.is_empty()).then(|| percent_decode(name))
}

/// decodeURIComponent: %XX escapes are UTF-8 bytes; anything malformed is left as is.
fn percent_decode(value: &str) -> String {
    let chars: Vec<char> = value.chars().collect();
    let mut bytes = Vec::new();
    let mut i = 0;
    while i < chars.len() {
        if chars[i] == '%' && i + 2 < chars.len() {
            let hex: String = chars[i + 1..i + 3].iter().collect();
            if let Ok(b) = u8::from_str_radix(&hex, 16) {
                bytes.push(b);
                i += 3;
                continue;
            }
        }
        let mut buf = [0; 4];
        bytes.extend_from_slice(chars[i].encode_utf8(&mut buf).as_bytes());
        i += 1;
    }
    String::from_utf8_lossy(&bytes).into_owned()
}

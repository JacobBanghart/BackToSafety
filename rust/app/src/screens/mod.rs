//! One module (and one CSS file) per area, each owned by one lane (rust/PORT_PLAN.md).

pub mod contacts;
pub mod destinations;
pub mod emergency;
pub mod home;
pub mod onboarding;
pub mod profile;
pub mod readout;
pub mod settings;

/// Every screen's CSS, in one stylesheet.
pub const CSS: &[&str] = &[
    contacts::CSS,
    destinations::CSS,
    emergency::CSS,
    home::CSS,
    onboarding::CSS,
    profile::CSS,
    readout::CSS,
    settings::CSS,
];

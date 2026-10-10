//! Platform services (Android via JNI; iOS later): the twin of what androidApp's Activity and
//! iosApp's App provide. Owned by the shell lane; other lanes add their own files here.

pub mod posthog;

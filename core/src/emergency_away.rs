//! The countdown alerts on and off the emergency screen. On the screen, its countdown tick
//! gives them in-app ([`EmergencyAway::in_app_alerts`]). Off the screen (the app in the
//! background, or another screen showing), they're scheduled as notifications
//! ([`EmergencyAway::left`]). Back on the screen, an alert that came due while away is caught
//! up ([`EmergencyAway::shown`]).
//!
//! Both apps drive this from the screen's lifecycle and do nothing but deliver what it decides,
//! so they can't drift apart. The time away starts at the last tick, not at leaving: an alert
//! due between that tick and leaving is caught up instead of lost. Once the countdown has
//! expired the ticks stop mattering, and it starts at leaving.

use crate::alerts::{
    AlertDelivery, ScheduledAlert, alert_due_at_ms, alerts_to_schedule, catch_up_alert,
    countdown_alert_properties, emergency_resumed_properties,
};
use crate::analytics::{Analytics, AnalyticsEvent};
use crate::emergency::{CountdownAlert, countdown_alerts};
use crate::store::{Result, Store};
use serde_json::json;
use std::sync::{Arc, Mutex};

/// Puts countdown alerts on the phone as local notifications. Each app has one: on Android a
/// timer under the emergency's foreground service, with alarms as the backstop; on iOS,
/// UNUserNotificationCenter.
#[uniffi::export(with_foreign)]
pub trait AlertScheduler: Send + Sync {
    /// Replaces whatever is scheduled with `alerts`.
    fn schedule(&self, alerts: Vec<ScheduledAlert>);

    fn cancel_all(&self);
}

#[derive(uniffi::Object)]
pub struct EmergencyAway {
    store: Arc<Store>,
    scheduler: Arc<dyn AlertScheduler>,
    lock: Mutex<()>,
    last_tick_ms: Mutex<Option<i64>>,
}

impl EmergencyAway {
    pub fn new(store: Arc<Store>, scheduler: Arc<dyn AlertScheduler>) -> Self {
        EmergencyAway {
            store,
            scheduler,
            lock: Mutex::new(()),
            last_tick_ms: Mutex::new(None),
        }
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, ()> {
        self.lock.lock().unwrap_or_else(|e| e.into_inner())
    }

    fn set_last_tick(&self, ms: Option<i64>) {
        *self.last_tick_ms.lock().unwrap_or_else(|e| e.into_inner()) = ms;
    }

    fn last_tick(&self) -> Option<i64> {
        *self.last_tick_ms.lock().unwrap_or_else(|e| e.into_inner())
    }

    /// The screen's countdown moved from `prev_seconds_left` to `next_seconds_left` at
    /// `now_ms`: the alerts to give now (vibration), reported as countdown_alert.
    pub fn in_app_alerts(
        &self,
        prev_seconds_left: i64,
        next_seconds_left: i64,
        now_ms: i64,
    ) -> Vec<CountdownAlert> {
        self.set_last_tick(Some(now_ms));
        let alerts = countdown_alerts(prev_seconds_left, next_seconds_left);
        for alert in &alerts {
            Analytics::track(
                AnalyticsEvent::CountdownAlert,
                countdown_alert_properties(*alert, AlertDelivery::InApp),
            );
        }
        alerts
    }

    /// The emergency screen stopped showing at `now_ms`. If an emergency is running, notes when
    /// the time away began (unless already away) and schedules the alerts still to come.
    pub fn left(&self, now_ms: i64) -> Result<()> {
        let _guard = self.lock();
        let Some(started) = self
            .store
            .active_emergency()?
            .and_then(|e| e.started_at_ms())
        else {
            return Ok(());
        };
        if self
            .store
            .setting(Store::EMERGENCY_AWAY_FROM)?
            .is_none_or(|v| v.is_empty())
        {
            // The apps stop ticking at expiry, so a tick from then or later says nothing about
            // when the screen was left: the expiry alert was already shown in-app, and nothing
            // is left to catch up.
            let tick_before_expiry = self
                .last_tick()
                .filter(|&tick| tick < alert_due_at_ms(CountdownAlert::Expired, started));
            self.store.put_setting(
                Store::EMERGENCY_AWAY_FROM,
                &tick_before_expiry.unwrap_or(now_ms).to_string(),
            )?;
        }
        self.scheduler.schedule(alerts_to_schedule(started, now_ms));
        Ok(())
    }

    /// The emergency screen is showing at `now_ms`: cancels the scheduled alerts, reports the
    /// time away (emergency_resumed) and returns the alert to catch up on, if one came due.
    pub fn shown(&self, now_ms: i64) -> Result<Option<CountdownAlert>> {
        let _guard = self.lock();
        self.scheduler.cancel_all();
        self.set_last_tick(Some(now_ms));
        let away_from = self
            .store
            .setting(Store::EMERGENCY_AWAY_FROM)?
            .and_then(|v| v.parse::<i64>().ok());
        if away_from.is_some() {
            self.store.put_setting(Store::EMERGENCY_AWAY_FROM, "")?;
        }
        let started = self
            .store
            .active_emergency()?
            .and_then(|e| e.started_at_ms());
        let (Some(away_from), Some(started)) = (away_from, started) else {
            return Ok(None);
        };
        let alert = catch_up_alert(started, away_from, now_ms);
        Analytics::track(
            AnalyticsEvent::EmergencyResumed,
            emergency_resumed_properties(away_from, now_ms, alert),
        );
        if let Some(alert) = alert {
            Analytics::track(
                AnalyticsEvent::CountdownAlert,
                countdown_alert_properties(alert, AlertDelivery::CatchUp),
            );
        }
        Ok(alert)
    }

    /// The phone restarted at `now_ms` (Android's boot receiver): the scheduled alerts and the
    /// notifications already shown went with it, and the screen isn't showing. Schedules the
    /// alerts still to come, as [`EmergencyAway::left`] does, and returns the one to post now:
    /// the most serious that came due while away, as [`EmergencyAway::shown`] would catch it up
    /// (it still will, on returning).
    ///
    /// Without a time away the screen was showing when the phone went off, at a moment nobody
    /// noted, so the time away counts from the emergency's start: an alert given twice beats a
    /// call-911 alert that never came.
    pub fn restarted(&self, now_ms: i64) -> Result<Option<CountdownAlert>> {
        let _guard = self.lock();
        let Some(started) = self
            .store
            .active_emergency()?
            .and_then(|e| e.started_at_ms())
        else {
            return Ok(None);
        };
        let away_from = match self
            .store
            .setting(Store::EMERGENCY_AWAY_FROM)?
            .and_then(|v| v.parse::<i64>().ok())
        {
            Some(away_from) => away_from,
            None => {
                self.store
                    .put_setting(Store::EMERGENCY_AWAY_FROM, &started.to_string())?;
                started
            }
        };
        self.scheduler.schedule(alerts_to_schedule(started, now_ms));
        Ok(catch_up_alert(started, away_from, now_ms))
    }

    /// The emergency ended (found, ended, or the account deleted): nothing more to deliver.
    pub fn ended(&self) -> Result<()> {
        let _guard = self.lock();
        self.scheduler.cancel_all();
        self.set_last_tick(None);
        self.store.put_setting(Store::EMERGENCY_AWAY_FROM, "")
    }

    /// Whether to ask for notification permission: once, after onboarding.
    pub fn should_ask_for_notifications(&self) -> Result<bool> {
        Ok(self
            .store
            .setting(Store::NOTIFICATIONS_PROMPTED)?
            .is_none_or(|v| v.is_empty()))
    }

    /// The answer to the notification permission prompt (notifications_permission).
    pub fn notifications_answered(&self, granted: bool) -> Result<()> {
        self.store.put_setting(Store::NOTIFICATIONS_PROMPTED, "1")?;
        let mut p = crate::alerts::Properties::new();
        p.insert("granted".into(), json!(granted));
        Analytics::track(AnalyticsEvent::NotificationsPermission, p);
        Ok(())
    }
}

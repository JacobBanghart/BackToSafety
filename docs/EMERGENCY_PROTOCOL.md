# Emergency Protocol

This document describes the 15-minute guided search protocol implemented in `app/emergency.tsx`.

---

## 1. Purpose

When a vulnerable adult goes missing, the app guides the caregiver through a structured search and escalation process. The protocol is designed to:

- Keep the caregiver focused and moving.
- Capture time-critical information (clothing, last seen).
- Notify emergency contacts quickly.
- Surface likely destinations and high-risk areas.
- Provide a pre-built 911 readout and one-tap dial.

---

## 2. Protocol Duration

```ts
const SEARCH_WINDOW_SECONDS = 15 * 60; // 15 minutes
```

- A new emergency starts a 15-minute countdown.
- The caregiver can resume an active emergency if the app is reopened within the window.
- After 15 minutes the timer expires; the screen still shows the checklist but the emergency is visually marked as expired.

---

## 3. Steps

Steps are created by `buildInitialSteps()` and rendered sequentially. Steps marked `urgent: true` are highlighted specially.

| #   | ID                  | Title                           | Urgent |
| --- | ------------------- | ------------------------------- | ------ |
| 1   | `home_search`       | Search home thoroughly          |        |
| 2   | `outside_immediate` | Check outside areas             |        |
| 3   | `neighbors`         | Alert neighbors                 |        |
| 4   | `radius_search`     | Search 1–1.5 mile radius        |        |
| 5   | `high_risk`         | Check high-risk areas first     | ✅     |
| 6   | `familiar_places`   | Search familiar places          |        |
| 7   | `call_911`          | Call 911                        | ✅     |
| 8   | `silver_alert`      | Request Silver/Feather Alert    |        |
| 9   | `share_info`        | Share relevant personal details |        |
| 10  | `coordinate`        | Coordinate search efforts       |        |
| 11  | `document`          | Document everything             |        |

The `call_911` title interpolates the local emergency number (`{{emergencyNumber}}`), which is `911` in the English locale.

---

## 4. Persisted State

Active emergencies are persisted to the `settings` table under key `active_emergency`:

```ts
type EmergencyState = {
  startedAt: string; // ISO timestamp
  wearing: string; // Clothing description
  checkedSteps: string[]; // Array of completed step IDs
  isActive: boolean; // Whether the protocol is still running
};
```

This lets the protocol survive app termination, phone calls, and when the user briefly leaves the screen.

### State lifecycle

1. **Start**: tapping "Start Emergency Search" creates a new `EmergencyState` with `startedAt = now`, empty `checkedSteps`, and `isActive = true`.
2. **Resume**: returning to `/emergency` within `SEARCH_WINDOW_SECONDS` reloads the state and recalculates remaining time.
3. **Completion**: tapping "Found — Safe" sets `isActive = false`, clears the active emergency setting, and creates an incident with outcome `found`.
4. **Cancellation/timeout**: the timer can expire with the protocol still active; the screen continues to display the checklist but marks expiration.

---

## 5. Timer Behavior

- Counts down from 15:00.
- Derived from `startedAt` each time the screen mounts, so it stays accurate across app restarts.
- Provides visual progress indication.

### Threshold feedback

| Condition            | Feedback                                                |
| -------------------- | ------------------------------------------------------- |
| New emergency starts | `Warning` haptic                                        |
| Step checked         | `Medium` impact haptic                                  |
| Step unchecked       | `Light` impact haptic                                   |
| 911 button pressed   | `Heavy` impact haptic                                   |
| Timer reaches ≤ 0    | `Error` haptic + vibration pattern `[0, 500, 200, 500]` |
| 5 minutes remaining  | `Warning` haptic                                        |

---

## 6. clothing / Appearance Input

A "What were they wearing?" card captures clothing details. The value is persisted to `EmergencyState.wearing` and surfaced in the 911 readout.

---

## 7. Dominant-Hand Direction Hint

If the profile's `dominantHand` is `left` or `right`, the app shows a directional hint indicating the tendency to veer (e.g., may veer left or right). This is based on the common observation that wandering individuals may drift toward their dominant side.

---

## 8. Familiar Destinations

When the caregiver reaches the `familiar_places` step, saved destinations are surfaced:

- Destinations are shown with name, category, and risk level.
- High-risk destinations are visually emphasized.
- Water-category destinations show a special warning.
- Tapping a destination opens it in the platform maps app.

---

## 9. Emergency Actions

### 911 call

- One-tap button opens the dialer with `tel:911`.
- Marks the `call_911` step complete.
- Records an incident with outcome `911_called`.
- Heavy haptic feedback confirms the action.

### Alert emergency contacts (SMS)

- Composes an SMS to contacts with `notifyOnEmergency = true`.
- Native: uses `expo-sms`.
- Web fallback: uses an `sms:` URL.
- Includes the missing person's name, last-seen information, and a request to help search.

### Info Sheet

- Navigates to `/readout` for a pre-written 911 script and full details.

### Found — Safe

- Resolves the emergency:
  - Sets `isActive = false`.
  - Clears `active_emergency` from settings.
  - Creates an incident with outcome `found`.
  - Returns the user to the home screen with the emergency cleared.

---

## 10. Incidents Created from the Protocol

The emergency screen writes incident records into the `incidents` table:

| Trigger           | Outcome      | Notes                                        |
| ----------------- | ------------ | -------------------------------------------- |
| "Call 911" button | `911_called` | Records that professional help was requested |
| "Found — Safe"    | `found`      | Marks the incident resolved                  |

Fields captured include `startedAt`, `wearing`, `areasChecked` (completed step IDs), and any available last-seen coordinates.

The incidents module also exposes `getIncidentPatterns()` for aggregate statistics, but the app currently has no incident-history or insights screen.

---

## 11. Last Seen

The caregiver can record when/where the person was last seen. This information is stored in `ProfileContext.lastSeen` and consumed by `/readout` to provide coordinates and timestamps to dispatch.

---

## 12. Accessibility & Safety Notes

- Haptics are triggered only on native platforms; web receives no tactile feedback.
- The dialer and SMS composer require appropriate native permissions (phone/SMS) or web `tel:`/`sms:` URL support.
- The protocol does **not** use or request GPS location automatically; Android permissions for location are explicitly blocked in `app.json`. Location is entered manually by the caregiver.

---
applyTo: "**"
---

### North star

Reduce time-to-recovery when someone with dementia wanders by giving caregivers clear, step-by-step actions, the right info at hand, and fast handoffs to neighbors/911.

### Primary users

Caregivers (family, home-health, facilities)

Inner circle (neighbors/friends)

First responders (info handoff, not a full user)

### Critical workflows (MVP)

Emergency “Wandering” button

#mvp One tap → starts a 15-minute guided search timer (most guidance says call 911 if not found within ~15 minutes). The app walks the caregiver through nearby checks (yard, usual routes, brush/tree lines), then prompts to call 911 at timer end.
Cleveland Clinic
eastonad.ucla.edu

#mvp Auto-assemble a read-out for dispatch: name, age, appearance, medical info, meds, cognitive status, triggers/soothers, last seen time & precise GPS, recent photo, and likely destinations. (Alzheimer’s/NIA emphasize preparation, photos, environment cues.)
National Institute on Aging
Alzheimer’s Association

Home/safe-zone geofences with escalating notifications (device vibra → caregiver ping → call tree) to avoid alarm fatigue.

#mvp Quiet-approach tips displayed to helpers (speak slowly, simple sentences, give time, look for medical ID).
The New Yorker

State-by-state guidance: eligibility and where these exist (Silver Alerts vary by state; some states have other colors like Purple Alert for IDD). Provide links/scripts to ask LE about an alert.
Careforth
ACS Home Care
CT Insider

Medical ID & registry integrations

Encourage/track enrollment in MedicAlert + Safe Return (24/7 response center & ID). Provide “activate Safe Return” button with instructions, member ID, and hotline.
alzbr.org
medicalert.org

Optional Project Lifesaver field (if the locality supports RF tracking wristbands) so responders see the tag ID/frequency.
Project Lifesaver
Somerset County

Prevention & routine

Checklists for door/window alarms, pressure mats, ID wear, locks/signage, securing keys, yard fencing, and night-wandering mitigations—pulled into simple habit trackers.
Alzheimer’s Association
National Institute on Aging
UPMC | Life Changing Medicine

The “Wandering” flow (what the app shows)

Start → shows last known location & “quick search” tiles (yard → street → usual route → past workplaces/faith/coffee shop). Includes “dominant-hand bias” hint for direction if caregiver has noted it.
Our Parents

Timer (15:00) with nearby-risk prompts (water, brush, fences) and “mark checked.”
eastonad.ucla.edu

Not found → 911 script + tap-to-call + on-screen facts; prompt to ask about issuing an alert (local policy).
Cleveland Clinic
Careforth

Notify circle (SMS link with photo & do/don’t tips).
The New Yorker

Found → de-escalation tips, rehydration, record incident (time, location, trigger) to refine prevention.

Data model (lean)

Person: demographics, photo, meds, conditions, triggers/soothers, dominant hand, mobility, devices (phone/watch/GPS/RF), IDs (MedicAlert, Project Lifesaver).

Places: home, geofences, “likely destinations.”

Contacts: roles, notification preferences, pre-approved info sharing.

Incidents: timeline, locations, actions taken, outcome.

Safety, consent & privacy

Treat this as PHI-adjacent: end-to-end encryption in transit, strong at-rest encryption, role-based sharing, audit log, quick “panic hide” on lock screen.

Explicit consents for: GPS sharing with circle, sending medical details via SMS, sharing with responders.

Offline mode for the first 10 minutes (cached profile & photo), with queued notifications when back online.

Clear limits: geofencing isn’t restraint; respect autonomy; include dignity-first copy (e.g., “Let’s help them get back to a familiar place”).
The New Yorker

Integrations & hardware

Phone/Watch: background location + fallbacks (low-battery last ping).

Wearables/IDs: store member numbers and instructions (MedicAlert hotline & Safe Return steps).
medicalert.org

Smart home hooks: door sensors, chimes, and cameras to enrich last-seen data. (Consistent with prevention tips recommending bells/alarms.)
Alzheimer’s Association

Content you can ship day one

911 script: “I’m reporting a missing vulnerable adult with dementia. Last seen at [time, address]. Wearing [clothes]. Risks: [medical]. Photo and MedicAlert ID available. Please advise on local Silver Alert.”
Cleveland Clinic

Neighbor card (shareable PDF): photo, how to help, your phone, “approach calmly; short sentences; offer to call me.”
The New Yorker

Home checklist (locks, chimes, ID, door mats, fencing, window limiters).
National Institute on Aging

Rollout plan

MVP (4–6 weeks): Person profile, geofence + alerts, emergency timer, 911 script, SMS call tree, incident log, checklists.

v1.1: State program guide, MedicAlert fields, neighbor card generator.

v1.2: Smart-home integrations, multi-caregiver roles, quiet hours/night mode.

v1.3: Facility mode (multiple residents, elopement reports), export for care teams.

Success metrics

Median time-to-call-911 after disappearance.

Time-to-recovery from last-seen to found.

% users with complete profiles (photo, likely places, IDs).

Prevention adherence (checklist completion).

If you want, I can sketch wireframes for the emergency flow and the caregiver profile next, or draft the 911/neighbor message templates for you to drop straight into the app.

### Other thoughts

Tracking devices may be located on the person but we most likely wont have their phone or a tracker

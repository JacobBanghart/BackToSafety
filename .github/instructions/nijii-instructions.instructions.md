---
applyTo: '**'
---

# Nijii App Development Instructions

## North Star

Reduce time-to-recovery when someone with dementia wanders by giving caregivers clear, step-by-step actions, the right info at hand, and fast handoffs to neighbors/911.

## Branding

- **Organization**: Nijii (nijii.org)
- **Primary Color**: Deep Purple `#3f2875` (900 on Material scale)
- **Secondary Color**: Gold/Cream `#e3d895` (100 on Material scale)
- See `constants/Colors.ts` for full palette

## Primary Users

1. **Caregivers** (family, home-health, facilities)
2. **Inner circle** (neighbors/friends)
3. **First responders** (info handoff, not a full user)

---

## Build Plan (Priority Order)

### Phase 1: Core Profile & Emergency Flow (MVP) ✅ Partially Complete

#### 1.1 Person Profile Screen

Create a profile management screen with these fields from the Emergency ID Sheet:

**Personal Info**

- [ ] Name (required)
- [ ] Nickname/preferred name
- [ ] Date of birth / Age
- [ ] Photo (recent, updated regularly)
- [ ] Physical description (height, weight, hair color, eye color)
- [ ] Identifying marks, tattoos, scars

**Medical & Behavioral**

- [ ] Medical conditions
- [ ] Medications (current)
- [ ] Allergies or dietary restrictions
- [ ] Cognitive status
- [ ] Dominant hand (for direction bias hint)
- [ ] Mobility level

**Communication & De-escalation**

- [ ] Preferred communication (speaking/non-speaking, visuals, sign language)
- [ ] What escalation looks like (crying, running, rocking, aggression)
- [ ] De-escalation techniques (what has helped in the past)
- [ ] Best way to approach
- [ ] Likes (favorite toys, characters, songs, TV shows)
- [ ] Dislikes/triggers (fears, sensitivities, noises, flashing lights)
- [ ] Family safe word (identifier to indicate you are a safe person)

**Devices & IDs**

- [ ] Locative device info (GPS tracker, Apple AirTag, etc.)
- [ ] ID bracelets (MedicAlert, Project Lifesaver, etc.)
- [ ] MedicAlert member ID & hotline

#### 1.2 Emergency Contacts

- [ ] Multiple contacts with roles (primary caregiver, neighbor, family)
- [ ] Phone numbers
- [ ] Addresses
- [ ] Notification preferences
- [ ] Pre-approved info sharing consent

#### 1.3 Likely Destinations

- [ ] Home address
- [ ] Specific locations they may wander to (water/pool/lake, movies, gas station, former workplace, church, favorite walking route)
- [ ] Distance from home
- [ ] Why they might go there (memory association)

#### 1.4 Emergency Timer Flow ✅ Exists - Enhance

Current: Basic 15-min timer with checklist
Enhance with 12-step protocol from reference docs:

1. Start timer (15 minutes)
2. Search home & immediate surroundings (closets, under beds, bathrooms, garage, basement, sheds - people seek small quiet spaces)
3. Activate tracking programs (Project Lifesaver, GPS device)
4. Organize coordinated search (1-1.5 mile radius - most found within this distance)
5. Check outside areas (yard, paths, driveways, vehicles)
6. Alert neighbors/bystanders (show photo, ask to call if seen)
7. Call 911 when timer expires
8. Look toward high-risk areas (water, wooded areas, ditches, busy roads)
9. Search common/familiar places (church, former home, workplace, favorite route)
10. Activate Silver Alert or Feather Alert program
11. Provide medical/behavioral info to responders
12. Keep documentation of search efforts (time, areas checked, people contacted)

#### 1.5 911 Read-out Screen ✅ Exists - Enhance

Current: Basic script and copy functionality
Add fields for:

- [ ] What they were wearing (prompt to fill in)
- [ ] Silver Alert / Feather Alert guidance
- [ ] One-tap call with script visible
- [ ] Share via SMS to inner circle

---

### Phase 2: Neighbor & Circle Features

#### 2.1 Wandering Letter Generator

From reference doc - generate shareable letter for neighbors:

- Recipient name field
- Auto-fill from profile (name, age, height, weight)
- Explain dementia wandering
- "If you see [NAME] unsupervised, please stay with them and call me"
- Contact information
- Additional helpful info
- Export as PDF or shareable text

#### 2.2 Neighbor Card (Quick Share)

One-page shareable card:

- Photo
- Name
- How to help
- Caregiver phone
- "Approach calmly; short sentences; offer to call me"
- Safe word (optional)

#### 2.3 SMS Circle Alert

When emergency starts:

- [ ] One-tap notify all contacts
- [ ] Include photo link
- [ ] Include do/don't approach tips
- [ ] Include last known location

---

### Phase 3: Prevention & Planning

#### 3.1 Safety Checklist (from Toolkit)

**At Home**

- [ ] Visual supports at eye level at all doors (stop sign images)
- [ ] Move locks to high-to-reach locations
- [ ] Install door chimes for auditory support
- [ ] Geofence setup (if using locative technology)
- [ ] Physical boundaries (fencing, locked gates)

**Away From Home**

- [ ] Alert caregivers/staff of wandering behavior
- [ ] Create approved zoning/safety plan for school/frequent locations
- [ ] Introduce loved one to first responders and neighbors
- [ ] Evaluate locative technology options

**Building Foundation**

- [ ] Social stories for safe interactions
- [ ] Swimming and water safety classes enrollment
- [ ] Make safety everyone's responsibility

#### 3.2 Wandering Emergency Plan Setup

Four-phase approach from docs:

1. **Identify/Assess** - Document that individual wanders, identify triggers
2. **Make a Plan** - Who calls 911, who alerts neighbors, likely destinations, search order
3. **Implement** - Practice the plan, check water first
4. **Fine Tune** - Monitor for changes, update favorite spots

#### 3.3 Incident Log ✅ Exists

- [ ] Enhanced with: location found, trigger identified, weather, time of day
- [ ] Pattern analysis over time

---

### Phase 4: Advanced Features

#### 4.1 State Alert Programs

- Silver Alert guidance by state
- Feather Alert (California - for IDD)
- Purple Alert (some states)
- Links and scripts for requesting alerts

#### 4.2 ID Card Generator

Printable/digital ID card (from Toolkit):

- "I have Dementia - See back for important information"
- Name
- Emergency contact
- Phone
- Individual support needs
- What they may/may not be able to do

#### 4.3 Geofencing (Future)

- Home safe zone
- Escalating notifications (vibra → ping → call tree)
- Quiet hours / night mode

---

## Technical Requirements

### Data Storage

**All data must remain local on the user's device.** No cloud sync, no remote servers.

- Use AsyncStorage for simple key-value data
- Consider SQLite for complex queries (incident patterns)
- Profile, incidents, and settings stored only on device
- Ensures maximum privacy and full offline functionality

### Offline Mode

- First 10 minutes of emergency fully cached
- Profile & photo always available offline
- Queue notifications for when back online

### Privacy & Consent

- Treat as PHI-adjacent
- Explicit consents for: GPS sharing, sending medical details via SMS, sharing with responders
- Dignity-first copy ("Let's help them get back to a familiar place")

---

## File Structure (Proposed)

```
app/
  (tabs)/
    index.tsx          # Home - Quick actions
    profile.tsx        # Person profile management
    plan.tsx           # Emergency plan setup
    safety.tsx         # Checklists & prevention
  emergency.tsx        # ✅ Emergency timer flow
  readout.tsx          # ✅ 911 read-out
  contacts.tsx         # Inner circle management
  destinations.tsx     # Likely places
  incidents.tsx        # Log & patterns
  letter.tsx           # Neighbor letter generator
  card.tsx             # Quick share card

context/
  ProfileContext.tsx   # ✅ Exists - expand

types/
  profile.ts           # TypeScript interfaces
  incident.ts
  contact.ts
```

---

## Design Notes

- Large touch targets (caregiver may be stressed/shaking)
- High contrast for readability
- Minimal steps to critical actions
- Timer always visible during emergency
- One-tap calling
- Offline-first architecture

---

## Engineering Rules

- Always run `git fetch` before reporting branch merge/ahead/behind status.
- Validate UI fixes on target device class (mobile) before calling them complete.
- Prefer shared component fixes for repeated UI issues (e.g., input alignment) over per-screen patches.
- Keep destructive actions (delete/remove) in edit/detail screens, not list cards, unless explicitly requested.
- For risky native infra changes (gesture/reanimated/drag), ship behind a fallback path and verify rollout incrementally.
- Keep one stable rendering model per screen state (empty vs populated) and avoid mixing complex list-empty composition until stable.
- Compatibility gate before library version changes: verify Expo SDK + `react-native` + `react-native-reanimated` + target package compatibility before install/upgrade/downgrade.
- Web workarounds must stay web-scoped: if a resolver bug is web-only, implement it in `.web.ts/.web.tsx` wrappers unless native repro proves broader scope.
- Known-good snapshot before risky infra changes: create a clear rollback point before touching gesture/reanimated/native package wiring.
- Drag UX standard: every drag-and-drop feature must include active-item visual state and haptics (when platform-supported).
- Sort order consistency: reorderable entities must have ordered reads, append-on-create (`max+1`), selective updates on reorder, and native/web parity.
- Solo-dev branch rule: default to working directly on `main`; only use a feature branch when explicitly instructed.
- Dirty tree scoped commit: in a non-clean worktree, stage files explicitly (no broad adds) and verify unrelated files remain uncommitted.
- Compact card hierarchy: use one visual signal per concept (e.g., severity) and avoid duplicate color cues in dense cards.
- Mobile tap targets: actionable icon buttons should be at least `40x40`.
- Copy UX feedback: clipboard actions must show visible success/failure feedback (no silent copy).
- Emergency script wording: avoid `Unknown`/`N/A` in dispatch copy; use fill-in placeholders and omit unavailable optional sections.
- Diagnosis-neutral default copy: emergency/public-facing scripts should not assume a specific diagnosis unless explicitly provided.
- Chip selector performance: use guarded state updates for option chips (`prev === next ? prev : ...`) to avoid no-op rerenders.

---

## Team Skills (Playbooks)

- **Mobile List Stability**: Checklist for list rendering on iOS/Android (safe area, viewport fill, empty state, footer/header composition, gesture stack).
- **Form Guard Standardization**: Reusable pattern for unsaved-change detection, back interception, and discard confirmation.
- **Expo Runtime Config Check**: Validate Babel/Expo Router/Reanimated/Gesture config against current SDK guidance.
- **UI Consistency Pass**: Normalize save placement, empty-state CTA behavior, and destructive action placement across screens.
- **Safe Feature Rollout**: Roll out native/infra-heavy features with platform flag or fallback path, then remove guard after device validation.
- **Draggable List Rollout**: Standard implementation template for press-and-hold drag, active-item visuals, haptics, optimistic reorder, and persistence.
- **Platform Import Firebreak**: Create `.native/.web` wrappers for fragile third-party imports and keep platform-specific resolver workarounds isolated.
- **List Order Persistence Audit**: Verify `sortOrder` behavior across read/create/update/delete paths for both SQLite/native and AsyncStorage/web.
- **Expo Go Crash Triage**: Fast debugging checklist for native crashes with weak logs (cache reset, version compatibility, entrypoint validation, rollback strategy).
- **Mobile Card Density Pass**: Reduce card clutter through hierarchy audit, metadata trimming, and redundant signal removal.
- **Clipboard Feedback Patterns**: Standardize copy/share confirmation UX (inline label/icon swap, timeout reset, failure handling).
- **Emergency Copy Builder**: Generate dispatch-ready, diagnosis-neutral scripts with conditional sections and fill-in placeholders.
- **Pressable Performance Tuning**: Improve perceived responsiveness for chip/segment controls (Pressable, hitSlop, guarded updates, memoized rows when needed).
- **Collapsed Content UX**: Reusable expand/collapse section pattern with clear affordance, default state guidance, and accessibility considerations.

---

## Reference Documents

Located in `/reference/`:

- `12 steps.docx` - 12-step search protocol
- `Emergency-ID-Sheet 04_25.docx` - Profile fields template
- `Wandering-Emergency-Plan 04_25.docx` - 4-phase planning approach
- `Wandering-Letter 04_25.docx` - Neighbor letter template
- `Toolkit 05_25.pdf` - Safety checklists, ID cards, dementia info

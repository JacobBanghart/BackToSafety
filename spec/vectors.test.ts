import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import i18next from 'i18next';
import { afterAll, describe, expect, it } from 'vitest';

import { ageOn, formatDob, parseDob } from '@/utils/age';
import * as emergency from '@/utils/emergency';
import * as formatters from '@/utils/formatters';
import { describeMobility } from '@/utils/mobility';
import * as phone from '@/utils/phone';
import { photoFileName } from '@/utils/photoPath';
import * as readout from '@/utils/readout';

// Runs every spec/vectors/*.json file against the TypeScript implementation. The
// Kotlin shared module gets the same runner with its own adapter table, so both apps
// are held to identical input→output pairs.
//
// Vector format: { source, description, functions: { <name>: [{ args, expected, finding? }] } }
// A case tagged `finding` pins current behavior that PARITY_PLAN.md lists as a known
// bug. It gets fixed and re-blessed before the freeze, never copied into Kotlin.
//
// Blessing: a new case may omit `expected`. `VECTORS_BLESS=1 npx vitest run spec/vectors`
// fills those in from the current implementation. Existing values are never rewritten;
// to change one, delete its `expected` in a separate, reviewed commit (PARITY_PLAN rule 1).

type Case = { args: unknown[]; expected?: unknown; finding?: string };
type VectorFile = { source: string; description: string; functions: Record<string, Case[]> };

// Vectors carry calendar dates as YYYY-MM-DD strings, read as local dates.
const toDate = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
};
const fromDate = (date: Date | null) =>
  date &&
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

// Text builders take (lang, input): translations come from the real locale files.
const LOCALES_DIR = path.join(__dirname, '..', 'i18n', 'locales');
const i18n = i18next.createInstance();
await i18n.init({
  resources: Object.fromEntries(
    readdirSync(LOCALES_DIR).map((lang) => [
      lang,
      Object.fromEntries(
        readdirSync(path.join(LOCALES_DIR, lang)).map((file) => [
          file.replace(/\.json$/, ''),
          JSON.parse(readFileSync(path.join(LOCALES_DIR, lang, file), 'utf8')),
        ]),
      ),
    ]),
  ),
  lng: 'en',
  fallbackLng: 'en',
  returnEmptyString: false,
  interpolation: { escapeValue: false },
  compatibilityJSON: 'v4',
});
const translator = (lang: string, ns: string) =>
  i18n.getFixedT(lang, ns) as unknown as readout.Translate;

type ReadoutArgs = Omit<readout.ReadoutInput, 'today'> & { today: string };
const readoutAdapter =
  <T>(fn: (input: readout.ReadoutInput, t: readout.Translate) => T) =>
  (lang: string, input: ReadoutArgs) =>
    fn({ ...input, today: toDate(input.today) }, translator(lang, 'readout'));

const ADAPTERS: Record<string, (...args: never[]) => unknown> = {
  parseDob: (dob: string) => fromDate(parseDob(dob)),
  ageOn: (dob: string, today: string) => ageOn(dob, toDate(today)),
  formatDob: (date: string) => formatDob(toDate(date)),
  formatHeightInput: formatters.formatHeightInput,
  formatWeightInput: formatters.formatWeightInput,
  formatMedicAlertIdInput: formatters.formatMedicAlertIdInput,
  formatDobInput: formatters.formatDobInput,
  formatPhoneNumber: phone.formatPhoneNumber,
  formatPhoneInput: phone.formatPhoneInput,
  stripPhoneFormatting: phone.stripPhoneFormatting,
  normalizeSmsRecipient: phone.normalizeSmsRecipient,
  normalizeUniqueSmsRecipients: phone.normalizeUniqueSmsRecipients,
  buildInitialSteps: (lang: string, emergencyNumber: string) =>
    emergency.buildInitialSteps(translator(lang, 'emergency'), emergencyNumber),
  secondsRemaining: (startedAt: string, now: string) =>
    emergency.secondsRemaining(Date.parse(startedAt), Date.parse(now)),
  countdownAlerts: emergency.countdownAlerts,
  parseActiveEmergency: emergency.parseActiveEmergency,
  formatCountdown: emergency.formatCountdown,
  buildAlertSms: (lang: string, input: Parameters<typeof emergency.buildAlertSms>[1]) =>
    emergency.buildAlertSms(translator(lang, 'emergency'), input),
  directionHint: (lang: string, hand: 'left' | 'right' | 'unknown' | null) =>
    emergency.directionHint(translator(lang, 'emergency'), hand ?? undefined),
  describeMobility: (lang: string, stored: string) =>
    describeMobility(stored, translator(lang, 'readout')),
  photoFileName,
  needsVehicleCheck: readout.needsVehicleCheck,
  vehicleCheckKind: readout.vehicleCheckKind,
  buildScript: readoutAdapter(readout.buildScript),
  buildCopyBlock: readoutAdapter(readout.buildCopyBlock),
  missingScriptDetails: readoutAdapter(readout.missingScriptDetails),
};

const BLESS = process.env.VECTORS_BLESS === '1';
const VECTORS_DIR = path.join(__dirname, 'vectors');
const files = readdirSync(VECTORS_DIR).filter((f) => f.endsWith('.json'));

describe.each(files)('%s', (file) => {
  const filePath = path.join(VECTORS_DIR, file);
  const vectors: VectorFile = JSON.parse(readFileSync(filePath, 'utf8'));
  let blessed = 0;

  afterAll(() => {
    if (blessed > 0) writeFileSync(filePath, `${JSON.stringify(vectors, null, 2)}\n`);
  });

  describe.each(Object.entries(vectors.functions))('%s', (fn, cases) => {
    it('has an adapter', () => {
      expect(ADAPTERS[fn], `no adapter for ${fn}`).toBeTypeOf('function');
    });

    it.each(cases.map((c, i) => [i, JSON.stringify(c.args).slice(0, 80), c] as const))(
      'case %i: %s',
      (_i, _label, c) => {
        const actual = ADAPTERS[fn](...(c.args as never[]));
        if (!('expected' in c)) {
          if (!BLESS) throw new Error('case has no expected value; bless it with VECTORS_BLESS=1');
          c.expected = actual;
          blessed++;
          return;
        }
        expect(actual).toEqual(c.expected);
      },
    );
  });
});

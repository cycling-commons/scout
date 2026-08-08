// Extracts the parser straight out of fit-viewer.html and exercises it, so the
// thing under test is the exact code that ships.
import { readFileSync } from 'node:fs';
import { buildTestFit, SCENARIO } from './make-test-fit.mjs';

const html = readFileSync(process.argv[2], 'utf8');
const m = html.match(/\/\/ ===PARSER-START===.*\r?\n([\s\S]*?)\/\/ ===PARSER-END===/);
if (!m) { console.error('FAIL: parser markers not found in html'); process.exit(1); }

const src = m[1] + '\nexport { parseFit, extractTags, crc16, BASE_TYPES, MESG, findDevKey, semiToDeg, fitToDate, applyUndoRule, buildSurfaceSegments, countVehicles };';
const mod = await import('data:text/javascript;base64,' + Buffer.from(src).toString('base64'));

const toAB = (buf) => buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);

let failures = 0;
const check = (name, cond, detail = '') => {
  console.log((cond ? '  PASS  ' : '  FAIL  ') + name + (detail ? ' :: ' + detail : ''));
  if (!cond) failures++;
};

// ---------- 1. real FIT from the SDK's PitchCounter sample (optional) ----------
// Only runs when a path is passed as arg 3 (the SDK sample isn't on CI runners).
// It's a simulator *playback input*: no developer fields, no GPS. Best real-file
// smoke test available; the full app-output path is covered end-to-end in [2].
if (process.argv[3]) {
  console.log('\n[1] SDK sample: ' + process.argv[3].split(/[\\/]/).pop());
  const sample = readFileSync(process.argv[3]);
  const p = mod.parseFit(toAB(sample));
  check('parses without throwing', true);
  check('CRC valid', p.crcOk, 'fileCrc=0x' + p.fileCrc.toString(16));
  check('.FIT signature + header', p.header.headerSize === 12 || p.header.headerSize === 14,
        'headerSize=' + p.header.headerSize);
  check('found record messages', (p.counts[20] ?? 0) > 0, 'records=' + p.counts[20]);
  check('no dev fields (playback input, none expected)', Object.keys(p.devFields).length === 0);
  const withPos = p.messages.filter(x => x.globalNum === 20 && x.fields[0] != null);
  check('no positions invented for an indoor file', withPos.length === 0,
        withPos.length + ' records with position');
  console.log('        message census:', JSON.stringify(p.counts));
} else {
  console.log('\n[1] SDK sample: skipped (no FIT path given as arg 3)');
}

// ---------- 2. end-to-end: a full binary FIT with every option ----------
// tools/make-test-fit.mjs writes a real .fit exercising all POI types, the
// closure durations, a surface-segment sequence, an undo pair, and the three
// radar channels over two passes. Parse it through the SHIPPED parser and assert
// the whole pipeline: parseFit -> extractTags -> buildSurfaceSegments ->
// countVehicles. This is the binary path CI relies on (no SDK sample needed).
console.log('\n[2] End-to-end: full binary FIT (all options)');

const q = mod.parseFit(toAB(buildTestFit()));
check('CRC round-trips over the whole file', q.crcOk);
check('all 5 developer fields declared',
      ['poi_type', 'poi_detail', 'radar_count', 'radar_near', 'radar_speed']
        .every(n => !!mod.findDevKey(q.devFields, n)),
      JSON.stringify(Object.values(q.devFields).map(d => d.name)));

const { tags, totalRecords } = mod.extractTags(q, 'poi_type', 'poi_detail');
check('sees all ' + SCENARIO.length + ' records', totalRecords === SCENARIO.length, 'got ' + totalRecords);
const liveTags = tags.filter(t => !t.cancelled);
const byType = {};
for (const t of liveTags) byType[t.type] = (byType[t.type] || 0) + 1;
check('the DANGER undo pair cancels (2 cancelled)', tags.length - liveTags.length === 2,
      (tags.length - liveTags.length) + ' cancelled');
check('live tag counts per type are as tagged',
      JSON.stringify(byType) === JSON.stringify({ 1: 1, 2: 1, 3: 1, 4: 1, 5: 5, 6: 4, 7: 1, 8: 1 }),
      JSON.stringify(byType));
check('a CLOSURE carries its duration through the binary path',
      liveTags.find(t => t.type === 5).detail >= 1);
check('lat/lon decode from semicircles',
      Math.abs(liveTags[0].lat - 52.0) < 0.05 && Math.abs(liveTags[0].lon - 4.0) < 0.05,
      liveTags[0].lat.toFixed(4) + ',' + liveTags[0].lon.toFixed(4));
check('speed field (6) decodes on records', (() => {
  const r = q.messages.find(x => x.globalNum === 20 && x.fields[6] != null);
  return r && r.fields[6] === 6000;
})());

const rideEnd = mod.fitToDate(q.messages.filter(x => x.globalNum === 20)
  .map(x => x.fields[253]).filter(v => v != null).at(-1));
const e2eSegs = mod.buildSurfaceSegments(tags, rideEnd);
check('surface -> 3 segments (cobbles, gravel, dirt)',
      JSON.stringify(e2eSegs.map(g => g.type)) === '[5,6,7]', JSON.stringify(e2eSegs.map(g => g.type)));
check('cobbles ended by switch, gravel by END, dirt open at ride end',
      e2eSegs[0].ended === false && e2eSegs[1].ended === true && e2eSegs[2].unterminated === true);
check('a segment measures a non-zero length along the track',
      e2eSegs[0].startTime && e2eSegs[0].endTime && e2eSegs[1].endTime > e2eSegs[1].startTime);

const radar = mod.countVehicles(q);
check('radar counts 2 vehicles over two passes', radar.total === 2, 'got ' + radar.total);
check('max concurrent is 1', radar.maxConcurrent === 1, 'got ' + radar.maxConcurrent);
check('radar coverage is partial (mostly not tracking)',
      radar.coverage > 0 && radar.coverage < 1, 'coverage=' + radar.coverage.toFixed(2));

// ---------- 3. the undo rule ----------
// Same type twice inside the window annihilates; any other type never interacts.
// Window is 3s for all tiles (direct and two-tap).
console.log('\n[3] Undo rule (second tap of the same tile within the window)');

const T0 = new Date('2026-07-17T10:00:00Z').getTime();
const at = (s, type) => ({ time: new Date(T0 + s * 1000), type, lat: 1, lon: 1, detail: 0 });
const live = (rows) => mod.applyUndoRule(rows).filter(t => !t.cancelled).map(t => t.type);

check('same type 1s apart -> both cancelled',
      JSON.stringify(live([at(0, 1), at(1, 1)])) === '[]',
      JSON.stringify(live([at(0, 1), at(1, 1)])));
check('same type 4s apart -> both kept (outside 3s direct window)',
      JSON.stringify(live([at(0, 4), at(4, 4)])) === '[4,4]',
      JSON.stringify(live([at(0, 4), at(4, 4)])));
check('same type exactly 3s apart -> kept (boundary is exclusive)',
      JSON.stringify(live([at(0, 4), at(3, 4)])) === '[4,4]',
      JSON.stringify(live([at(0, 4), at(3, 4)])));
check('different types 1s apart -> both kept (burst tagging)',
      JSON.stringify(live([at(0, 1), at(1, 6)])) === '[1,6]',
      JSON.stringify(live([at(0, 1), at(1, 6)])));
check('mistap DANGER, undo, then tag SURFACE -> only SURFACE',
      JSON.stringify(live([at(0, 1), at(1, 1), at(2, 6)])) === '[6]',
      JSON.stringify(live([at(0, 1), at(1, 1), at(2, 6)])));
check('a different type between the pair does not block the cancel',
      JSON.stringify(live([at(0, 1), at(1, 6), at(2, 1)])) === '[6]',
      JSON.stringify(live([at(0, 1), at(1, 6), at(2, 1)])));
check('three same-type in 3s -> first pair cancels, third survives',
      JSON.stringify(live([at(0, 1), at(1, 1), at(2, 1)])) === '[1]',
      JSON.stringify(live([at(0, 1), at(1, 1), at(2, 1)])));
check('four same-type in a burst -> two pairs, none survive',
      JSON.stringify(live([at(0, 1), at(1, 1), at(2, 1), at(3, 1)])) === '[]',
      JSON.stringify(live([at(0, 1), at(1, 1), at(2, 1), at(3, 1)])));
check('CLOSURE pair cancels regardless of differing detail', (() => {
  const rows = [{ ...at(0, 5), detail: 1 }, { ...at(1, 5), detail: 3 }];
  return live(rows).length === 0;
})());

// Two-tap tiles use the same 3s undo window as direct tiles.
check('CLOSURE pair 2s apart -> cancelled',
      JSON.stringify(live([at(0, 5), at(2, 5)])) === '[]',
      JSON.stringify(live([at(0, 5), at(2, 5)])));
check('CLOSURE pair 4s apart -> kept (outside 3s window)',
      JSON.stringify(live([at(0, 5), at(4, 5)])) === '[5,5]',
      JSON.stringify(live([at(0, 5), at(4, 5)])));
check('NOTICE pair 2s apart -> cancelled',
      JSON.stringify(live([at(0, 1), at(2, 1)])) === '[]',
      JSON.stringify(live([at(0, 1), at(2, 1)])));
check('SCENERY pair 2s apart -> cancelled',
      JSON.stringify(live([at(0, 2), at(2, 2)])) === '[]',
      JSON.stringify(live([at(0, 2), at(2, 2)])));
check('RESUPPLY leaf (WATER) pair 2s apart -> cancelled',
      JSON.stringify(live([at(0, 3), at(2, 3)])) === '[]',
      JSON.stringify(live([at(0, 3), at(2, 3)])));
check('OTHER (direct tile) pair 4s apart -> kept',
      JSON.stringify(live([at(0, 4), at(4, 4)])) === '[4,4]',
      JSON.stringify(live([at(0, 4), at(4, 4)])));
// SURFACE is exempt from undo entirely — surface tags are segment transitions,
// so two in a row must NOT cancel, at any spacing.
check('SURFACE tags never cancel (transitions), 1s apart -> both kept',
      JSON.stringify(live([at(0, 6), at(1, 6)])) === '[6,6]',
      JSON.stringify(live([at(0, 6), at(1, 6)])));
check('untimed tags are left alone, not crashed on',
      mod.applyUndoRule([{ time: null, type: 1 }, at(0, 1)]).length === 2);
check('cancelled rows are marked, not dropped',
      mod.applyUndoRule([at(0, 1), at(1, 1)]).length === 2);
check('empty input is fine', mod.applyUndoRule([]).length === 0);

// ---------- 3b. surface segments ----------
// A surface type opens a stretch, SURF_END (9) closes it, a different type
// switches (previous ends where the new one starts). SURF_NONE (0) is skipped.
console.log('\n[3b] Surface segments (begin / end)');
const RIDE_END = new Date(T0 + 3600 * 1000);
// surface tag s seconds in, with detail and a distinct position
const su = (s, detail) => ({ time: new Date(T0 + s * 1000), type: 6, detail,
                             lat: 52 + s / 1000, lon: 4 + s / 1000, cancelled: false });
const segs = (rows) => mod.buildSurfaceSegments(rows, RIDE_END);

check('type then END -> one closed segment of that type', (() => {
  const s = segs([su(10, 5), su(40, 9)]);              // cobbles 10s..40s
  return s.length === 1 && s[0].type === 5 && s[0].ended === true &&
         s[0].startTime.getTime() === T0 + 10000 && s[0].endTime.getTime() === T0 + 40000;
})());
check('switching type closes the previous where the next starts', (() => {
  const s = segs([su(10, 5), su(30, 6), su(50, 9)]);   // cobbles->gravel->end
  return s.length === 2 && s[0].type === 5 && s[1].type === 6 &&
         s[0].endTime.getTime() === T0 + 30000 &&       // cobbles ends where gravel starts
         s[0].ended === false && s[1].ended === true;
})());
check('unterminated stretch is closed at ride end and flagged', (() => {
  const s = segs([su(10, 7)]);                          // dirt, never ended
  return s.length === 1 && s[0].type === 7 && s[0].unterminated === true &&
         s[0].endTime.getTime() === RIDE_END.getTime();
})());
check('a stray END with nothing open is ignored',
      segs([su(10, 9)]).length === 0);
check('SURF_NONE (unspecified) is not a segment boundary',
      segs([su(10, 0), su(20, 5), su(40, 9)]).length === 1);
check('cancelled surface tags are skipped', (() => {
  const rows = [su(10, 5), { ...su(20, 6), cancelled: true }, su(40, 9)];
  return segs(rows).length === 1 && segs(rows)[0].type === 5;   // gravel switch was cancelled
})());

// ---------- 4. radar vehicle counting ----------
console.log('\n[4] Radar vehicle counting');

// Fake a parsed file: radar_count per second. null = radar not tracking.
const radarFile = (counts, speeds = [], nears = []) => ({
  devFields: {
    '0-2': { name: 'radar_count', devIdx: 0, fieldNum: 2 },
    '0-3': { name: 'radar_near',  devIdx: 0, fieldNum: 3 },
    '0-4': { name: 'radar_speed', devIdx: 0, fieldNum: 4 },
  },
  messages: counts.map((c, i) => ({
    globalNum: 20,
    fields: { 253: 1000000000 + i, 0: 1, 1: 1 },
    devFields: {
      '0-2': c,
      // Default ≤10 m so leave looks like a confirmed pass; override with
      // nears[] for turn-aways / mid-range dropout.
      '0-3': i < nears.length ? nears[i] : (c != null && c > 0 ? 8 : null),
      '0-4': i < speeds.length ? speeds[i] : (c != null && c > 0 ? 40 : null),
    },
  })),
});

const nCars = (counts, speeds = [], nears = []) =>
  mod.countVehicles(radarFile(counts, speeds, nears)).total;

check('one car approaching and passing -> 1',
      nCars([0, 1, 1, 1, 0]) === 1, 'got ' + nCars([0, 1, 1, 1, 0]));
check('two cars in sequence -> 2',
      nCars([0, 1, 1, 0, 0, 1, 1, 0]) === 2, 'got ' + nCars([0, 1, 1, 0, 0, 1, 1, 0]));
check('a platoon of 3 arriving together -> 3',
      nCars([0, 3, 3, 0]) === 3, 'got ' + nCars([0, 3, 3, 0]));
check('cars joining one at a time -> 3',
      nCars([0, 1, 2, 3, 2, 1, 0]) === 3, 'got ' + nCars([0, 1, 2, 3, 2, 1, 0]));
check('empty road -> 0', nCars([0, 0, 0, 0]) === 0);
// Need ≥2 occupied seconds before the first leave, or the earliest departure is dropped.
check('cars already behind at ride start are counted',
      nCars([3, 3, 2, 1, 0]) === 3, 'got ' + nCars([3, 3, 2, 1, 0]));
check('a falling count never adds cars',
      nCars([0, 3, 3, 2, 1, 0]) === 3, 'got ' + nCars([0, 3, 3, 2, 1, 0]));

// The distinction the whole dataset depends on.
const noRadar = mod.countVehicles(radarFile([null, null, null, null]));
check('no radar -> 0 counted but 0% coverage (NOT an empty road)',
      noRadar.total === 0 && noRadar.coverage === 0,
      'total=' + noRadar.total + ' coverage=' + noRadar.coverage);
const emptyRoad = mod.countVehicles(radarFile([0, 0, 0, 0]));
check('empty road -> 0 counted with 100% coverage',
      emptyRoad.total === 0 && emptyRoad.coverage === 1,
      'total=' + emptyRoad.total + ' coverage=' + emptyRoad.coverage);
check('coverage is partial when radar drops mid-ride',
      mod.countVehicles(radarFile([1, 1, null, null])).coverage === 0.5);
check('a car already present when radar connects still counts',
      nCars([null, 1, 1, 0]) === 1, 'got ' + nCars([null, 1, 1, 0]));
check('radar dropping out mid-pass does not credit that car',
      nCars([0, 1, 1, null, 1, 1, 0]) === 1,
      'got ' + nCars([0, 1, 1, null, 1, 1, 0]) + ' (only the second stretch leaves cleanly)');
check('closing speed captured at last second before pass',
      mod.countVehicles(radarFile([0, 1, 1, 0], [null, 45, 40, null])).passes[0].speed === 40);

// Count + speed commit on leave after ≥2 s in view.
check('a one-second blip is not a car',
      nCars([0, 1, 0, 0]) === 0, 'got ' + nCars([0, 1, 0, 0]));
check('mid-range turn-away is not a pass',
      nCars([0, 1, 1, 0], [], [null, 80, 70, null]) === 0,
      'got ' + nCars([0, 1, 1, 0], [], [null, 80, 70, null]));
check('convoy: second turns far after first pass -> only 1',
      nCars([0, 2, 2, 1, 1, 0], [], [null, 20, 10, 60, 55, null]) === 1,
      'got ' + nCars([0, 2, 2, 1, 1, 0], [], [null, 20, 10, 60, 55, null]));
check('count without speed is not a car',
      nCars([0, 1, 1, 0], [null, null, null, null]) === 0,
      'got ' + nCars([0, 1, 1, 0], [null, null, null, null]));
check('repeated one-second blips stay uncounted',
      nCars([0, 1, 0, 1, 0, 1, 0]) === 0, 'got ' + nCars([0, 1, 0, 1, 0, 1, 0]));
check('a phantom pair in one second is not two cars',
      nCars([0, 2, 0, 0]) === 0, 'got ' + nCars([0, 2, 0, 0]));
check('an arrival in the final record is dropped, not counted',
      nCars([0, 0, 1]) === 0, 'got ' + nCars([0, 0, 1]));
check('a queue whose peak lasts one second keeps its last car',
      nCars([0, 1, 2, 3, 2, 1, 0]) === 3, 'got ' + nCars([0, 1, 2, 3, 2, 1, 0]));
check('a blip during a real pass is counted (accepted trade-off)',
      nCars([0, 1, 1, 2, 1, 1, 0]) === 2, 'got ' + nCars([0, 1, 1, 2, 1, 1, 0]));
check('pass time and speed are from the last second before leave',
      (() => {
        const p = mod.countVehicles(radarFile([0, 1, 1, 1, 0], [null, 60, 55, 50, null]));
        return p.passes[0].speed === 50;
      })());
check('no radar fields at all -> null (not zero traffic)',
      mod.countVehicles({ devFields: {}, messages: [] }) === null);
check('maxConcurrent tracks the busiest second',
      mod.countVehicles(radarFile([0, 1, 4, 2, 0])).maxConcurrent === 4);

// ---------- 5. failure modes ----------
console.log('\n[5] Bad input handling');
const notFit = new Uint8Array(64); notFit[0] = 12;
try { mod.parseFit(toAB(notFit)); check('rejects non-FIT file', false, 'no throw'); }
catch (e) { check('rejects non-FIT file', /signature/i.test(e.message), e.message); }

try { mod.parseFit(toAB(new Uint8Array(4))); check('rejects tiny file', false, 'no throw'); }
catch (e) { check('rejects tiny file', /too small/i.test(e.message), e.message); }

const good = buildTestFit();
const trunc = good.slice(0, good.length - 20);
try { mod.parseFit(toAB(trunc)); check('rejects truncated file', false, 'no throw'); }
catch (e) { check('rejects truncated file', /truncated/i.test(e.message), e.message); }

const corrupt = Uint8Array.from(good); corrupt[corrupt.length - 3] ^= 0xFF;
check('flags CRC mismatch on corruption', mod.parseFit(toAB(corrupt)).crcOk === false);

console.log('\n' + (failures ? 'FAILURES: ' + failures : 'ALL CHECKS PASSED'));
process.exit(failures ? 1 : 0);

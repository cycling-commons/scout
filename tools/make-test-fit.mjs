// Builds a synthetic but *binary-valid* FIT file that exercises every Scout
// output: all POI types, the closure durations, a full surface-segment sequence
// (start -> switch -> END, plus an unterminated stretch), an undo pair, the three
// radar channels across two vehicle passes, and GPS + speed. It's what the E2E
// test parses through the shipped parser, and running this file directly writes
// it to disk so a real .fit can be inspected in tools/fit-viewer.html.
//
//   node tools/make-test-fit.mjs out.fit
//
// Nothing here is committed (see .gitignore) — the file is regenerated on demand.

// Same CRC-16 as the parser; the E2E test cross-checks it by asserting crcOk.
const CRC_TABLE = [0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
                   0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400];
function crc16(bytes, start, end) {
  let crc = 0;
  for (let i = start; i < end; i++) {
    const b = bytes[i];
    let t = CRC_TABLE[crc & 0xF];
    crc = ((crc >> 4) & 0x0FFF) ^ t ^ CRC_TABLE[b & 0xF];
    t = CRC_TABLE[crc & 0xF];
    crc = ((crc >> 4) & 0x0FFF) ^ t ^ CRC_TABLE[(b >> 4) & 0xF];
  }
  return crc;
}

const DEG = Math.pow(2, 31) / 180;    // degrees -> semicircles
const NA = 0xFF;                       // uint8 invalid — radar not tracking / no tag

// The scenario, one entry per second. Fields default to "untagged, no radar".
// [poi_type, poi_detail] tags and [count, near, speed] radar are set where noted.
// Same-type point tags are spaced > 3 s apart so the undo rule doesn't eat them;
// the one deliberate undo is the DANGER pair at the end.
export const SCENARIO = (() => {
  const N = 60;
  const rec = [];
  for (let i = 0; i < N; i++) {
    rec.push({
      ts: 1000000000 + i,
      lat: Math.round((52.0 + i * 0.0004) * DEG),
      lon: Math.round((4.0 + i * 0.0006) * DEG),
      speed: 6000,                     // 6.000 m/s (uint16, scale 1000)
      type: 0, detail: 0,              // no tag
      count: NA, near: NA, rspeed: NA, // radar not tracking
    });
  }
  const tag = (i, type, detail) => { rec[i].type = type; rec[i].detail = detail; };
  const radar = (i, c, near, sp) => { rec[i].count = c; rec[i].near = near; rec[i].rspeed = sp; };

  tag(5, 1, 1);                        // DANGER / POTHOLES
  tag(10, 2, 4);                       // SCENERY / VIEW
  tag(13, 9, 1);                       // RESUPPLY / WATER
  tag(18, 9, 2);                       // RESUPPLY / FOOD (>3 s after WATER — same type)
  tag(25, 9, 3);                       // RESUPPLY / MECHANICAL
  tag(19, 4, 0);                       // OTHER
  tag(22, 5, 1);                       // CLOSURE / TODAY
  tag(30, 5, 2);                       // CLOSURE / DAYS
  tag(38, 5, 3);                       // CLOSURE / WEEKS
  tag(46, 5, 4);                       // CLOSURE / MONTHS
  tag(54, 5, 5);                       // CLOSURE / UNKNOWN
  // Surface segments: cobbles -> (switch) gravel -> END, then an open dirt stretch
  tag(24, 6, 5);                       // SURFACE cobbles START
  tag(27, 6, 6);                       // SURFACE gravel  (cobbles ends here)
  tag(33, 6, 9);                       // SURFACE END     (gravel ends; back to normal)
  tag(42, 6, 7);                       // SURFACE dirt START — never ended
  // Undo pair: two DANGER within 3 s cancel each other
  tag(57, 1, 0);
  tag(58, 1, 0);
  // Two vehicle passes — nearest must reach ≤10 m so leave confirms as a pass.
  radar(6, 1, 40, 28); radar(7, 1, 8, 30); radar(8, 0, NA, NA);   // car 1
  radar(35, 1, 50, 45); radar(36, 1, 5, 48); radar(37, 0, NA, NA); // car 2
  return rec;
})();

export function buildTestFit(scenario = SCENARIO) {
  const out = [];
  const u8 = (v) => out.push(v & 0xFF);
  const u16 = (v) => { u8(v); u8(v >> 8); };
  const u32 = (v) => { u16(v); u16(v >> 16); };
  const i32 = (v) => u32(v >>> 0);
  const str = (s, len) => { for (let i = 0; i < len; i++) u8(i < s.length ? s.charCodeAt(i) : 0); };

  // file_id (0), local 0 — one enum field: type = activity (4)
  out.push(0x40); u8(0); u8(0); u16(0); u8(1); u8(0); u8(1); u8(0x00);
  out.push(0x00); u8(4);

  // developer_data_id (207), local 1 — developer_data_index = 0
  out.push(0x41); u8(0); u8(0); u16(207); u8(1); u8(3); u8(1); u8(0x02);
  out.push(0x01); u8(0);

  // field_description (206), local 2 — [dev_data_index, field_num, base_type, name]
  out.push(0x42); u8(0); u8(0); u16(206); u8(4);
  u8(0); u8(1); u8(0x02);     // developer_data_index  uint8
  u8(1); u8(1); u8(0x02);     // field_definition_number uint8
  u8(2); u8(1); u8(0x02);     // fit_base_type_id uint8
  u8(3); u8(16); u8(0x07);    // field_name string(16)
  const DEV = [[0, 'poi_type'], [1, 'poi_detail'], [2, 'radar_count'],
               [3, 'radar_near'], [4, 'radar_speed']];
  for (const [num, name] of DEV) { out.push(0x02); u8(0); u8(num); u8(0x02); str(name, 16); }

  // record (20), local 3, with 5 developer fields
  out.push(0x63); u8(0); u8(0); u16(20); u8(4);
  u8(253); u8(4); u8(0x86);   // timestamp     uint32
  u8(0);   u8(4); u8(0x85);   // position_lat  sint32
  u8(1);   u8(4); u8(0x85);   // position_long sint32
  u8(6);   u8(2); u8(0x84);   // speed         uint16
  u8(5);                      // 5 dev fields, each uint8, devIdx 0
  for (const [num] of DEV) { u8(num); u8(1); u8(0); }
  for (const r of scenario) {
    out.push(0x03);
    u32(r.ts); i32(r.lat); i32(r.lon); u16(r.speed);
    u8(r.type); u8(r.detail); u8(r.count); u8(r.near); u8(r.rspeed);
  }

  const body = Uint8Array.from(out);
  const head = new Uint8Array(12);
  const hv = new DataView(head.buffer);
  hv.setUint8(0, 12); hv.setUint8(1, 0x20); hv.setUint16(2, 2140, true);
  hv.setUint32(4, body.length, true);
  head.set([0x2E, 0x46, 0x49, 0x54], 8); // ".FIT"

  const all = new Uint8Array(head.length + body.length + 2);
  all.set(head, 0); all.set(body, head.length);
  const crc = crc16(all, 0, head.length + body.length);
  new DataView(all.buffer).setUint16(head.length + body.length, crc, true);
  return all;
}

// Run directly -> write the .fit to the given path.
const { pathToFileURL } = await import('node:url');
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const outPath = process.argv[2];
  if (!outPath) { console.error('usage: node tools/make-test-fit.mjs <out.fit>'); process.exit(1); }
  const { writeFileSync } = await import('node:fs');
  const fit = buildTestFit();
  writeFileSync(outPath, fit);
  console.log('wrote ' + fit.length + ' bytes to ' + outPath + ' (' + SCENARIO.length + ' records)');
}

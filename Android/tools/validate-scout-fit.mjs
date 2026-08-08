// Validates a Scout .fit against the shipped fit-viewer parser.
//   node Android/tools/validate-scout-fit.mjs tools/fit-viewer.html path/to/file.fit
import { readFileSync } from 'node:fs';

const htmlPath = process.argv[2];
const fitPath = process.argv[3];
if (!htmlPath || !fitPath) {
  console.error('usage: node validate-scout-fit.mjs <fit-viewer.html> <file.fit>');
  process.exit(2);
}

const html = readFileSync(htmlPath, 'utf8');
const m = html.match(/\/\/ ===PARSER-START===.*\r?\n([\s\S]*?)\/\/ ===PARSER-END===/);
if (!m) {
  console.error('FAIL: parser markers not found');
  process.exit(1);
}
const src = m[1] + '\nexport { parseFit, extractTags, buildSurfaceSegments, countVehicles };';
const mod = await import('data:text/javascript;base64,' + Buffer.from(src).toString('base64'));

const buf = readFileSync(fitPath);
const ab = buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);
const p = mod.parseFit(ab);
const { tags, totalRecords } = mod.extractTags(p, 'poi_type', 'poi_detail');
const rideEnd = tags.length ? tags[tags.length - 1].time : null;
const surfaces = mod.buildSurfaceSegments(tags, rideEnd);
const vehicles = mod.countVehicles(p);

let fail = 0;
const check = (name, cond, detail = '') => {
  console.log((cond ? '  PASS  ' : '  FAIL  ') + name + (detail ? ' :: ' + detail : ''));
  if (!cond) fail++;
};

check('CRC ok', p.crcOk, '0x' + p.fileCrc.toString(16));
check('has records', (p.counts[20] ?? 0) > 0, 'n=' + (p.counts[20] ?? 0));
check('dev field names', findNames(p) >= 5, findNames(p).toString());
check('totalRecords', totalRecords === 60, 'n=' + totalRecords);
check('tags after undo', tags.filter(t => !t.cancelled).length >= 10);
check('surface segments', surfaces.length >= 2, 'n=' + surfaces.length);
check('vehicles', vehicles && vehicles.total === 2, JSON.stringify(vehicles && { total: vehicles.total, coverage: vehicles.coverage }));
process.exit(fail ? 1 : 0);

function findNames(parsed) {
  return Object.values(parsed.devFields).filter(m => m && m.name).length;
}

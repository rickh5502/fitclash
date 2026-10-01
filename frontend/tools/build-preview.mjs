// File: frontend/tools/build-preview.mjs
//
// Agent 1 (Token Guard & File Modularizer).
//
// Builds preview/index.html - a single no-build HTML file that runs the exact
// component source from src/FitClash.jsx, so the prototype can be opened by
// double-clicking a file with no npm install and no backend.
//
// There is deliberately ONE copy of the component. This script only swaps the
// module preamble: React, framer-motion and the icon set arrive as UMD globals
// instead of ESM imports. If the two ever drift, the prototype stops being
// evidence of anything.
//
//   node tools/build-preview.mjs
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..');
const MARKER = '/* ===== SHARED-BODY-START ===== */';

const source = readFileSync(resolve(root, 'src/FitClash.jsx'), 'utf8');

const at = source.indexOf(MARKER);
if (at === -1) throw new Error(`${MARKER} missing from src/FitClash.jsx`);

let body = source.slice(at + MARKER.length).trimStart();
if (!body.includes('export default function FitClash()')) {
  throw new Error('root component signature changed - update this script');
}
body = body.replace('export default function FitClash()', 'function FitClash()');
if (/^import\s/m.test(body)) {
  throw new Error('the shared body imports a module; move the import above the marker');
}

const html = [
  readFileSync(resolve(here, 'preview.head.html'), 'utf8'),
  readFileSync(resolve(here, 'preview.preamble.jsx'), 'utf8'),
  body,
  "\nReactDOM.createRoot(document.getElementById('root')).render(React.createElement(FitClash));\n</script>\n",
].join('');

const out = resolve(root, 'preview/index.html');
writeFileSync(out, html);
console.log(`preview/index.html written (${(html.length / 1024).toFixed(1)} KB)`);

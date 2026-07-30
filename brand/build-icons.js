// Rasterises the Noobatyar brand SVGs to PNG + WebP.
//
// sharp is not a dependency of this folder — it comes along with front_client's
// Next.js install, so point NODE_PATH at it from the repo root:
//     NODE_PATH=front_client/node_modules node brand/build-icons.js
const sharp = require('sharp');
const path = require('path');
const fs = require('fs');

const BRAND = __dirname;
const OUT_PNG = path.join(BRAND, 'png');
const OUT_WEBP = path.join(BRAND, 'webp');
for (const d of [OUT_PNG, OUT_WEBP]) fs.mkdirSync(d, { recursive: true });

// High density so librsvg rasterises from vector at full precision rather than
// upscaling a small bitmap.
const load = (f) => sharp(path.join(BRAND, f), { density: 900 });

// Below ~20px the checkmark closes up, so the simplified mark is used instead.
const SIMPLE_BELOW = 20;

const sizes = [1024, 512, 256, 192, 180, 128, 96, 64, 48, 32, 16];

(async () => {
  const made = [];

  for (const s of sizes) {
    const src = s < SIMPLE_BELOW ? 'noobatyar-favicon.svg' : 'noobatyar-icon.svg';
    const name = `noobatyar-icon-${s}.png`;
    await load(src).resize(s, s).png({ compressionLevel: 9 }).toFile(path.join(OUT_PNG, name));
    made.push(['png', name, s, src]);
  }

  // WebP: same ladder, lossless so flat colour + crisp edges stay exact.
  for (const s of [1024, 512, 256, 192, 128, 64, 32]) {
    const src = s < SIMPLE_BELOW ? 'noobatyar-favicon.svg' : 'noobatyar-icon.svg';
    const name = `noobatyar-icon-${s}.webp`;
    await load(src).resize(s, s).webp({ lossless: true, effort: 6 }).toFile(path.join(OUT_WEBP, name));
    made.push(['webp', name, s, src]);
  }

  // Transparent mark (no background plate) for headers, docs, dark surfaces.
  // The SVG uses `currentColor`, which has no meaning to a standalone
  // rasteriser (librsvg resolves it to black), so each raster variant is
  // rendered from a copy with the colour substituted explicitly.
  const markSvg = fs.readFileSync(path.join(BRAND, 'noobatyar-mark.svg'), 'utf8');
  const variants = {
    ink: '#2E1065',    // deep violet, for light backgrounds
    white: '#FFFFFF',  // for dark backgrounds and photos
  };
  for (const [label, colour] of Object.entries(variants)) {
    const buf = Buffer.from(markSvg.replaceAll('currentColor', colour));
    for (const s of [1024, 512, 256, 128]) {
      await sharp(buf, { density: 900 }).resize(s, s)
        .png({ compressionLevel: 9 }).toFile(path.join(OUT_PNG, `noobatyar-mark-${label}-${s}.png`));
      await sharp(buf, { density: 900 }).resize(s, s)
        .webp({ lossless: true, effort: 6 }).toFile(path.join(OUT_WEBP, `noobatyar-mark-${label}-${s}.webp`));
    }
  }

  // Full-bleed square for Android adaptive / iOS, which apply their own mask.
  for (const s of [1024, 512, 432, 192]) {
    await load('noobatyar-icon-square.svg').resize(s, s)
      .png({ compressionLevel: 9 }).toFile(path.join(OUT_PNG, `noobatyar-square-${s}.png`));
    await load('noobatyar-icon-square.svg').resize(s, s)
      .webp({ lossless: true, effort: 6 }).toFile(path.join(OUT_WEBP, `noobatyar-square-${s}.webp`));
  }

  console.log('generated', made.length + 16, 'raster files');
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });

// Distributes the Noobatyar brand icon into every app surface in the repo:
// the Next.js client, both Android apps, and both iOS apps.
//
// sharp is not a dependency of this folder — it comes along with front_client's
// Next.js install, so point NODE_PATH at it from the repo root:
//     NODE_PATH=front_client/node_modules node brand/apply-to-apps.js
//
// Safe to re-run: every output is overwritten from the SVG sources in this
// folder, so the SVGs stay the single source of truth.
const sharp = require('sharp');
const path = require('path');
const fs = require('fs');

const BRAND = __dirname;
const ROOT = path.resolve(BRAND, '..');
const src = (f) => path.join(BRAND, f);
const out = (...p) => path.join(ROOT, ...p);
const mkdir = (f) => fs.mkdirSync(path.dirname(f), { recursive: true });

// Rasterise from vector at high density rather than upscaling a small bitmap.
const render = (file, size) => sharp(src(file), { density: 900 }).resize(size, size);

const written = [];
const png = async (file, size, dest) => {
  mkdir(dest);
  await render(file, size).png({ compressionLevel: 9 }).toFile(dest);
  written.push(dest);
};
// Android ships launcher icons as lossless webp — lossy smears the crisp edges.
const webp = async (file, size, dest) => {
  mkdir(dest);
  await render(file, size).webp({ lossless: true, effort: 6 }).toFile(dest);
  written.push(dest);
};
// iOS rejects app icons that carry an alpha channel, so these are flattened
// onto the brand violet rather than left transparent at the corners.
const opaquePng = async (file, size, dest) => {
  mkdir(dest);
  await render(file, size).flatten({ background: '#7C3AED' })
    .png({ compressionLevel: 9 }).toFile(dest);
  written.push(dest);
};

// Multi-resolution .ico. sharp has no .ico encoder, but the format is just a
// 6-byte header, one 16-byte directory entry per image, then the payloads —
// and since Vista those payloads may be PNGs, which sharp does encode.
// The 16px frame is drawn from the simplified mark (the full icon's checkmark
// closes up at that size); the larger frames use the full icon.
async function ico(dest) {
  const frames = [
    { file: 'noobatyar-favicon.svg', size: 16 },
    { file: 'noobatyar-icon.svg', size: 32 },
    { file: 'noobatyar-icon.svg', size: 48 },
  ];
  const pngs = [];
  for (const f of frames) {
    pngs.push(await render(f.file, f.size).png({ compressionLevel: 9 }).toBuffer());
  }

  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);              // reserved
  header.writeUInt16LE(1, 2);              // 1 = icon
  header.writeUInt16LE(frames.length, 4);

  const dir = Buffer.alloc(16 * frames.length);
  let offset = 6 + 16 * frames.length;
  frames.forEach((f, i) => {
    const b = i * 16;
    dir.writeUInt8(f.size, b);             // width  (0 would mean 256)
    dir.writeUInt8(f.size, b + 1);         // height
    dir.writeUInt8(0, b + 2);              // palette size
    dir.writeUInt8(0, b + 3);              // reserved
    dir.writeUInt16LE(1, b + 4);           // colour planes
    dir.writeUInt16LE(32, b + 6);          // bits per pixel
    dir.writeUInt32LE(pngs[i].length, b + 8);
    dir.writeUInt32LE(offset, b + 12);
    offset += pngs[i].length;
  });

  mkdir(dest);
  fs.writeFileSync(dest, Buffer.concat([header, dir, ...pngs]));
  written.push(dest);
}

(async () => {
  // ── 1. Next.js client ────────────────────────────────────────────────────
  // app/icon.svg, app/apple-icon.png and app/favicon.ico are file conventions:
  // Next emits the <link> tags for them on its own, no layout markup needed.
  fs.copyFileSync(src('noobatyar-icon.svg'), out('front_client/app/icon.svg'));
  written.push(out('front_client/app/icon.svg'));

  await ico(out('front_client/app/favicon.ico'));

  await png('noobatyar-icon.svg', 180, out('front_client/app/apple-icon.png'));
  await png('noobatyar-icon.svg', 192, out('front_client/public/icons/icon-192.png'));
  await png('noobatyar-icon.svg', 512, out('front_client/public/icons/icon-512.png'));
  // Maskable: full-bleed square, so Android/Chrome can crop it to any shape
  // without eating the artwork.
  await png('noobatyar-icon-square.svg', 512, out('front_client/public/icons/icon-maskable-512.png'));

  // ── 2. Android ───────────────────────────────────────────────────────────
  // Legacy launcher icons are pre-masked and sized in dp*density; adaptive
  // layers are a 108dp canvas, hence the second, larger ladder.
  const legacy = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
  const adaptive = { mdpi: 108, hdpi: 162, xhdpi: 216, xxhdpi: 324, xxxhdpi: 432 };

  // Kept as a loop rather than inlined for mobile_owner: this used to also
  // cover a mobile_client app, and a second KMP app would slot straight back in.
  for (const app of ['mobile_owner']) {
    const res = out(app, 'composeApp/src/androidMain/res');
    for (const [density, size] of Object.entries(legacy)) {
      await webp('noobatyar-icon.svg', size, path.join(res, `mipmap-${density}/ic_launcher.webp`));
      await webp('android/ic_launcher_round.svg', size, path.join(res, `mipmap-${density}/ic_launcher_round.webp`));
    }
    for (const [density, size] of Object.entries(adaptive)) {
      await webp('android/ic_launcher_background.svg', size, path.join(res, `mipmap-${density}/ic_launcher_background.webp`));
      await webp('android/ic_launcher_foreground.svg', size, path.join(res, `mipmap-${density}/ic_launcher_foreground.webp`));
    }
    // Play Store listing icon: 512x512, and the Play Console rejects alpha.
    await opaquePng('noobatyar-icon-square.svg', 512,
      out(app, 'composeApp/src/androidMain/ic_launcher-playstore.png'));

    // Two DIFFERENT files both named main_icon.png, resolved by two different
    // resource systems — so each gets the artwork its consumer actually needs:
    //
    //   commonMain/composeResources/  -> Res.drawable.main_icon, drawn as-is by
    //                                    Compose in SettingsScreen/AboutUsScreen,
    //                                    so it wants the full colour icon.
    //   androidMain/res/drawable/     -> R.drawable.main_icon, used only as the
    //                                    notification setSmallIcon, which Android
    //                                    rebuilds from the alpha channel alone,
    //                                    so it wants a white silhouette.
    await png('noobatyar-icon.svg', 512,
      out(app, 'composeApp/src/commonMain/composeResources/drawable/main_icon.png'));
    await png('android/ic_notification.svg', 96,
      out(app, 'composeApp/src/androidMain/res/drawable/main_icon.png'));
  }

  // ── 3. iOS ───────────────────────────────────────────────────────────────
  // The asset catalogue declares one universal 512x512 entry; the file keeps
  // that name and size so Contents.json stays valid. Opaque, per Apple's rule.
  for (const app of ['mobile_owner']) {
    await opaquePng('noobatyar-icon-square.svg', 512,
      out(app, 'iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/icon-ios-512.png'));
  }

  console.log(`wrote ${written.length} files`);
  for (const f of written) console.log('  ' + path.relative(ROOT, f));
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });

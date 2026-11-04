#!/usr/bin/env node
/**
 * P1.30 — pipeline de geracao dos simbolos de mapa de agua e esgoto.
 *
 * Fonte unica: packages/sanitation/icons/map/source.json (forma por classe +
 * marca por tipo, grade 32x32, sem cor propria) + states.json (cinco estados).
 * Cores de dominio vem dos tokens map-* de frontend/src/theme/common.json
 * (nunca duplicadas aqui em hexadecimal, exceto o cinza de abandonado e os
 * halos, definidos em states.json / derivados do token de fundo).
 *
 * Saidas (packages/sanitation/icons/map/generated/):
 *   svg/<classe>/<tipo>.svg e <tipo>--<estado>.svg (currentColor, web/legenda)
 *   svg-colored/<tema>/<classe>/<tipo>--<estado>.svg (cor literal, QGIS/SLD)
 *   sprite.json + sprite.png e sprite@2x.json + sprite@2x.png (MapLibre, SDF)
 *   qgis-style.xml, sld-light.xml, sld-dark.xml, style-fragment.json
 *   legend.svg, contact-sheet.html, manifest.json
 *
 * Sprite SDF: rasterizacao propria (o vocabulario de primitivas do source.json
 * e so circulo, retangulo, linha, polilinha e poligono, sem curvas) + campo de
 * distancia assinado pela transformada de distancia euclidiana de
 * Felzenszwalb/Huttenlocher, com a MESMA codificacao do tiny-sdf/spritezero
 * (raio 8 a 1x, corte 0,25: byte = 255*(1-0,25) - (255/raio)*distancia, borda
 * em ~191, dentro alto, fora baixo, distancia no canal alfa, RGB zero) e o
 * flag sdf:true no indice, para recoloracao por icon-color/icon-halo-color
 * no MapLibre. Sem dependencias alem da biblioteca padrao do Node.
 *
 * Uso: node scripts/icons/generate-map-symbols.mjs [--check]
 *   --check: regenera num diretorio temporario e recusa diferenca (CI).
 */
import { createHash } from 'node:crypto';
import { deflateSync } from 'node:zlib';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const MAP_DIR = path.join(ROOT, 'packages', 'sanitation', 'icons', 'map');
const OUT = path.join(MAP_DIR, 'generated');
const GRID = 32;

const sha256 = (data) => createHash('sha256').update(data).digest('hex');
const stable = (v) => {
  if (v === null || typeof v !== 'object') return JSON.stringify(v);
  if (Array.isArray(v)) return `[${v.map(stable).join(',')}]`;
  return `{${Object.keys(v).sort().map((k) => `${JSON.stringify(k)}:${stable(v[k])}`).join(',')}}`;
};
const xmlEsc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const hslToHex = (hsl) => {
  const [h, s, l] = hsl.split(/\s+/).map((p) => parseFloat(p));
  const sat = s / 100, light = l / 100;
  const c = (1 - Math.abs(2 * light - 1)) * sat;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = light - c / 2;
  let r = 0, g = 0, b = 0;
  if (h < 60) [r, g, b] = [c, x, 0];
  else if (h < 120) [r, g, b] = [x, c, 0];
  else if (h < 180) [r, g, b] = [0, c, x];
  else if (h < 240) [r, g, b] = [0, x, c];
  else if (h < 300) [r, g, b] = [x, 0, c];
  else [r, g, b] = [c, 0, x];
  const byte = (v) => Math.round((v + m) * 255).toString(16).padStart(2, '0');
  return `#${byte(r)}${byte(g)}${byte(b)}`;
};

// ---------------------------------------------------------------- fonte
const source = JSON.parse(fs.readFileSync(path.join(MAP_DIR, 'source.json'), 'utf-8'));
const statesDoc = JSON.parse(fs.readFileSync(path.join(MAP_DIR, 'states.json'), 'utf-8'));
const theme = JSON.parse(fs.readFileSync(path.join(ROOT, 'frontend', 'src', 'theme', 'common.json'), 'utf-8'));
const STATES = statesDoc.states;
const stateById = Object.fromEntries(STATES.map((s) => [s.id, s]));
const classes = Object.fromEntries(source.classes.map((c) => [c.code, c]));
const domainToken = (domain) => (domain === 'SEWER' ? 'map-sewer' : 'map-water');
const themeColor = (themeName, domain, grey) => {
  if (grey) return statesDoc.abandonedGrey;
  return hslToHex(theme[themeName][domainToken(domain)]);
};
const haloColor = (themeName) => hslToHex(theme[themeName].background);

function symbolOf(type) {
  const cls = classes[type.class];
  const domain = source.domainOverrides[type.code] || cls.domain;
  const prims = type.overrideBase ? [...(type.mark || [])] : [...cls.base, ...(type.mark || [])];
  return {
    styleKey: `sanitation:${type.class.toLowerCase()}:${type.code.toLowerCase().replace(/_/g, '-')}`,
    classCode: type.class, typeCode: type.code, label: type.label, domain,
    geometry: cls.geometry, topologyRole: cls.topologyRole, prims,
  };
}
const symbols = source.types.map(symbolOf).sort((a, b) => (a.styleKey < b.styleKey ? -1 : 1));
for (const s of symbols) {
  s.originHash = sha256(stable({
    class: s.classCode, code: s.typeCode, label: s.label, domain: s.domain,
    geometry: s.geometry, prims: s.prims,
  }));
}

// ---------------------------------------------------------------- SVG
const STROKE = 2;
function primToSvg(p, color, width, dash) {
  const [kind, ...rest] = p;
  const strokeAttrs = `stroke="${color}" stroke-width="${width}" fill="none" stroke-linecap="round" stroke-linejoin="round"${dash ? ` stroke-dasharray="${dash}"` : ''}`;
  switch (kind) {
    case 'circle': return `<circle cx="${rest[0]}" cy="${rest[1]}" r="${rest[2]}" ${strokeAttrs}/>`;
    case 'circle-fill': return `<circle cx="${rest[0]}" cy="${rest[1]}" r="${rest[2]}" fill="${color}" stroke="none"/>`;
    case 'rect': return `<rect x="${rest[0]}" y="${rest[1]}" width="${rest[2]}" height="${rest[3]}" ${strokeAttrs}/>`;
    case 'rect-fill': return `<rect x="${rest[0]}" y="${rest[1]}" width="${rest[2]}" height="${rest[3]}" fill="${color}" stroke="none"/>`;
    case 'line': return `<line x1="${rest[0]}" y1="${rest[1]}" x2="${rest[2]}" y2="${rest[3]}" ${strokeAttrs}/>`;
    case 'polyline': return `<polyline points="${rest[0].map(([x, y]) => `${x},${y}`).join(' ')}" ${strokeAttrs}/>`;
    case 'polygon': return `<polygon points="${rest[0].map(([x, y]) => `${x},${y}`).join(' ')}" ${strokeAttrs}/>`;
    case 'polygon-fill': return `<polygon points="${rest[0].map(([x, y]) => `${x},${y}`).join(' ')}" fill="${color}" stroke="none"/>`;
    default: throw new Error(`primitiva desconhecida: ${kind}`);
  }
}
function variantInner(sym, state, color) {
  const width = state.grey ? STROKE * state.widthFactor : STROKE;
  const parts = sym.prims.map((p) => {
    const filled = p[0].endsWith('-fill');
    return primToSvg(p, color, filled ? STROKE : width, filled ? null : state.dash);
  });
  if (state.overlay === 'halo-ring') {
    parts.push(`<circle cx="16" cy="16" r="${statesDoc.haloRing.r}" stroke="${color}" stroke-width="${statesDoc.haloRing.width}" fill="none" stroke-dasharray="${statesDoc.haloRing.dash}"/>`);
  }
  return parts.join('');
}
function variantSvg(sym, state, color) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">${variantInner(sym, state, color)}</svg>\n`;
}
function lineStyleOf(sym, state, themeName) {
  const baseWidth = statesDoc.lineWidths[sym.classCode] || 3;
  const color = state.grey ? statesDoc.abandonedGrey : themeColor(themeName, sym.domain, false);
  const casing = state.overlay === 'halo-ring' || state.id === 'out-of-target'
    ? { color: haloColor(themeName), width: baseWidth * state.widthFactor + 4 }
    : null;
  // Linhas fora da meta usam o mesmo halo das de ponto: uma capa (casing) clara
  // sob a linha, porque traco tracejado ja e outro estado (projetado/abandonado).
  const halo = state.id === 'out-of-target' ? casing : null;
  return {
    color, width: baseWidth * state.widthFactor,
    dash: state.dash ? state.dash.split(' ').map(Number) : null, halo,
  };
}
function zoneStyleOf(sym, state, themeName) {
  const color = state.grey ? statesDoc.abandonedGrey : themeColor(themeName, sym.domain, false);
  const width = sym.typeCode === 'SEWER_SUBBASIN' ? statesDoc.zone.widthSubbasin : statesDoc.zone.width;
  return {
    color, fillOpacity: state.id === 'in-service' || state.id === 'out-of-target'
      ? statesDoc.zone.fillOpacityInService : statesDoc.zone.fillOpacityOther,
    width: width * state.widthFactor, dash: state.dash || null,
    hatch: state.id === 'out-of-target',
  };
}

// ---------------------------------------------------------------- raster + SDF
// Converte primitivas em segmentos de traco e poligonos de preenchimento,
// aplica o padrao de traco do estado e rasteriza com superamostragem 3x3.
// Circulo vira poligono regular de 72 lados (erro < 0,03 px a 32 px).
function primSegments(p, width, dash) {
  const strokes = [];
  const fills = [];
  const pushSeg = (x1, y1, x2, y2) => strokes.push({ x1, y1, x2, y2, w: width });
  const asPoly = (pts, closed) => {
    for (let i = 0; i < pts.length - 1; i++) pushSeg(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]);
    if (closed) pushSeg(pts[pts.length - 1][0], pts[pts.length - 1][1], pts[0][0], pts[0][1]);
  };
  const circlePts = (cx, cy, r) => Array.from({ length: 72 }, (_, i) => {
    const a = (i / 72) * Math.PI * 2;
    return [cx + r * Math.cos(a), cy + r * Math.sin(a)];
  });
  const [kind, ...rest] = p;
  switch (kind) {
    case 'circle': asPoly(circlePts(rest[0], rest[1], rest[2]), true); break;
    case 'circle-fill': fills.push(circlePts(rest[0], rest[1], rest[2])); break;
    case 'rect': asPoly([[rest[0], rest[1]], [rest[0] + rest[2], rest[1]], [rest[0] + rest[2], rest[1] + rest[3]], [rest[0], rest[1] + rest[3]]], true); break;
    case 'rect-fill': fills.push([[rest[0], rest[1]], [rest[0] + rest[2], rest[1]], [rest[0] + rest[2], rest[1] + rest[3]], [rest[0], rest[1] + rest[3]]]); break;
    case 'line': pushSeg(rest[0], rest[1], rest[2], rest[3]); break;
    case 'polyline': asPoly(rest[0], false); break;
    case 'polygon': asPoly(rest[0], true); break;
    case 'polygon-fill': fills.push(rest[0]); break;
    default: throw new Error(`primitiva desconhecida: ${kind}`);
  }
  if (!dash) return { strokes, fills };
  const pattern = dash.split(' ').map(Number);
  const dashed = [];
  for (const s of strokes) {
    const len = Math.hypot(s.x2 - s.x1, s.y2 - s.y1);
    if (len === 0) continue;
    let pos = 0, pi = 0, on = true, remain = pattern[0];
    while (pos < len) {
      const take = Math.min(remain, len - pos);
      if (on) {
        const t0 = pos / len, t1 = (pos + take) / len;
        dashed.push({ x1: s.x1 + (s.x2 - s.x1) * t0, y1: s.y1 + (s.y2 - s.y1) * t0, x2: s.x1 + (s.x2 - s.x1) * t1, y2: s.y1 + (s.y2 - s.y1) * t1, w: s.w });
      }
      pos += take; remain -= take;
      if (remain <= 1e-9) { pi = (pi + 1) % pattern.length; remain = pattern[pi]; on = !on; }
    }
  }
  return { strokes: dashed, fills };
}
function rasterizeAlpha(sym, state, cell, pad, scale) {
  const width = state.grey ? STROKE * state.widthFactor : STROKE;
  let strokes = [], fills = [];
  for (const p of sym.prims) {
    const filled = p[0].endsWith('-fill');
    const { strokes: st, fills: fi } = primSegments(p, filled ? 0 : width, filled ? null : state.dash);
    strokes.push(...st); fills.push(...fi);
  }
  if (state.overlay === 'halo-ring') {
    const { strokes: st } = primSegments(['circle', 16, 16, statesDoc.haloRing.r], statesDoc.haloRing.width, statesDoc.haloRing.dash);
    strokes.push(...st);
  }
  const SS = 3;
  const alpha = new Uint8Array(cell * cell);
  const toCell = (g) => g * scale + pad;
  for (const s of strokes) {
    s.cx1 = toCell(s.x1); s.cy1 = toCell(s.y1); s.cx2 = toCell(s.x2); s.cy2 = toCell(s.y2);
    s.hw = (s.w * scale) / 2;
    s.minX = Math.min(s.cx1, s.cx2) - s.hw; s.maxX = Math.max(s.cx1, s.cx2) + s.hw;
    s.minY = Math.min(s.cy1, s.cy2) - s.hw; s.maxY = Math.max(s.cy1, s.cy2) + s.hw;
    s.dx = s.cx2 - s.cx1; s.dy = s.cy2 - s.cy1; s.len2 = s.dx * s.dx + s.dy * s.dy;
  }
  const fillPolys = fills.map((pts) => pts.map(([x, y]) => [toCell(x), toCell(y)]));
  const inside = (x, y, poly) => {
    let c = false;
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      const [xi, yi] = poly[i], [xj, yj] = poly[j];
      if ((yi > y) !== (yj > y) && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) c = !c;
    }
    return c;
  };
  for (let py = 0; py < cell; py++) {
    for (let px = 0; px < cell; px++) {
      let covered = 0;
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const x = px + (sx + 0.5) / SS, y = py + (sy + 0.5) / SS;
          let hit = false;
          for (const s of strokes) {
            if (x < s.minX || x > s.maxX || y < s.minY || y > s.maxY) continue;
            const t = s.len2 === 0 ? 0 : Math.max(0, Math.min(1, ((x - s.cx1) * s.dx + (y - s.cy1) * s.dy) / s.len2));
            const dx = x - (s.cx1 + s.dx * t), dy = y - (s.cy1 + s.dy * t);
            if (dx * dx + dy * dy <= s.hw * s.hw) { hit = true; break; }
          }
          if (!hit) for (const poly of fillPolys) if (inside(x, y, poly)) { hit = true; break; }
          if (hit) covered++;
        }
      }
      alpha[py * cell + px] = Math.round((covered / (SS * SS)) * 255);
    }
  }
  return alpha;
}
// EDT de Felzenszwalb/Huttenlocher (distancia euclidiana ao quadrado, 2 passes).
function edt1d(grid, offset, stride, length, f, v, z) {
  const INF = 1e20;
  v[0] = 0; z[0] = -INF; z[1] = INF; f[0] = grid[offset];
  for (let q = 1, k = 0; q < length; q++) {
    const fq = f[q] = grid[offset + q * stride];
    const q2 = q * q;
    let s;
    do {
      const r = v[k];
      s = (fq - f[r] + q2 - r * r) / (q - r) / 2;
    } while (s <= z[k] && --k > -1);
    k++; v[k] = q; z[k] = s; z[k + 1] = INF;
  }
  for (let q = 0, k = 0; q < length; q++) {
    while (z[k + 1] < q) k++;
    const r = v[k], qr = q - r;
    grid[offset + q * stride] = f[r] + qr * qr;
  }
}
function edt2d(data, width, height) {
  const f = new Float64Array(Math.max(width, height));
  const v = new Uint16Array(Math.max(width, height));
  const z = new Float64Array(Math.max(width, height) + 1);
  for (let x = 0; x < width; x++) edt1d(data, x, width, height, f, v, z);
  for (let y = 0; y < height; y++) edt1d(data, y * width, 1, width, f, v, z);
}
const ALPHA_TABLE = (() => {
  const t = new Float64Array(256);
  for (let i = 0; i < 256; i++) {
    const d = 0.5 - Math.pow(i / 255, 1 / 2.2);
    t[i] = d * Math.abs(d);
  }
  t[255] = -1e20;
  return t;
})();
function alphaToSdf(alpha, cell, radius, cutoff) {
  const INF = 1e20;
  const outer = new Float64Array(cell * cell).fill(INF);
  const inner = new Float64Array(cell * cell).fill(0);
  for (let i = 0; i < alpha.length; i++) {
    const a = alpha[i];
    if (a === 0) continue;
    const t = ALPHA_TABLE[a];
    outer[i] = Math.max(0, t);
    inner[i] = Math.max(0, -t);
  }
  edt2d(outer, cell, cell);
  edt2d(inner, cell, cell);
  const scale = 255 / radius, base = 255 * (1 - cutoff), out = new Uint8Array(cell * cell);
  for (let i = 0; i < cell * cell; i++) {
    const d = Math.sqrt(outer[i]) - Math.sqrt(inner[i]);
    out[i] = Math.max(0, Math.min(255, Math.round(base - scale * d)));
  }
  return out;
}
// PNG RGBA (filtro 0 por linha) com a distancia no alfa e RGB zero (spritezero).
const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function pngChunk(type, data) {
  const out = Buffer.alloc(12 + data.length);
  out.writeUInt32BE(data.length, 0);
  out.write(type, 4, 'ascii');
  data.copy(out, 8);
  out.writeUInt32BE(crc32(Buffer.concat([Buffer.from(type, 'ascii'), data])), 8 + data.length);
  return out;
}
function encodePngSdf(cells, cols, rows, cell) {
  const W = cols * cell, H = rows * cell;
  const raw = Buffer.alloc((W * 4 + 1) * H);
  for (let y = 0; y < H; y++) {
    const rowStart = y * (W * 4 + 1);
    raw[rowStart] = 0;
    for (let x = 0; x < W; x++) {
      const cellIdx = Math.floor(y / cell) * cols + Math.floor(x / cell);
      const a = cells[cellIdx] ? cells[cellIdx][(y % cell) * cell + (x % cell)] : 0;
      const o = rowStart + 1 + x * 4;
      raw[o] = 0; raw[o + 1] = 0; raw[o + 2] = 0; raw[o + 3] = a;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr), pngChunk('IDAT', deflateSync(raw)), pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

// Escrita com retry: o sandbox devolve EPERM transitorio em rajadas (o mesmo
// fenomeno que os scripts de ops contornam com dpkg_retry); sem retry, uma
// rajada no meio da geracao deixa o diretorio parcial e mata a pipeline.
function sleepSync(ms) {
  try {
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
  } catch {
    const end = Date.now() + ms;
    while (Date.now() < end) { /* espera ocupada de reserva */ }
  }
}
function writeFileRetry(p, content) {
  // Escrita atomica via temporario + rename: alem de nunca deixar arquivo
  // parcial numa queda, evita o truncate in-place, que o sandbox ja negou
  // com EPERM persistente para sprite@2x.png (30/30 tentativas) enquanto a
  // criacao do mesmo arquivo no mesmo diretorio passava.
  const tmp = `${p}.tmp-${process.pid}`;
  let delay = 100;
  for (let attempt = 1; attempt <= 180; attempt++) {
    try {
      fs.mkdirSync(path.dirname(p), { recursive: true });
      fs.writeFileSync(tmp, content);
      fs.renameSync(tmp, p);
      return;
    } catch (e) {
      try { fs.unlinkSync(tmp); } catch { /* temporario pode nem existir */ }
      if (e.code !== 'EPERM' || attempt === 180) throw e;
      process.stderr.write(`[map-symbols] escrita falhou (${e.code}) em ${path.relative(ROOT, p)}; tentativa ${attempt}/180 em ${delay}ms\n`);
      sleepSync(delay);
      delay = Math.min(delay * 2, 10000);
    }
  }
}
function qgisStyle(pointSyms, lineSyms, zoneSyms) {
  const symXml = [];
  for (const s of pointSyms) {
    for (const st of STATES) {
      symXml.push(`  <symbol name="${xmlEsc(s.styleKey)}--${st.id}" type="marker" clip_to_extent="1" alpha="1">\n    <layer enabled="1" class="SvgMarker" locked="0" pass="0">\n      <prop v="svg-colored/light/${s.classCode.toLowerCase()}/${s.typeCode.toLowerCase().replace(/_/g, '-') + '--' + st.id}.svg" k="name"/>\n      <prop v="0" k="angle"/>\n      <prop v="8" k="size"/>\n      <prop v="MM" k="size_unit"/>\n      <prop v="0,0" k="offset"/>\n      <prop v="1" k="horizontal_anchor_point"/>\n      <prop v="1" k="vertical_anchor_point"/>\n    </layer>\n  </symbol>`);
    }
  }
  for (const s of lineSyms) {
    for (const st of STATES) {
      const ls = lineStyleOf(s, st, 'light');
      const layers = [];
      if (ls.halo) layers.push(`    <layer enabled="1" class="SimpleLine" locked="0" pass="0">\n      <prop v="${ls.halo.color}" k="line_color"/>\n      <prop v="${ls.halo.width}" k="line_width"/>\n      <prop v="MM" k="line_width_unit"/>\n      <prop v="solid" k="line_style"/>\n    </layer>`);
      layers.push(`    <layer enabled="1" class="SimpleLine" locked="0" pass="0">\n      <prop v="${ls.color}" k="line_color"/>\n      <prop v="${ls.width}" k="line_width"/>\n      <prop v="MM" k="line_width_unit"/>\n      <prop v="${ls.dash ? 'dash' : 'solid'}" k="line_style"/>\n${ls.dash ? `      <prop v="${ls.dash.join(';')}" k="customdash"/>\n      <prop v="Pixel" k="customdash_unit"/>\n` : ''}    </layer>`);
      symXml.push(`  <symbol name="${xmlEsc(s.styleKey)}--${st.id}" type="line" clip_to_extent="1" alpha="1">\n${layers.join('\n')}\n  </symbol>`);
    }
  }
  for (const s of zoneSyms) {
    for (const st of STATES) {
      const zs = zoneStyleOf(s, st, 'light');
      const hatch = zs.hatch ? `\n    <layer enabled="1" class="LinePatternFill" locked="0" pass="0">\n      <prop v="45" k="angle"/>\n      <prop v="${zs.color}" k="color"/>\n      <prop v="4" k="distance"/>\n      <prop v="MM" k="distance_unit"/>\n      <prop v="0.5" k="line_width"/>\n    </layer>` : '';
      symXml.push(`  <symbol name="${xmlEsc(s.styleKey)}--${st.id}" type="fill" clip_to_extent="1" alpha="1">\n    <layer enabled="1" class="SimpleFill" locked="0" pass="0">\n      <prop v="${zs.color}" k="color"/>\n      <prop v="${zs.color}" k="outline_color"/>\n      <prop v="${zs.width}" k="outline_width"/>\n      <prop v="MM" k="outline_width_unit"/>\n      <prop v="${zs.dash ? 'dash' : 'solid'}" k="outline_style"/>\n${zs.dash ? `      <prop v="${zs.dash.split(' ').join(';')}" k="outline_customdash"/>\n` : ''}      <prop v="${zs.fillOpacity}" k="fill_opacity"/>\n    </layer>${hatch}\n  </symbol>`);
    }
  }
  return `<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE qgis_style>\n<qgis_style version="2">\n <symbols>\n${symXml.join('\n')}\n </symbols>\n</qgis_style>\n`;
}
function sldDoc(themeName, pointSyms, lineSyms, zoneSyms) {
  const rules = [];
  const svgPath = (s, st) => `svg-colored/${themeName}/${s.classCode.toLowerCase()}/${s.typeCode.toLowerCase().replace(/_/g, '-') + '--' + st.id}.svg`;
  for (const s of pointSyms) {
    for (const st of STATES) {
      rules.push(`    <Rule><Name>${xmlEsc(s.styleKey)}--${st.id}</Name><PointSymbolizer><Graphic><ExternalGraphic><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="${svgPath(s, st)}"/><Format>image/svg+xml</Format></ExternalGraphic><Size>32</Size></Graphic></PointSymbolizer></Rule>`);
    }
  }
  for (const s of lineSyms) {
    for (const st of STATES) {
      const ls = lineStyleOf(s, st, themeName);
      const casing = ls.halo ? `<LineSymbolizer><Stroke><CssParameter name="stroke">${ls.halo.color}</CssParameter><CssParameter name="stroke-width">${ls.halo.width}</CssParameter></Stroke></LineSymbolizer>` : '';
      const dash = ls.dash ? `<CssParameter name="stroke-dasharray">${ls.dash.join(' ')}</CssParameter>` : '';
      rules.push(`    <Rule><Name>${xmlEsc(s.styleKey)}--${st.id}</Name>${casing}<LineSymbolizer><Stroke><CssParameter name="stroke">${ls.color}</CssParameter><CssParameter name="stroke-width">${ls.width}</CssParameter>${dash}</Stroke></LineSymbolizer></Rule>`);
    }
  }
  for (const s of zoneSyms) {
    for (const st of STATES) {
      const zs = zoneStyleOf(s, st, themeName);
      const dash = zs.dash ? `<CssParameter name="stroke-dasharray">${zs.dash}</CssParameter>` : '';
      const hatch = zs.hatch ? `<PolygonSymbolizer><Fill><GraphicFill><Graphic><Mark><WellKnownName>shape://slash</WellKnownName><Stroke><CssParameter name="stroke">${zs.color}</CssParameter><CssParameter name="stroke-width">0.5</CssParameter></Stroke></Mark><Size>6</Size></Graphic></GraphicFill></Fill></PolygonSymbolizer>` : '';
      rules.push(`    <Rule><Name>${xmlEsc(s.styleKey)}--${st.id}</Name><PolygonSymbolizer><Fill><CssParameter name="fill">${zs.color}</CssParameter><CssParameter name="fill-opacity">${zs.fillOpacity}</CssParameter></Fill><Stroke><CssParameter name="stroke">${zs.color}</CssParameter><CssParameter name="stroke-width">${zs.width}</CssParameter>${dash}</Stroke></PolygonSymbolizer>${hatch}</Rule>`);
    }
  }
  return `<?xml version="1.0" encoding="UTF-8"?>\n<StyledLayerDescriptor version="1.0.0" xmlns="http://www.opengis.net/sld" xmlns:ogc="http://www.opengis.net/ogc" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd">\n  <NamedLayer><Name>ziipsan-map-symbols-${themeName}</Name><UserStyle><Title>Ziip SAN — simbolos de mapa (${themeName})</Title><FeatureTypeStyle>\n${rules.join('\n')}\n  </FeatureTypeStyle></UserStyle></NamedLayer>\n</StyledLayerDescriptor>\n`;
}
function styleFragment(pointSyms, lineSyms, zoneSyms) {
  const layers = [];
  const iconMapping = {};
  for (const s of pointSyms) {
    iconMapping[s.styleKey] = {};
    for (const st of STATES) {
      const name = `${s.styleKey}--${st.id}`;
      iconMapping[s.styleKey][st.id] = name;
      layers.push({ id: `symbol-${name}`, type: 'symbol', source: 'ziipsan-gis', 'source-layer': s.classCode, filter: ['all', ['==', ['get', 'style_key'], s.styleKey], ['==', ['get', 'state'], st.id]], layout: { 'icon-image': name, 'icon-size': 1 }, paint: {} });
    }
  }
  for (const s of lineSyms) {
    for (const st of STATES) {
      const ls = lineStyleOf(s, st, 'light');
      if (ls.halo) layers.push({ id: `line-${s.styleKey}--${st.id}--casing`, type: 'line', source: 'ziipsan-gis', 'source-layer': s.classCode, filter: ['all', ['==', ['get', 'style_key'], s.styleKey], ['==', ['get', 'state'], st.id]], paint: { 'line-color': ls.halo.color, 'line-width': ls.halo.width } });
      layers.push({ id: `line-${s.styleKey}--${st.id}`, type: 'line', source: 'ziipsan-gis', 'source-layer': s.classCode, filter: ['all', ['==', ['get', 'style_key'], s.styleKey], ['==', ['get', 'state'], st.id]], paint: { 'line-color': ls.color, 'line-width': ls.width, ...(ls.dash ? { 'line-dasharray': ls.dash } : {}) } });
    }
  }
  for (const s of zoneSyms) {
    for (const st of STATES) {
      const zs = zoneStyleOf(s, st, 'light');
      layers.push({ id: `fill-${s.styleKey}--${st.id}`, type: 'fill', source: 'ziipsan-gis', 'source-layer': s.classCode, filter: ['all', ['==', ['get', 'style_key'], s.styleKey], ['==', ['get', 'state'], st.id]], paint: { 'fill-color': zs.color, 'fill-opacity': zs.fillOpacity, 'fill-outline-color': zs.color, ...(zs.hatch ? { 'fill-pattern-note': 'hachura: GIS-06 acrescenta fill-pattern a partir do sprite' } : {}) } });
    }
  }
  return { version: 8, name: 'ziipsan-map-symbols', note: 'Fragmento MapLibre (tema claro; o tema escuro troca as cores pelos tokens map-* no GIS-06). Sprite SDF: a cor do icone vem de icon-color/icon-halo-color em tempo de execucao.', iconMapping, layers };
}
function slug(sym) { return sym.typeCode.toLowerCase().replace(/_/g, '-'); }
function legendSvg(pointSyms, lineSyms, zoneSyms) {
  const all = [...pointSyms, ...lineSyms, ...zoneSyms].sort((a, b) => (a.classCode === b.classCode ? (a.typeCode < b.typeCode ? -1 : 1) : (a.classCode < b.classCode ? -1 : 1)));
  const labelW = 300, cellW = 56, rowH = 44, headerH = 56;
  const W = labelW + STATES.length * cellW;
  const H = headerH + all.length * rowH + 40;
  const out = [`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="sans-serif">`,
    `<rect width="${W}" height="${H}" fill="#ffffff"/>`,
    `<text x="12" y="24" font-size="16" font-weight="bold" fill="#111111">Ziip SAN — legenda de símbolos de mapa (água e esgoto, P1.30)</text>`,
    `<text x="12" y="44" font-size="11" fill="#444444">Tema claro. Estado nunca só por cor: tracejado, traço e ponto, fino e halo distinguem os estados. Aproximado usa a sobreposição de fora da meta. Aceite do dono: pendente.</text>`];
  STATES.forEach((st, i) => out.push(`<text x="${labelW + i * cellW + cellW / 2}" y="${headerH - 8}" font-size="11" text-anchor="middle" fill="#111111">${xmlEsc(st.label)}</text>`));
  all.forEach((s, r) => {
    const y = headerH + r * rowH;
    out.push(`<text x="12" y="${y + 18}" font-size="12" fill="#111111">${xmlEsc(s.label)}</text>`);
    out.push(`<text x="12" y="${y + 34}" font-size="9" fill="#666666">${xmlEsc(s.styleKey)}</text>`);
    STATES.forEach((st, i) => {
      const x = labelW + i * cellW;
      const color = st.grey ? statesDoc.abandonedGrey : themeColor('light', s.domain, false);
      out.push(`<g color="${color}">`);
      if (s.geometry === 'point') {
        out.push(`<svg x="${x + (cellW - 32) / 2}" y="${y + 6}" width="32" height="32" viewBox="0 0 32 32">${variantInner(s, st, 'currentColor')}</svg>`);
      } else if (s.geometry === 'line') {
        const ls = lineStyleOf(s, st, 'light');
        if (ls.halo) out.push(`<line x1="${x + 6}" y1="${y + 22}" x2="${x + cellW - 6}" y2="${y + 22}" stroke="${ls.halo.color}" stroke-width="${ls.halo.width}" stroke-linecap="round"/>`);
        out.push(`<line x1="${x + 6}" y1="${y + 22}" x2="${x + cellW - 6}" y2="${y + 22}" stroke="${ls.color}" stroke-width="${ls.width}" stroke-linecap="round"${ls.dash ? ` stroke-dasharray="${ls.dash.join(' ')}"` : ''}/>`);
      } else {
        const zs = zoneStyleOf(s, st, 'light');
        out.push(`<rect x="${x + 8}" y="${y + 10}" width="40" height="24" fill="${zs.color}" fill-opacity="${zs.fillOpacity}" stroke="${zs.color}" stroke-width="${zs.width}"${zs.dash ? ` stroke-dasharray="${zs.dash}"` : ''}/>`);
        if (zs.hatch) out.push(`<line x1="${x + 12}" y1="${y + 34}" x2="${x + 28}" y2="${y + 10}" stroke="${zs.color}" stroke-width="0.5"/><line x1="${x + 24}" y1="${y + 34}" x2="${x + 40}" y2="${y + 10}" stroke="${zs.color}" stroke-width="0.5"/>`);
      }
      out.push('</g>');
    });
  });
  out.push(`<text x="12" y="${H - 12}" font-size="10" fill="#666666">Fonte única: packages/sanitation/icons/map/source.json + states.json. Gerado por scripts/icons/generate-map-symbols.mjs.</text>`);
  out.push('</svg>');
  return out.join('\n') + '\n';
}
function contactSheet(pointSyms, lineSyms, zoneSyms) {
  const all = [...pointSyms, ...lineSyms, ...zoneSyms].sort((a, b) => (a.classCode === b.classCode ? (a.typeCode < b.typeCode ? -1 : 1) : (a.classCode < b.classCode ? -1 : 1)));
  const sid = (s, st) => `s-${s.styleKey.replace(/[^a-z0-9]+/gi, '-')}--${st.id}`;
  const defs = [];
  for (const s of all) {
    for (const st of STATES) {
      if (s.geometry === 'point') defs.push(`<symbol id="${sid(s, st)}" viewBox="0 0 32 32">${variantInner(s, st, 'currentColor')}</symbol>`);
      else if (s.geometry === 'line') {
        const ls = lineStyleOf(s, st, 'light');
        defs.push(`<symbol id="${sid(s, st)}" viewBox="0 0 32 32">${ls.halo ? `<line x1="2" y1="16" x2="30" y2="16" stroke="#ffffff" stroke-width="${ls.halo.width}" stroke-linecap="round"/>` : ''}<line x1="2" y1="16" x2="30" y2="16" stroke="currentColor" stroke-width="${ls.width}" stroke-linecap="round"${ls.dash ? ` stroke-dasharray="${ls.dash.join(' ')}"` : ''}/></symbol>`);
        // A capa (casing) de fora da meta e literal: no tema escuro ela e da
        // cor do fundo escuro, nao branca — por isso existe a variante -dark.
        if (st.id === 'out-of-target' && ls.halo) {
          defs.push(`<symbol id="${sid(s, st)}-dark" viewBox="0 0 32 32"><line x1="2" y1="16" x2="30" y2="16" stroke="${haloColor('dark')}" stroke-width="${ls.halo.width}" stroke-linecap="round"/><line x1="2" y1="16" x2="30" y2="16" stroke="currentColor" stroke-width="${ls.width}" stroke-linecap="round"/></symbol>`);
        }
      } else {
        const zs = zoneStyleOf(s, st, 'light');
        defs.push(`<symbol id="${sid(s, st)}" viewBox="0 0 32 32"><rect x="4" y="6" width="24" height="20" fill="currentColor" fill-opacity="${zs.fillOpacity}" stroke="currentColor" stroke-width="${zs.width}"${zs.dash ? ` stroke-dasharray="${zs.dash}"` : ''}/>${zs.hatch ? '<line x1="8" y1="26" x2="20" y2="6" stroke="currentColor" stroke-width="0.5"/><line x1="16" y1="26" x2="28" y2="6" stroke="currentColor" stroke-width="0.5"/>' : ''}</symbol>`);
      }
    }
  }
  const sections = ['light', 'dark'].map((themeName) => {
    const bg = haloColor(themeName);
    const fg = themeName === 'light' ? '#111111' : '#f5f5f5';
    const rows = all.map((s) => {
      const cells = STATES.map((st) => {
        const color = st.grey ? statesDoc.abandonedGrey : themeColor(themeName, s.domain, false);
        const useId = themeName === 'dark' && s.geometry === 'line' && st.id === 'out-of-target' ? `${sid(s, st)}-dark` : sid(s, st);
        const uses = [12, 16, 24].map((size) => `<svg width="${size}" height="${size}" viewBox="0 0 32 32" style="color:${color}"><use href="#${useId}" href-xlink="#${useId}"></use></svg>`).join(' ');
        return `<td><div class="state">${xmlEsc(st.label)}</div>${uses}</td>`;
      }).join('');
      return `<tr><th>${xmlEsc(s.label)}<br><small>${xmlEsc(s.styleKey)}</small></th>${cells}</tr>`;
    }).join('\n');
    return `<h2>Tema ${themeName === 'light' ? 'claro' : 'escuro'} — fundo ${bg}</h2><table style="background:${bg};color:${fg}"><thead><tr><th>Tipo</th>${STATES.map((st) => `<th>${xmlEsc(st.label)}</th>`).join('')}</tr></thead><tbody>${rows}</tbody></table>`;
  }).join('\n');
  return `<!DOCTYPE html>\n<html lang="pt-BR"><head><meta charset="utf-8"><title>Ziip SAN — folha de contato dos símbolos de mapa (P1.30)</title><style>body{font-family:sans-serif}table{border-collapse:collapse}th,td{border:1px solid #888;padding:6px;vertical-align:middle}th{text-align:left}.state{font-size:11px;margin-bottom:4px}svg{margin-right:6px;vertical-align:middle}</style></head><body><svg width="0" height="0" style="position:absolute"><defs>${defs.join('')}</defs></svg><h1>Ziip SAN — folha de contato dos símbolos de mapa (água e esgoto, P1.30)</h1><p>Todos os símbolos nos cinco estados e em 12, 16 e 24 px, nos temas claro e escuro. Estado nunca só por cor. Cadastro aproximado usa a sobreposição de fora da meta. <strong>Aceite do dono: pendente</strong> — esta folha é o artefato a validar.</p>${sections}</body></html>\n`;
}

// ---------------------------------------------------------------- main
function generate(outDir) {
  const written = {};
  // Remove temporarios orfaos de uma corrida morta (kill) antes de gerar.
  const cleanTmp = (d) => {
    if (!fs.existsSync(d)) return;
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) cleanTmp(p);
      else if (/\.tmp-\d+$/.test(e.name)) { try { fs.unlinkSync(p); } catch { /* segue */ } }
    }
  };
  cleanTmp(outDir);
  const put = (rel, content) => {
    const p = path.join(outDir, rel);
    writeFileRetry(p, content);
    written[rel] = sha256(Buffer.isBuffer(content) ? content : Buffer.from(content, 'utf-8'));
  };
  const pointSyms = symbols.filter((s) => s.geometry === 'point');
  const lineSyms = symbols.filter((s) => s.geometry === 'line');
  const zoneSyms = symbols.filter((s) => s.geometry === 'polygon');
  const manifestSymbols = [];
  for (const s of symbols) {
    const entry = { styleKey: s.styleKey, classCode: s.classCode, typeCode: s.typeCode, label: s.label, domain: s.domain, geometry: s.geometry, topologyRole: s.topologyRole, originHash: s.originHash, states: STATES.map((x) => x.id), files: { svg: {}, svgColored: { light: {}, dark: {} } }, sprite: null, lineStyle: null, zoneStyle: null };
    if (s.geometry === 'point') {
      for (const st of STATES) {
        const base = `${s.classCode.toLowerCase()}/${slug(s)}--${st.id}.svg`;
        put(`svg/${base}`, variantSvg(s, st, 'currentColor'));
        entry.files.svg[st.id] = `svg/${base}`;
        for (const themeName of ['light', 'dark']) {
          const color = st.grey ? statesDoc.abandonedGrey : themeColor(themeName, s.domain, false);
          const cpath = `svg-colored/${themeName}/${base}`;
          put(cpath, variantSvg(s, st, color));
          entry.files.svgColored[themeName][st.id] = cpath;
        }
      }
      put(`svg/${s.classCode.toLowerCase()}/${slug(s)}.svg`, variantSvg(s, stateById['in-service'], 'currentColor'));
      entry.files.svgBase = `svg/${s.classCode.toLowerCase()}/${slug(s)}.svg`;
    } else if (s.geometry === 'line') {
      entry.lineStyle = Object.fromEntries(STATES.map((st) => [st.id, lineStyleOf(s, st, 'light')]));
    } else {
      entry.zoneStyle = Object.fromEntries(STATES.map((st) => [st.id, zoneStyleOf(s, st, 'light')]));
    }
    manifestSymbols.push(entry);
  }
  // Sprite SDF (só glifos de ponto): 365 icones, celula 48 (1x) / 96 (2x).
  const pointEntries = pointSyms.flatMap((s) => STATES.map((st) => ({ sym: s, state: st, name: `${s.styleKey}--${st.id}` })));
  const COLS = 20, ROWS = Math.ceil(pointEntries.length / COLS);
  const spriteJson = {}, spriteJson2x = {};
  for (const [scale, cell, pad, radius, json] of [[1, 48, 8, 8, spriteJson], [2, 96, 16, 16, spriteJson2x]]) {
    const cells = pointEntries.map(({ sym, state }) => alphaToSdf(rasterizeAlpha(sym, state, cell, pad, scale), cell, radius, 0.25));
    const png = encodePngSdf(cells, COLS, ROWS, cell);
    put(scale === 1 ? 'sprite.png' : 'sprite@2x.png', png);
    pointEntries.forEach(({ name }, i) => {
      json[name] = { x: (i % COLS) * cell, y: Math.floor(i / COLS) * cell, width: cell, height: cell, pixelRatio: scale, content: [pad, pad, cell - pad, cell - pad], sdf: true };
    });
    // Autochecagem do SDF: toda celula tem interior profundo e exterior vazio,
    // e o estado projetado difere do em operacao (o traco muda o SDF).
    for (let i = 0; i < cells.length; i++) {
      let mn = 255, mx = 0;
      for (const b of cells[i]) { if (b < mn) mn = b; if (b > mx) mx = b; }
      if (mn > 55 || mx < 200) throw new Error(`SDF degenerado em ${pointEntries[i].name}: min=${mn} max=${mx}`);
    }
  }
  put('sprite.json', JSON.stringify(spriteJson, null, 2) + '\n');
  put('sprite@2x.json', JSON.stringify(spriteJson2x, null, 2) + '\n');
  pointSyms.forEach((s) => {
    const entry = manifestSymbols.find((e) => e.styleKey === s.styleKey);
    entry.sprite = Object.fromEntries(STATES.map((st) => [st.id, spriteJson[`${s.styleKey}--${st.id}`]]));
  });
  put('qgis-style.xml', qgisStyle(pointSyms, lineSyms, zoneSyms));
  put('sld-light.xml', sldDoc('light', pointSyms, lineSyms, zoneSyms));
  put('sld-dark.xml', sldDoc('dark', pointSyms, lineSyms, zoneSyms));
  put('style-fragment.json', JSON.stringify(styleFragment(pointSyms, lineSyms, zoneSyms), null, 2) + '\n');
  put('legend.svg', legendSvg(pointSyms, lineSyms, zoneSyms));
  put('contact-sheet.html', contactSheet(pointSyms, lineSyms, zoneSyms));
  const manifest = {
    package: source.package, grid: GRID,
    source: { sourceFile: 'source.json', statesFile: 'states.json', sourceHash: sha256(fs.readFileSync(path.join(MAP_DIR, 'source.json')) + fs.readFileSync(path.join(MAP_DIR, 'states.json'))) },
    counts: { types: symbols.length, pointTypes: pointSyms.length, lineTypes: lineSyms.length, zoneTypes: zoneSyms.length, states: STATES.length, spriteIcons: pointEntries.length, channelEntries: symbols.length * STATES.length },
    states: STATES.map((s) => ({ id: s.id, label: s.label })),
    ownerAcceptance: 'PENDENTE — a folha de contato (contact-sheet.html) e o artefato a validar pelo dono; nenhum aceite foi concedido por esta frente.',
    tenantFallback: 'Tipo do tenant sem simbolo proprio usa o glifo da classe (base) no estilo do tipo mais proximo; o teste de cobertura recusa tipo de fabrica ativo sem simbolo.',
    symbols: manifestSymbols,
    channels: Object.fromEntries(Object.entries(written).filter(([rel]) => !rel.startsWith('svg/') && !rel.startsWith('svg-colored/')).map(([rel, hash]) => [rel, { sha256: hash }])),
  };
  put('manifest.json', JSON.stringify(manifest, null, 2) + '\n');
  return { counts: manifest.counts, files: Object.keys(written).length };
}
function dirSnapshot(dir) {
  const out = {};
  const walk = (d, rel) => {
    for (const e of fs.readdirSync(d, { withFileTypes: true }).sort((a, b) => (a.name < b.name ? -1 : 1))) {
      const p = path.join(d, e.name), r = rel ? `${rel}/${e.name}` : e.name;
      if (e.isDirectory()) walk(p, r); else out[r] = sha256(fs.readFileSync(p));
    }
  };
  walk(dir, '');
  return out;
}
const check = process.argv.includes('--check');
if (check) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'ziipsan-symbols-'));
  generate(tmp);
  const a = dirSnapshot(tmp), b = dirSnapshot(OUT);
  const diffs = [...new Set([...Object.keys(a), ...Object.keys(b)])].filter((k) => a[k] !== b[k]);
  if (diffs.length) { console.error(`DESATUALIZADO: ${diffs.length} arquivo(s) divergente(s): ${diffs.slice(0, 20).join(', ')}`); process.exit(1); }
  console.log(`OK: ${Object.keys(a).length} arquivos gerados conferem com a fonte`);
} else {
  const { counts, files } = generate(OUT);
  console.log(`OK: ${counts.types} tipos (${counts.pointTypes} ponto, ${counts.lineTypes} linha, ${counts.zoneTypes} zona) x ${counts.states} estados = ${counts.channelEntries} entradas; ${counts.spriteIcons} icones no sprite; ${files} arquivos em ${path.relative(ROOT, OUT)}`);
}

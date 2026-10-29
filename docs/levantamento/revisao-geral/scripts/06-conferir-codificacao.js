// Confere UTF-8 válido, ausência de U+FFFD, de CR e de BOM nos arquivos indicados.
// Uso: node 06-conferir-codificacao.js arquivo1 [arquivo2 ...]
"use strict";
const fs = require("fs");
let erro = 0;
for (const f of process.argv.slice(2)) {
  const b = fs.readFileSync(f);
  const s = new TextDecoder("utf-8", { fatal: false }).decode(b);
  let valido = true;
  try { new TextDecoder("utf-8", { fatal: true }).decode(b); } catch { valido = false; }
  const fffd = (s.match(new RegExp(String.fromCharCode(0xFFFD), "g")) || []).length, cr = b.includes(13), bom = b[0] === 0xef && b[1] === 0xbb && b[2] === 0xbf;
  const ok = valido && !fffd && !cr && !bom;
  if (!ok) erro++;
  console.log(`${ok ? "OK  " : "ERRO"} ${f}: utf8=${valido} U+FFFD=${fffd} CR=${cr} BOM=${bom} bytes=${b.length}`);
}
process.exit(erro ? 1 : 0);

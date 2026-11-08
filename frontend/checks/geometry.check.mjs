// Conferencia executavel do espelho TS de GisGeom (correcao D6,
// revisao ED-53) — roda SEM build do frontend:
//   node --experimental-strip-types checks/geometry.check.mjs
// (a partir de entrega/frontend/; Node >= 22 pelo strip-types).
// Prova: split nas extremidades RECUSADO dos dois lados do
// espelho; split em vertice existente sem ponto duplicado
// (igual ao Java); split no meio e merge corretos.
import {
  splitLineAt,
  mergeLines,
} from "../src/utils/geometry.ts";

let failures = 0;
function check(name, cond) {
  console.log(`${cond ? "OK  " : "FAIL"} ${name}`);
  if (!cond) failures++;
}
function refuses(fn) {
  try { fn(); return false; } catch { return true; }
}

const line = [{ x: 0, y: 0 }, { x: 10, y: 0 }];
check("split no inicio recusado",
  refuses(() => splitLineAt(line, { x: 0, y: 0 }, 0.01)));
check("split no fim recusado",
  refuses(() => splitLineAt(line, { x: 10, y: 0 }, 0.01)));
const atVertex = splitLineAt(
  [{ x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 }], { x: 10, y: 0 }, 0.01);
check("split em vertice: trechos de 2 pontos, sem duplicar",
  atVertex.first.length === 2 && atVertex.second.length === 2
  && atVertex.node.x === 10 && atVertex.node.y === 0);
const mid = splitLineAt(line, { x: 4, y: 0 }, 0.01);
check("split no meio compartilha o no",
  mid.first[mid.first.length - 1].x === 4 && mid.second[0].x === 4);
check("merge une na extremidade comum",
  mergeLines(line, [{ x: 20, y: 0 }, { x: 10, y: 0 }], 0.01).length === 3);
process.exit(failures === 0 ? 0 : 1);

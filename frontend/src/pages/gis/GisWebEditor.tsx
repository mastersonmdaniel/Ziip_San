import React from "react";
import { webEdit, type EditTool } from "../../services/gis/webEdit";
import { SchemaForm } from "./SchemaForm";
import type { FormField } from "../../services/gis/webEdit";
import {
  centroid,
  dist,
  insertNodeAt,
  mergeLines,
  moveVertex,
  rotate,
  scale,
  splitLineAt,
  toLineWkt,
  toPointWkt,
  toPolygonWkt,
  translate,
  type Pt,
} from "../../utils/geometry";

/**
 * Tela do editor web completo (GIS-09, ED-53): ferramentas de
 * ponto, linha e poligono com vertices, mover, rotacionar e
 * escalar (SG-838/SG-839, TR-1026/TR-1027), dividir e unir arcos
 * e inserir no que segmenta.
 *
 * MODELO DE INTERACCAO POR RATO (nota do dono, 10/11/2026 03:37,
 * decisao 65 — frente 5 do momento final do front): "a
 * movimentacao de elementos e os desenhos fazem-se VIA MOUSE, nao
 * com botoes". As ferramentas seleccionam o MODO de interaccao e
 * NAO executam geometria: mover e arrastar a feicao ou um vertice
 * com o rato sobre a area de desenho; desenhar e clicar com o
 * rato a definir vertices; rodar/escalar e arrastar em torno do
 * centroide; dividir/inserir no e clicar sobre o arco no ponto
 * pretendido; unir e clicar os vertices do arco de continuacao e
 * fechar com duplo clique. A versao anterior executava a
 * geometria no clique do botao da ferramenta, com parametros
 * fixos (mover +1,0; rodar 15 graus; escalar x1,1; dividir/unir/
 * inserir no ponto medio do primeiro segmento) e tinha um botao
 * "Mover 1o vertice (+1, +1)" — removidos por esta adaptacao.
 *
 * ESTADO REAL DESTA TELA (correcao D8 da revisao ED-53 — sem
 * ✅ onde e esqueleto):
 * - LIGADO: as ferramentas de geometria operam de verdade sobre
 *   a feicao de trabalho LOCAL usando as operacoes puras de
 *   utils/geometry.ts (o mesmo espelho TS de GisGeom provado no
 *   backend), agora accionadas por rato directo na area de
 *   desenho local desta pagina, com pre-visualizacao em WKT; o
 *   botao "Conferir na API" manda para o servico o deslocamento
 *   que o rato fez (com layerId — sem permissao camada x
 *   operacao o servico devolve 403, D1); o formulario de exemplo
 *   e gerado pelo endpoint /forms/from-schema; as permissoes vem
 *   de /layer-permissions/mine.
 * - ESQUELETO (NAO ligado): a renderizacao/desenho no mapa da
 *   GIS-06 (P3.25, MapLibre) — a area de desenho abaixo e a
 *   superficie LOCAL da feicao de trabalho, nao e o mapa da
 *   GIS-06; a gravacao da feicao editada em pacote da GIS-04 e a
 *   tela de pacotes (ChangeSetReview da P2.22) tambem ficam fora
 *   desta pagina. Os endpoints de camada do usuario, juncao,
 *   projeto, unidades e sync estao expostos no servico TS
 *   (services/gis/webEdit.ts) mas sem formulario proprio nesta
 *   pagina.
 */
const TOOLS: { id: EditTool; label: string }[] = [
  { id: "select", label: "Selecionar" },
  { id: "point", label: "Ponto" },
  { id: "line", label: "Linha" },
  { id: "polygon", label: "Poligono" },
  { id: "move", label: "Mover" },
  { id: "rotate", label: "Rotacionar" },
  { id: "scale", label: "Escalar" },
  { id: "split", label: "Dividir arco" },
  { id: "merge", label: "Unir arcos" },
  { id: "insert-node", label: "Inserir no" },
];

// Instrucao do MODO seleccionado: a ferramenta nao executa nada;
// quem executa e o rato sobre a area de desenho.
const MODE_HINTS: Record<EditTool, string> = {
  select: "Arraste um vertice com o rato para o mover.",
  point: "Clique com o rato na area de desenho para colocar o ponto.",
  line: "Clique com o rato na area de desenho para definir cada vertice da linha.",
  polygon:
    "Clique com o rato para definir os vertices; clique junto ao primeiro vertice para fechar o poligono.",
  move: "Arraste a feicao (ou um vertice) com o rato para a mover.",
  rotate: "Arraste com o rato em torno do centro da feicao para a rotacionar.",
  scale: "Arraste com o rato para longe/perto do centro da feicao para a escalar.",
  split: "Clique com o rato sobre o arco, no ponto onde o quer dividir.",
  merge:
    "Clique com o rato a definir o arco de continuacao a partir de uma extremidade da feicao; duplo clique fecha e une.",
  "insert-node": "Clique com o rato sobre o arco, no ponto onde quer inserir o no.",
};

// Janela da area de desenho, nas unidades da geometria de
// trabalho (a feicao inicial ocupa 0..10 em x e y).
export const EDITOR_VIEW = { minX: -5, minY: -5, width: 30, height: 30 };

// Tolerancia de clique do rato, em unidades da geometria: a
// distancia maxima ao arco/vertice para o clique contar como
// "sobre" ele (dividir, inserir no, fechar poligono, unir). E um
// parametro da camada de interaccao por rato — as funcoes puras
// de utils/geometry.ts continuam a receber a tolerancia por
// argumento, sem alteracao.
export const PICK_TOLERANCE = 0.75;

/** Converte um evento de rato (coordenadas do cliente) para
 * coordenadas da geometria, dada a caixa da area de desenho. */
export function clientToGeometry(
  clientX: number,
  clientY: number,
  rect: { left: number; top: number; width: number; height: number },
  view: typeof EDITOR_VIEW = EDITOR_VIEW,
): Pt {
  // A camada de interaccao quantiza o clique para 7 casas
  // decimais — a mesma precisao que o espelho de geometria emite
  // em WKT (fmt de utils/geometry.ts) — e normaliza o -0.
  const round7 = (v: number) => Math.round(v * 1e7) / 1e7 + 0;
  return {
    x: round7(view.minX + ((clientX - rect.left) / rect.width) * view.width),
    y: round7(view.minY + (1 - (clientY - rect.top) / rect.height) * view.height),
  };
}

/** Converte um ponto da geometria para coordenadas do viewBox
 * SVG da area de desenho (y invertido: geometria cresce para
 * cima, SVG para baixo). */
export function geometryToSvg(p: Pt, view: typeof EDITOR_VIEW = EDITOR_VIEW): Pt {
  return { x: p.x - view.minX, y: view.height - (p.y - view.minY) };
}

// Esquema de EXEMPLO para demonstrar o formulario gerado — na
// integracao, o esquema vem do attributes_schema do tipo da
// feicao selecionada (o backend valida contra o esquema real do
// banco, correcao D2; este exemplo nao valida nada sozinho).
const SAMPLE_SCHEMA: Record<string, unknown> = {
  type: "object",
  required: ["diameter_mm"],
  properties: {
    diameter_mm: {
      type: "integer", title: "Diametro (mm)", minimum: 20, maximum: 2000,
    },
    material: {
      type: "string", title: "Material", enum: ["PVC", "PEAD", "FOFO"],
    },
  },
};

type DragState =
  | { kind: "feature"; last: Pt }
  | { kind: "vertex"; index: number }
  | { kind: "transform"; last: Pt }
  | null;

function shapeWkt(shape: Pt[]): string {
  if (shape.length === 0) return "(sem geometria)";
  if (shape.length === 1) return toPointWkt(shape[0]);
  const first = shape[0];
  const last = shape[shape.length - 1];
  if (shape.length >= 4 && first.x === last.x && first.y === last.y) {
    return toPolygonWkt(shape);
  }
  return toLineWkt(shape);
}

export function GisWebEditor() {
  const [tool, setTool] = React.useState<EditTool>("select");
  const [fields, setFields] = React.useState<FormField[]>([]);
  const [values, setValues] = React.useState<Record<string, unknown>>({});
  const [status, setStatus] = React.useState<string>("");
  // Feicao de trabalho local (linha) sobre a qual as ferramentas
  // de geometria pura operam de verdade.
  const [shape, setShape] = React.useState<Pt[]>([
    { x: 0, y: 0 }, { x: 10, y: 0 }, { x: 10, y: 10 },
  ]);
  const [preview, setPreview] = React.useState<string>("");
  const [layerId, setLayerId] = React.useState<string>("");
  // Arco de continuacao em desenho no modo "unir arcos".
  const [draft, setDraft] = React.useState<Pt[]>([]);
  // Deslocamento acumulado do ultimo arrasto de mover — e ele
  // (feito com o rato) que o "Conferir na API" manda ao servico.
  const [lastDragDelta, setLastDragDelta] = React.useState<Pt | null>(null);
  const dragRef = React.useRef<DragState>(null);
  // Nos modos de desenho (ponto/linha/poligono), o primeiro
  // clique apos seleccionar a ferramenta comeca uma feicao nova;
  // os seguintes acrescentam vertices a essa feicao.
  const drawingRef = React.useRef<boolean>(false);

  React.useEffect(() => {
    webEdit
      .myLayerPermissions()
      .then((perms) =>
        setStatus(
          perms.length === 0
            ? "Sem permissoes de camada (SG-864/SG-865)."
            : `${perms.length} camada(s) com permissao.`,
        ),
      )
      .catch(() => setStatus("Permissoes de camada indisponiveis."));
  }, []);

  async function loadForm(schema: Record<string, unknown>) {
    const form = await webEdit.formFromSchema(schema);
    setFields(form.fields);
  }

  // Seleccionar a ferramenta so muda o MODO de interaccao do
  // rato (nota do dono, decisao 65): nao executa geometria.
  function selectTool(selected: EditTool) {
    setTool(selected);
    dragRef.current = null;
    drawingRef.current = false;
    setDraft([]);
    setPreview(MODE_HINTS[selected]);
  }

  function pointerGeometry(e: React.PointerEvent<SVGSVGElement>): Pt {
    return clientToGeometry(
      e.clientX,
      e.clientY,
      e.currentTarget.getBoundingClientRect(),
    );
  }

  function handleCanvasPointerDown(e: React.PointerEvent<SVGSVGElement>) {
    const at = pointerGeometry(e);
    try {
      switch (tool) {
        case "point":
          setShape([at]);
          drawingRef.current = false;
          setPreview(`ponto: ${toPointWkt(at)}`);
          break;
        case "line":
          if (!drawingRef.current) {
            setShape([at]);
            drawingRef.current = true;
          } else {
            setShape((s) => [...s, at]);
          }
          break;
        case "polygon":
          if (!drawingRef.current) {
            setShape([at]);
            drawingRef.current = true;
          } else if (shape.length >= 3 && dist(at, shape[0]) <= PICK_TOLERANCE) {
            const ring = [...shape, shape[0]];
            setShape(ring);
            drawingRef.current = false;
            setPreview(`poligono: ${toPolygonWkt(ring)}`);
          } else {
            setShape((s) => [...s, at]);
          }
          break;
        case "move":
          dragRef.current = { kind: "feature", last: at };
          setLastDragDelta({ x: 0, y: 0 });
          break;
        case "rotate":
        case "scale":
          dragRef.current = { kind: "transform", last: at };
          break;
        case "split": {
          const r = splitLineAt(shape, at, PICK_TOLERANCE);
          setShape(r.first);
          setPreview(`segundo trecho: ${toLineWkt(r.second)}`);
          break;
        }
        case "insert-node": {
          const r = insertNodeAt(shape, at, PICK_TOLERANCE);
          setShape([...r.first, ...r.second.slice(1)]);
          setPreview(`no inserido: ${r.node.x}, ${r.node.y}`);
          break;
        }
        case "merge":
          setDraft((d) => {
            // O primeiro clique junto a uma extremidade da feicao
            // encaixa nela (snap do rato): unir exige extremidade
            // comum (mergeLines).
            if (d.length === 0) {
              const first = shape[0];
              const last = shape[shape.length - 1];
              if (dist(at, first) <= PICK_TOLERANCE) return [first];
              if (dist(at, last) <= PICK_TOLERANCE) return [last];
            }
            return [...d, at];
          });
          break;
        default:
          // select: a interaccao e arrastar vertices (pegas abaixo).
          break;
      }
    } catch (err) {
      setPreview(`Operacao recusada: ${err instanceof Error ? err.message : err}`);
    }
  }

  function handleCanvasPointerMove(e: React.PointerEvent<SVGSVGElement>) {
    const drag = dragRef.current;
    if (!drag) return;
    const at = pointerGeometry(e);
    try {
      if (drag.kind === "feature") {
        const dx = at.x - drag.last.x;
        const dy = at.y - drag.last.y;
        drag.last = at;
        if (dx !== 0 || dy !== 0) {
          setShape((s) => translate(s, dx, dy));
          setLastDragDelta((d) => ({ x: (d?.x ?? 0) + dx, y: (d?.y ?? 0) + dy }));
        }
      } else if (drag.kind === "vertex") {
        setShape((s) => moveVertex(s, drag.index, at));
      } else if (tool === "rotate") {
        const c = centroid(shape);
        const prev = drag.last;
        drag.last = at;
        const a0 = Math.atan2(prev.y - c.y, prev.x - c.x);
        const a1 = Math.atan2(at.y - c.y, at.x - c.x);
        const degrees = ((a1 - a0) * 180) / Math.PI;
        if (Number.isFinite(degrees) && degrees !== 0) {
          setShape((s) => rotate(s, centroid(s), degrees));
        }
      } else if (tool === "scale") {
        const c = centroid(shape);
        const prevDist = dist(drag.last, c);
        drag.last = at;
        const nextDist = dist(at, c);
        if (prevDist > 0 && nextDist > 0) {
          const factor = nextDist / prevDist;
          if (factor !== 1) setShape((s) => scale(s, centroid(s), factor));
        }
      }
    } catch (err) {
      setPreview(`Operacao recusada: ${err instanceof Error ? err.message : err}`);
    }
  }

  function endDrag() {
    dragRef.current = null;
  }

  function handleCanvasDoubleClick() {
    if (tool !== "merge") return;
    if (draft.length < 2) {
      setPreview("Unir arcos: defina ao menos 2 pontos do arco de continuacao com o rato.");
      return;
    }
    try {
      setShape((s) => mergeLines(s, draft, PICK_TOLERANCE));
      setDraft([]);
      setPreview("arcos unidos");
    } catch (err) {
      setPreview(`Operacao recusada: ${err instanceof Error ? err.message : err}`);
    }
  }

  function handleVertexPointerDown(index: number) {
    return (e: React.PointerEvent<SVGCircleElement>) => {
      // A pega do vertice tem prioridade sobre o fundo da area de
      // desenho: arrastar a pega move o vertice com o rato.
      e.stopPropagation();
      if (tool === "select" || tool === "move") {
        dragRef.current = { kind: "vertex", index };
      }
    };
  }

  // A mesma transformacao, conferida pelo servico (com layerId):
  // confere o deslocamento que o RATO fez no ultimo arrasto de
  // mover — nao ha deslocamento fixo por botao.
  async function checkOnApi() {
    if (!layerId) {
      setStatus("Informe o layerId para conferir na API (a guarda D1 exige a camada).");
      return;
    }
    if (!lastDragDelta) {
      setStatus("Mova a feicao com o rato (modo Mover) antes de conferir na API.");
      return;
    }
    if (shape.length < 2) {
      setStatus("A feicao actual nao e uma linha para conferir na API.");
      return;
    }
    try {
      const out = await webEdit.transform({
        geomWkt: toLineWkt(shape),
        mode: "MOVE",
        dx: lastDragDelta.x,
        dy: lastDragDelta.y,
        layerId,
      });
      setStatus(`API conferiu: ${out.geomWkt}`);
    } catch (e) {
      setStatus(`API recusou: ${e instanceof Error ? e.message : e}`);
    }
  }

  const shapeSvgPoints = shape.map((p) => geometryToSvg(p));
  const draftSvgPoints = draft.map((p) => geometryToSvg(p));
  const pointsAttr = (pts: Pt[]) => pts.map((p) => `${p.x},${p.y}`).join(" ");

  return (
    <section className="gis-web-editor">
      <header>
        <h1>Editor GIS</h1>
        <p role="status">{status}</p>
        <p className="gis-skeleton-note">
          Parcial: geometria local operada com o rato e endpoints
          ligados; desenho no mapa (GIS-06) e gravacao em pacote
          (GIS-04) sao esqueleto fora desta pagina — ver o cabecalho
          do arquivo.
        </p>
      </header>
      <div className="gis-tools" role="toolbar" aria-label="Ferramentas de edicao">
        {TOOLS.map((t) => (
          <button
            key={t.id}
            type="button"
            aria-pressed={tool === t.id}
            onClick={() => selectTool(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>
      <p className="gis-mode-hint">{MODE_HINTS[tool]}</p>
      <svg
        className="gis-editor-canvas"
        data-testid="gis-editor-canvas"
        role="application"
        aria-label="Area de desenho do editor GIS (interaccao por rato)"
        viewBox={`0 0 ${EDITOR_VIEW.width} ${EDITOR_VIEW.height}`}
        width={480}
        height={480}
        style={{ background: "#f4f6f8", touchAction: "none" }}
        onPointerDown={handleCanvasPointerDown}
        onPointerMove={handleCanvasPointerMove}
        onPointerUp={endDrag}
        onPointerLeave={endDrag}
        onDoubleClick={handleCanvasDoubleClick}
      >
        {shapeSvgPoints.length >= 2 ? (
          <polyline
            points={pointsAttr(shapeSvgPoints)}
            fill="none"
            stroke="#00548f"
            strokeWidth={0.35}
          />
        ) : null}
        {draftSvgPoints.length >= 2 ? (
          <polyline
            points={pointsAttr(draftSvgPoints)}
            fill="none"
            stroke="#0090d0"
            strokeWidth={0.3}
            strokeDasharray="1 0.7"
          />
        ) : null}
        {shapeSvgPoints.map((p, i) => (
          <circle
            key={i}
            data-testid={`vertex-${i}`}
            cx={p.x}
            cy={p.y}
            r={0.7}
            fill="#ffffff"
            stroke="#00548f"
            strokeWidth={0.25}
            onPointerDown={handleVertexPointerDown(i)}
          />
        ))}
        {draftSvgPoints.map((p, i) => (
          <circle key={`draft-${i}`} cx={p.x} cy={p.y} r={0.55} fill="#0090d0" />
        ))}
      </svg>
      <div className="gis-local-preview">
        <p>Feicao de trabalho (geometria pura local): {shapeWkt(shape)}</p>
        <label>
          layerId{" "}
          <input
            value={layerId}
            onChange={(e) => setLayerId(e.target.value)}
            placeholder="uuid da camada"
          />
        </label>
        <button type="button" onClick={checkOnApi}>
          Conferir na API o deslocamento feito com o rato
        </button>
        {preview ? <p>{preview}</p> : null}
      </div>
      <button type="button" onClick={() => void loadForm(SAMPLE_SCHEMA)}>
        Gerar formulario do esquema de exemplo
      </button>
      <SchemaForm
        fields={fields}
        values={values}
        onChange={(name, value) =>
          setValues((v) => ({ ...v, [name]: value }))
        }
      />
    </section>
  );
}

export default GisWebEditor;

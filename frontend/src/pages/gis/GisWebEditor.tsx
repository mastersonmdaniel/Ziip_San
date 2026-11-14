import React from "react";
import * as maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { webEdit, type EditTool } from "../../services/gis/webEdit";
import { gisMap } from "../../services/gis/map";
import { readMapTokens, resolveTokenColors } from "../../services/gis/mapStyle";
import {
  polylinesFromGeoJsonGeometry,
  snapPoint,
  type GeoJsonGeometryLike,
  type SnapTarget,
} from "../../services/gis/editorSnap";
import {
  changeSets,
  geometryItemInput,
  CHANGE_SET_FEATURE_TABLES,
} from "../../services/gis/changeSets";
import { SchemaForm } from "./SchemaForm";
import type { FormField } from "../../services/gis/webEdit";
import { useCan } from "@hooks/usePermissions";
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
 * - LIGADO (Ciclo 3 do PDCA GIS, P-GISROTAS + E2-SNAP): o
 *   desenho tambem acontece sobre o mapa MapLibre real da
 *   GIS-06 (estilo gerado, mesma fiacao do GisMap) — os cliques
 *   e arrastos no mapa alimentam a MESMA feicao de trabalho e
 *   as MESMAS ferramentas, e a feicao e desenhada no mapa por
 *   uma fonte GeoJSON; o snap a vertices/bordas
 *   (services/gis/editorSnap.ts) vale ao desenhar e ao arrastar
 *   vertices nas duas superficies (no mapa, os alvos incluem as
 *   feicoes renderizadas e o snap calcula-se em pixeis); a
 *   gravacao da feicao editada em pacote da GIS-04 esta ligada
 *   nesta pagina (criar pacote -> item com a geometria ->
 *   submeter; a revisao e por outra pessoa, guarda do backend).
 *   Se o mapa nao carregar, a superficie local continua a
 *   funcionar e o estado declara o mapa indisponivel.
 * - ESQUELETO (NAO ligado): os endpoints de camada do usuario,
 *   juncao, projeto, unidades e sync estao expostos no servico
 *   TS (services/gis/webEdit.ts) mas sem formulario proprio
 *   nesta pagina.
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

// Tolerancia do snap na superficie do mapa, em PIXEIS de ecra
// (Ciclo 3, E2-SNAP): e um parametro da camada de interaccao
// (rato/toque), como o PICK_TOLERANCE acima — nao e limiar de
// negocio. Em pixeis, o snap vale o mesmo em qualquer zoom e o
// toque (dedo) beneficia mais que o rato.
export const SNAP_TOLERANCE_PX = 12;

// Fonte/camadas GeoJSON da feicao de trabalho sobre o mapa real.
const EDITOR_SOURCE = "gis-editor-work";
const EDITOR_LINE_LAYER = "gis-editor-work-line";
const EDITOR_POINT_LAYER = "gis-editor-work-point";

// Tipos de pacote validos da GIS-04 (espelho de
// GisChangeSetLifecycle.CHANGE_SET_TYPES, backend — a
// autoridade e o servidor; a lista so monta o select).
const CHANGE_SET_TYPES = [
  "PROJECT", "AS_BUILT", "CORRECTION", "IMPORT", "FIELD", "QGIS",
  "SERVICE_ORDER", "AUTO_SERVICE_LINE",
];

/** FeatureCollection da feicao de trabalho + rascunho (unir
 * arcos) + vertices, para a fonte GeoJSON do mapa real. */
export function editorFeatureCollection(shape: Pt[], draft: Pt[]) {
  const features: Record<string, unknown>[] = [];
  const pushLine = (pts: Pt[], kind: string) => {
    if (pts.length === 1) {
      features.push({
        type: "Feature",
        properties: { kind },
        geometry: { type: "Point", coordinates: [pts[0].x, pts[0].y] },
      });
    } else if (pts.length >= 2) {
      features.push({
        type: "Feature",
        properties: { kind },
        geometry: {
          type: "LineString",
          coordinates: pts.map((p) => [p.x, p.y]),
        },
      });
    }
  };
  pushLine(shape, "shape");
  pushLine(draft, "draft");
  for (const p of shape) {
    features.push({
      type: "Feature",
      properties: { kind: "vertex" },
      geometry: { type: "Point", coordinates: [p.x, p.y] },
    });
  }
  return { type: "FeatureCollection", features };
}

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
  // R41-4-35: guarda client-side de escrita. A escrita GIS vigente e
  // `gis:import-edit` (GisWebEditRoles.EDIT, backend); gravar em pacote
  // exige `gis:change-set-edit` (GisChangeSetController.create). Sem
  // elas o editor e so de consulta: o servidor nega a gravacao com 403,
  // mas a tela nao deve convidar a editar trabalho que nunca pode ser
  // gravado. Sem provider, useCan nega tudo (fail-closed) — o editor
  // nasce so de leitura. A entrada do menu fica visivel: o editor e
  // tambem a superficie de leitura de `gis:view`.
  const canEdit = useCan("gis:import-edit");
  const canSave = useCan("gis:change-set-edit");
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
  // Snap a vertices/bordas (E2-SNAP): ligado por omissao; e
  // parametro de interaccao, nao executa geometria sozinho.
  const [snapEnabled, setSnapEnabled] = React.useState<boolean>(true);
  const [snapNote, setSnapNote] = React.useState<string>("");
  // Superficie do mapa real (GIS-06) e o seu estado declarado.
  const mapContainerRef = React.useRef<HTMLDivElement>(null);
  const mapRef = React.useRef<maplibregl.Map | null>(null);
  const [mapStatus, setMapStatus] = React.useState<string>("Mapa GIS-06 a carregar…");
  // Gravacao em pacote GIS-04: identidade da feicao editada,
  // dada pelo utilizador (o servico exige feicao existente para
  // UPDATE/RETIRE; sem identidade nao se grava — ver o handler).
  const [csTable, setCsTable] = React.useState<string>("gis_arc");
  const [csFeatureId, setCsFeatureId] = React.useState<string>("");
  const [csOperation, setCsOperation] = React.useState<"INSERT" | "UPDATE" | "RETIRE">("UPDATE");
  const [csType, setCsType] = React.useState<string>("CORRECTION");
  const [csStatus, setCsStatus] = React.useState<string>("");
  const [csSaving, setCsSaving] = React.useState<boolean>(false);
  // Arco de continuacao em desenho no modo "unir arcos".
  const [draft, setDraft] = React.useState<Pt[]>([]);
  // Deslocamento acumulado do ultimo arrasto de mover — e ele
  // (feito com o rato) que o "Conferir na API" manda ao servico.
  const [lastDragDelta, setLastDragDelta] = React.useState<Pt | null>(null);
  const dragRef = React.useRef<DragState>(null);
  // Espelhos para os handlers do MapLibre (registados uma vez,
  // na criacao do mapa): apontam sempre para a renderizacao
  // mais recente e para a feicao corrente.
  const shapeRef = React.useRef<Pt[]>([]);
  const mapHandlersRef = React.useRef<{
    click: (e: maplibregl.MapMouseEvent) => void;
    down: (e: maplibregl.MapMouseEvent) => void;
    move: (e: maplibregl.MapMouseEvent) => void;
    up: () => void;
    dbl: (e: maplibregl.MapMouseEvent) => void;
  }>({ click: () => {}, down: () => {}, move: () => {}, up: () => {}, dbl: () => {} });
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

  // Ferramentas cujo clique DEFINE geometria: sao as que o snap
  // serve (desenhar e clicar sobre o arco). Mover/rodar/escalar
  // comecam arrastos — o snap aplica-se ao arrasto de vertice.
  const SNAP_CLICK_TOOLS: EditTool[] = ["point", "line", "polygon", "split", "insert-node", "merge"];

  // Snap na superficie LOCAL (unidades da geometria): alvos sao
  // a feicao de trabalho e o rascunho de uniao.
  function snapLocal(at: Pt, excludeVertex?: number): Pt {
    if (!snapEnabled) return at;
    const targets: SnapTarget[] = [];
    const own = excludeVertex === undefined
      ? shape
      : shape.filter((_, i) => i !== excludeVertex);
    if (own.length > 0) targets.push({ points: own });
    if (draft.length > 0) targets.push({ points: draft });
    const r = snapPoint(at, targets, PICK_TOLERANCE);
    setSnapNote(r.kind ? `snap: ${r.kind === "vertex" ? "vértice" : "borda"}` : "");
    return r.point;
  }

  // Nucleo de interaccao por rato/toque, agnostico da
  // superficie: a area local e o mapa real chamam-no com o
  // ponto ja convertido (e ja com snap, quando aplicavel).
  function pointerDownAt(rawAt: Pt) {
    // R41-4-35: sem escrita, o rato nao define geometria.
    if (!canEdit) return;
    const at = rawAt;
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

  function handleCanvasPointerDown(e: React.PointerEvent<SVGSVGElement>) {
    const raw = pointerGeometry(e);
    const at = SNAP_CLICK_TOOLS.includes(tool) ? snapLocal(raw) : raw;
    pointerDownAt(at);
  }

  // Nucleo do arrasto, agnostico da superficie. O snap do
  // arrasto de vertice e aplicado por quem chama (cada
  // superficie snap no seu espaco), por isso aqui o ponto ja
  // vem final.
  function pointerMoveAt(at: Pt) {
    // R41-4-35: sem escrita, o arrasto nao move nada.
    if (!canEdit) return;
    const drag = dragRef.current;
    if (!drag) return;
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

  function handleCanvasPointerMove(e: React.PointerEvent<SVGSVGElement>) {
    const drag = dragRef.current;
    if (!drag) return;
    const raw = pointerGeometry(e);
    const at = drag.kind === "vertex" ? snapLocal(raw, drag.index) : raw;
    pointerMoveAt(at);
  }

  function endDrag() {
    dragRef.current = null;
  }

  function handleCanvasDoubleClick() {
    // R41-4-35: sem escrita, o duplo clique nao une arcos.
    if (!canEdit) return;
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
      // R41-4-35: sem escrita, a pega do vertice nao arrasta nada.
      if (!canEdit) return;
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
    // R41-4-35: sem escrita, nao ha deslocamento para conferir.
    if (!canEdit) {
      setStatus("Sem a permissão gis:import-edit, a conferência na API está desativada.");
      return;
    }
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

  // ----------------------------------------------------------------
  // Superficie do mapa real (GIS-06, Ciclo 3 — P-GISROTAS)
  // ----------------------------------------------------------------

  shapeRef.current = shape;

  // Snap na superficie do mapa, em PIXEIS: os alvos sao a
  // feicao de trabalho, o rascunho e as feicoes RENDERIZADAS no
  // mapa (queryRenderedFeatures na vizinhanca do clique) — e o
  // snap a vertices/bordas da rede desenhada, nao so da feicao
  // local. Quem chama recebe o ponto ja em lng/lat (Pt).
  function snapOnMap(at: Pt, e: maplibregl.MapMouseEvent, excludeVertex?: number): Pt {
    const map = mapRef.current;
    if (!snapEnabled || !map) return at;
    const lines: Pt[][] = [];
    const own = excludeVertex === undefined
      ? shape
      : shape.filter((_, i) => i !== excludeVertex);
    if (own.length > 0) lines.push(own);
    if (draft.length > 0) lines.push(draft);
    const box: [maplibregl.PointLike, maplibregl.PointLike] = [
      [e.point.x - SNAP_TOLERANCE_PX, e.point.y - SNAP_TOLERANCE_PX],
      [e.point.x + SNAP_TOLERANCE_PX, e.point.y + SNAP_TOLERANCE_PX],
    ];
    for (const f of map.queryRenderedFeatures(box)) {
      if (f.source === EDITOR_SOURCE) continue;
      lines.push(
        ...polylinesFromGeoJsonGeometry(f.geometry as unknown as GeoJsonGeometryLike),
      );
    }
    const targets: SnapTarget[] = lines.map((line) => ({
      points: line.map((p) => {
        const pr = map.project([p.x, p.y]);
        return { x: pr.x, y: pr.y };
      }),
    }));
    const r = snapPoint({ x: e.point.x, y: e.point.y }, targets, SNAP_TOLERANCE_PX);
    setSnapNote(r.kind ? `snap: ${r.kind === "vertex" ? "vértice" : "borda"} (mapa)` : "");
    if (!r.kind) return at;
    const ll = map.unproject([r.point.x, r.point.y]);
    const round7 = (v: number) => Math.round(v * 1e7) / 1e7 + 0;
    return { x: round7(ll.lng), y: round7(ll.lat) };
  }

  function handleMapClick(e: maplibregl.MapMouseEvent) {
    if (!SNAP_CLICK_TOOLS.includes(tool)) return;
    const at = snapOnMap({ x: e.lngLat.lng, y: e.lngLat.lat }, e);
    pointerDownAt(at);
  }

  function handleMapMouseDown(e: maplibregl.MapMouseEvent) {
    // R41-4-35: sem escrita, o mapa nao inicia arrastos.
    if (!canEdit) return;
    const map = mapRef.current;
    if (!map) return;
    if (tool === "select" || tool === "move") {
      // Pegar um vertice no mapa: o vertice mais proximo dentro
      // da tolerancia de snap em pixeis comeca o arrasto dele.
      let best = -1;
      let bestDist = Number.POSITIVE_INFINITY;
      shape.forEach((p, i) => {
        const pr = map.project([p.x, p.y]);
        const d = Math.hypot(pr.x - e.point.x, pr.y - e.point.y);
        if (d < bestDist) { bestDist = d; best = i; }
      });
      if (best >= 0 && bestDist <= SNAP_TOLERANCE_PX) {
        dragRef.current = { kind: "vertex", index: best };
        map.dragPan.disable();
        return;
      }
    }
    if (tool === "move" || tool === "rotate" || tool === "scale") {
      pointerDownAt({ x: e.lngLat.lng, y: e.lngLat.lat });
      if (dragRef.current) map.dragPan.disable();
    }
  }

  function handleMapMouseMove(e: maplibregl.MapMouseEvent) {
    const drag = dragRef.current;
    if (!drag) return;
    let at: Pt = { x: e.lngLat.lng, y: e.lngLat.lat };
    if (drag.kind === "vertex") at = snapOnMap(at, e, drag.index);
    pointerMoveAt(at);
  }

  function handleMapMouseUp() {
    endDrag();
    mapRef.current?.dragPan.enable();
  }

  function handleMapDoubleClick(e: maplibregl.MapMouseEvent) {
    if (tool !== "merge") return;
    e.preventDefault();
    handleCanvasDoubleClick();
  }

  mapHandlersRef.current = {
    click: handleMapClick,
    down: handleMapMouseDown,
    move: handleMapMouseMove,
    up: handleMapMouseUp,
    dbl: handleMapDoubleClick,
  };

  // Criacao do mapa real: mesmo padrao do GisMap (estilo gerado
  // da GIS-06 com os tokens do tema resolvidos). Centro neutro
  // do Brasil — a sede do tenant centra pelas pesquisas, nao
  // por coordenada chutada nesta tela (idem GisMap).
  React.useEffect(() => {
    let cancelled = false;
    let created: maplibregl.Map | null = null;
    gisMap
      .style("light")
      .then((style) => {
        if (cancelled || !mapContainerRef.current) return;
        const resolved = resolveTokenColors(
          structuredClone(style),
          readMapTokens(),
        ) as maplibregl.StyleSpecification;
        const map = new maplibregl.Map({
          container: mapContainerRef.current,
          style: resolved,
          center: [-52.0, -14.5],
          zoom: 4,
          attributionControl: { compact: true },
        });
        created = map;
        mapRef.current = map;
        map.addControl(new maplibregl.NavigationControl(), "top-right");
        map.doubleClickZoom.disable();
        map.on("load", () => {
          map.addSource(EDITOR_SOURCE, {
            type: "geojson",
            data: editorFeatureCollection(shapeRef.current, []) as never,
          });
          map.addLayer({
            id: EDITOR_LINE_LAYER,
            type: "line",
            source: EDITOR_SOURCE,
            filter: ["==", ["geometry-type"], "LineString"],
            paint: {
              "line-color": ["match", ["get", "kind"], "draft", "#0090d0", "#00548f"],
              "line-width": 3,
            },
          });
          map.addLayer({
            id: EDITOR_POINT_LAYER,
            type: "circle",
            source: EDITOR_SOURCE,
            filter: ["==", ["geometry-type"], "Point"],
            paint: {
              "circle-radius": 5,
              "circle-color": ["match", ["get", "kind"], "vertex", "#ffffff", "#00548f"],
              "circle-stroke-color": "#00548f",
              "circle-stroke-width": 2,
            },
          });
          setMapStatus("Mapa GIS-06 ligado — desenhe com o rato/toque sobre o mapa.");
        });
        map.on("click", (e: maplibregl.MapMouseEvent) => mapHandlersRef.current.click(e));
        map.on("mousedown", (e: maplibregl.MapMouseEvent) => mapHandlersRef.current.down(e));
        map.on("mousemove", (e: maplibregl.MapMouseEvent) => mapHandlersRef.current.move(e));
        map.on("mouseup", () => mapHandlersRef.current.up());
        map.on("dblclick", (e: maplibregl.MapMouseEvent) => mapHandlersRef.current.dbl(e));
      })
      .catch(() => {
        if (!cancelled) {
          setMapStatus(
            "Mapa GIS-06 indisponível nesta sessão (o estilo não carregou) — a superfície local continua a funcionar.",
          );
        }
      });
    return () => {
      cancelled = true;
      created?.remove();
      if (mapRef.current === created) mapRef.current = null;
    };
    // Uma so vez: a feicao entra pela fonte GeoJSON (efeito
    // abaixo) e os handlers leem o estado pelos refs.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // A feicao de trabalho (e o rascunho) desenhados no mapa real.
  React.useEffect(() => {
    const src = mapRef.current?.getSource(EDITOR_SOURCE);
    if (src) {
      (src as unknown as { setData: (d: unknown) => void }).setData(
        editorFeatureCollection(shape, draft),
      );
    }
  }, [shape, draft]);

  // ----------------------------------------------------------------
  // Gravacao em pacote GIS-04 (Ciclo 3): criar -> item -> submeter
  // ----------------------------------------------------------------

  async function saveToChangeSet() {
    if (csSaving) return;
    // R41-4-35: sem gis:change-set-edit, a gravacao nem e tentada
    // (o servidor nega-a com 403; o botao ja esta desativado).
    if (!canSave) {
      setCsStatus("Sem a permissão gis:change-set-edit, a gravação em pacote está desativada.");
      return;
    }
    if (!csFeatureId.trim()) {
      setCsStatus(
        "Informe o id da feição (UUID): gravar em pacote exige a feição que esta geometria edita (UPDATE/RETIRE exigem feição existente no servidor).",
      );
      return;
    }
    let wkt: string;
    try {
      wkt = shapeWkt(shape);
      if (wkt === "(sem geometria)") throw new Error("sem geometria");
    } catch {
      setCsStatus("Desenhe uma geometria (ponto, linha ou polígono) antes de gravar em pacote.");
      return;
    }
    setCsSaving(true);
    try {
      const itemInput = geometryItemInput({
        featureTable: csTable,
        featureId: csFeatureId.trim(),
        operation: csOperation,
        geomWkt: wkt,
        proposedState: values,
      });
      const created = await changeSets.create({ type: csType, channel: "WEB" });
      const item = await changeSets.addItem(created.id, itemInput);
      const submitted = await changeSets.submit(created.id);
      setCsStatus(
        `Pacote ${submitted.id} submetido (estado ${submitted.status}); item ${item.id} com a geometria editada. Revisão por outra pessoa: o autor não aprova o próprio pacote — o pacote entra na fila de revisão (GIS-04).`,
      );
    } catch (err) {
      setCsStatus(`Gravação recusada: ${err instanceof Error ? err.message : err}`);
    } finally {
      setCsSaving(false);
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
        {!canEdit ? (
          <p role="status" data-testid="gis-editor-readonly-notice">
            Só de consulta: sem a permissão gis:import-edit, as ferramentas
            de edição, a conferência na API e a gravação em pacote estão
            desativadas — o servidor nega a gravação sem ela (403).
          </p>
        ) : !canSave ? (
          <p role="status" data-testid="gis-editor-readonly-notice">
            Sem a permissão gis:change-set-edit, a gravação em pacote está
            desativada — o servidor nega-a sem ela (403). A edição local
            continua disponível.
          </p>
        ) : null}
        <p className="gis-skeleton-note">
          Ligado: geometria operada com o rato/toque na superfície
          local e no mapa real (GIS-06), snap a vértices/bordas e
          gravação em pacote (GIS-04) com revisão por outra
          pessoa — ver o cabeçalho do arquivo.
        </p>
        <p role="status">{mapStatus}</p>
      </header>
      <div
        ref={mapContainerRef}
        data-testid="gis-editor-map"
        role="application"
        aria-label="Mapa do editor GIS (GIS-06, interacção por rato/toque)"
        style={{ height: "420px", width: "100%", background: "#e8edf1" }}
      />
      <div className="gis-tools" role="toolbar" aria-label="Ferramentas de edicao">
        {TOOLS.map((t) => (
          <button
            key={t.id}
            type="button"
            aria-pressed={tool === t.id}
            disabled={!canEdit}
            onClick={() => selectTool(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>
      <p className="gis-mode-hint">{MODE_HINTS[tool]}</p>
      <label className="gis-snap-toggle">
        <input
          type="checkbox"
          checked={snapEnabled}
          onChange={(e) => setSnapEnabled(e.target.checked)}
        />{" "}
        Snap a vértices/bordas ao desenhar e arrastar (E2-SNAP)
        {snapNote ? ` — último: ${snapNote}` : ""}
      </label>
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
        <button type="button" onClick={checkOnApi} disabled={!canEdit}>
          Conferir na API o deslocamento feito com o rato
        </button>
        {preview ? <p>{preview}</p> : null}
      </div>
      <div className="gis-changeset-save">
        <h2>Gravar em pacote GIS-04</h2>
        <p>
          Cria um pacote (canal WEB), junta a feição editada com a
          geometria actual e submete — a revisão é por outra
          pessoa (o autor não aprova o próprio pacote).
        </p>
        <label>
          Tabela da feição{" "}
          <select value={csTable} disabled={!canSave} onChange={(e) => setCsTable(e.target.value)}>
            {CHANGE_SET_FEATURE_TABLES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </label>{" "}
        <label>
          Operação{" "}
          <select
            value={csOperation}
            disabled={!canSave}
            onChange={(e) => setCsOperation(e.target.value as "INSERT" | "UPDATE" | "RETIRE")}
          >
            <option value="UPDATE">UPDATE</option>
            <option value="INSERT">INSERT</option>
            <option value="RETIRE">RETIRE</option>
          </select>
        </label>{" "}
        <label>
          Tipo de pacote{" "}
          <select value={csType} disabled={!canSave} onChange={(e) => setCsType(e.target.value)}>
            {CHANGE_SET_TYPES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </label>{" "}
        <label>
          Id da feição{" "}
          <input
            value={csFeatureId}
            disabled={!canSave}
            onChange={(e) => setCsFeatureId(e.target.value)}
            placeholder="uuid da feição editada"
          />
        </label>{" "}
        <button type="button" onClick={() => void saveToChangeSet()} disabled={csSaving || !canSave}>
          {csSaving ? "A gravar…" : "Gravar em pacote e submeter a revisão"}
        </button>
        {csStatus ? <p role="status">{csStatus}</p> : null}
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

import React from "react";
import { webEdit, type EditTool } from "../../services/gis/webEdit";
import { SchemaForm } from "./SchemaForm";
import type { FormField } from "../../services/gis/webEdit";
import {
  centroid,
  insertNodeAt,
  mergeLines,
  moveVertex,
  rotate,
  scale,
  splitLineAt,
  toLineWkt,
  translate,
  type Pt,
} from "../../utils/geometry";

/**
 * Tela do editor web completo (GIS-09, ED-53): ferramentas de
 * ponto, linha e poligono com vertices, mover, rotacionar e
 * escalar (SG-838/SG-839, TR-1026/TR-1027), dividir e unir arcos
 * e inserir no que segmenta.
 *
 * ESTADO REAL DESTA TELA (correcao D8 da revisao ED-53 — sem
 * ✅ onde e esqueleto):
 * - LIGADO: as ferramentas de geometria operam de verdade sobre
 *   a feicao de trabalho LOCAL usando as operacoes puras de
 *   utils/geometry.ts (o mesmo espelho TS de GisGeom provado no
 *   backend), com pre-visualizacao em WKT; o botao "Conferir na
 *   API" manda a MESMA operacao para o servico (com layerId —
 *   sem permissao camada x operacao o servico devolve 403, D1);
 *   o formulario de exemplo e gerado pelo endpoint
 *   /forms/from-schema; as permissoes vem de
 *   /layer-permissions/mine.
 * - ESQUELETO (NAO ligado): a renderizacao/desenho no mapa da
 *   GIS-06 (P3.25, MapLibre) — clicar no mapa nao desenha aqui;
 *   a gravacao da feicao editada em pacote da GIS-04 e a tela de
 *   pacotes (ChangeSetReview da P2.22) tambem ficam fora desta
 *   pagina. Os endpoints de camada do usuario, juncao, projeto,
 *   unidades e sync estao expostos no servico TS
 *   (services/gis/webEdit.ts) mas sem formulario proprio nesta
 *   pagina. Build do frontend nao executado nesta VM
 *   (pendencia 8).
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

function midpoint(a: Pt, b: Pt): Pt {
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
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

  // Aplica a ferramenta selecionada sobre a feicao local com a
  // geometria pura de utils/geometry.ts (espelho de GisGeom).
  function applyTool(selected: EditTool) {
    setTool(selected);
    try {
      switch (selected) {
        case "move":
          setShape((s) => translate(s, 1, 0));
          break;
        case "rotate":
          setShape((s) => rotate(s, centroid(s), 15));
          break;
        case "scale":
          setShape((s) => scale(s, centroid(s), 1.1));
          break;
        case "split": {
          const r = splitLineAt(shape, midpoint(shape[0], shape[1]), 0.01);
          setShape(r.first);
          setPreview(`segundo trecho: ${toLineWkt(r.second)}`);
          break;
        }
        case "insert-node": {
          const r = insertNodeAt(shape, midpoint(shape[0], shape[1]), 0.01);
          setShape([...r.first, ...r.second.slice(1)]);
          setPreview(`no inserido: ${r.node.x}, ${r.node.y}`);
          break;
        }
        case "merge": {
          const last = shape[shape.length - 1];
          const continuation = [last, { x: last.x + 5, y: last.y }];
          setShape((s) => mergeLines(s, continuation, 0.01));
          break;
        }
        default:
          // select/point/line/polygon: o desenho no mapa e da
          // GIS-06 — fora desta pagina (esqueleto declarado).
          setPreview(
            "Ferramenta de desenho: integracao com o mapa da GIS-06 fora desta pagina (esqueleto).",
          );
      }
    } catch (e) {
      setPreview(`Operacao recusada: ${e instanceof Error ? e.message : e}`);
    }
  }

  // Mover o primeiro vertice da feicao local (edicao com vertices).
  function nudgeFirstVertex() {
    setShape((s) => moveVertex(s, 0, { x: s[0].x + 1, y: s[0].y + 1 }));
  }

  // A mesma transformacao, conferida pelo servico (com layerId).
  async function checkOnApi() {
    if (!layerId) {
      setStatus("Informe o layerId para conferir na API (a guarda D1 exige a camada).");
      return;
    }
    try {
      const out = await webEdit.transform({
        geomWkt: toLineWkt(shape),
        mode: "MOVE",
        dx: 1,
        dy: 0,
        layerId,
      });
      setStatus(`API conferiu: ${out.geomWkt}`);
    } catch (e) {
      setStatus(`API recusou: ${e instanceof Error ? e.message : e}`);
    }
  }

  return (
    <section className="gis-web-editor">
      <header>
        <h1>Editor GIS</h1>
        <p role="status">{status}</p>
        <p className="gis-skeleton-note">
          Parcial: geometria local e endpoints ligados; desenho no
          mapa (GIS-06) e gravacao em pacote (GIS-04) sao esqueleto
          fora desta pagina — ver o cabecalho do arquivo.
        </p>
      </header>
      <div className="gis-tools" role="toolbar" aria-label="Ferramentas de edicao">
        {TOOLS.map((t) => (
          <button
            key={t.id}
            type="button"
            aria-pressed={tool === t.id}
            onClick={() => applyTool(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div className="gis-local-preview">
        <p>Feicao de trabalho (geometria pura local): {toLineWkt(shape)}</p>
        <button type="button" onClick={nudgeFirstVertex}>
          Mover 1o vertice (+1, +1)
        </button>
        <label>
          layerId{" "}
          <input
            value={layerId}
            onChange={(e) => setLayerId(e.target.value)}
            placeholder="uuid da camada"
          />
        </label>
        <button type="button" onClick={checkOnApi}>
          Conferir mover (+1, 0) na API
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

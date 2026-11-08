// Servico do editor web GIS-09 (ED-53) — /api/v1/gis/web-edit.
// Correcao D1 (revisao ED-53): TODA escrita carrega layerId — o
// backend confere a permissao camada x operacao (SG-864/SG-865)
// antes de escrever e devolve 403 sem ela.
import type { Pt } from "../../utils/geometry";

export type EditTool =
  | "select" | "point" | "line" | "polygon"
  | "move" | "rotate" | "scale" | "split" | "merge" | "insert-node";

export interface TransformInput {
  geomWkt: string;
  mode: "MOVE" | "ROTATE" | "SCALE" | "VERTEX";
  dx?: number; dy?: number;
  pivotX?: number; pivotY?: number;
  degrees?: number; factor?: number;
  vertexIndex?: number; toX?: number; toY?: number;
  layerId: string;
}

export interface LayerPermission {
  layerId: string; layerKey: string; operations: string[];
}

export interface FormField {
  name: string; label: string; type: string; required: boolean;
  options: unknown[]; minimum: number | null; maximum: number | null;
  maxLength: number | null;
}

export interface UserLayer {
  id: string; name: string; geometryType: string; ownerId: string;
}

export interface UserFeature {
  id: string; layerId: string; geomWkt: string;
  attributes: Record<string, unknown>;
}

export interface JoinDef {
  id: string; leftTable: string; rightTable: string;
  joinType: string; joinKey: string; layerId: string;
}

export interface JoinResult {
  joinId: string; leftTable: string; rightTable: string;
  joinType: string; joinKey: string;
  rows: { leftId: string; rightId: string | null; keyValue: string }[];
}

export interface Project {
  id: string; name: string; status: string;
  projectChangeSetId: string | null; asBuiltChangeSetId: string | null;
  workRef: string | null;
}

const BASE = "/api/v1/gis/web-edit";

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`web-edit ${path}: HTTP ${res.status}`);
  return (await res.json()) as T;
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) throw new Error(`web-edit ${path}: HTTP ${res.status}`);
  return (await res.json()) as T;
}

export const webEdit = {
  transform: (input: TransformInput) =>
    post<{ geomWkt: string }>("/geometry/transform", input),
  split: (geomWkt: string, at: Pt, layerId: string, tolerance?: number) =>
    post<{ firstWkt: string; secondWkt: string; nodeWkt: string }>(
      "/geometry/split", { geomWkt, atX: at.x, atY: at.y, tolerance, layerId }),
  insertNode: (geomWkt: string, at: Pt, layerId: string, tolerance?: number) =>
    post<{ firstWkt: string; secondWkt: string; nodeWkt: string }>(
      "/geometry/insert-node", { geomWkt, atX: at.x, atY: at.y, tolerance, layerId }),
  merge: (firstWkt: string, secondWkt: string, layerId: string, tolerance?: number) =>
    post<{ geomWkt: string }>("/geometry/merge",
      { firstWkt, secondWkt, tolerance, layerId }),
  formFromSchema: (schema: Record<string, unknown>) =>
    post<{ fields: FormField[] }>("/forms/from-schema", schema),
  myLayerPermissions: () =>
    get<LayerPermission[]>("/layer-permissions/mine"),
  createUserLayer: (name: string, geometryType: string) =>
    post<UserLayer>("/user-layers", { name, geometryType }),
  listUserLayers: () => get<UserLayer[]>("/user-layers"),
  addUserFeature: (layerId: string, geomWkt: string,
    attributes: Record<string, unknown>) =>
    post<UserFeature>(`/user-layers/${layerId}/features`, { geomWkt, attributes }),
  listUserFeatures: (layerId: string) =>
    get<UserFeature[]>(`/user-layers/${layerId}/features`),
  createJoin: (input: {
    leftTable: string; rightTable: string; joinType: string;
    joinKey: string; layerId: string;
  }) => post<JoinDef>("/table-joins", input),
  executeJoin: (joinId: string) =>
    post<JoinResult>(`/table-joins/${joinId}/execute`, {}),
  draftPlantStructure: (input: {
    structureKind: string; code: string; name?: string;
    geomWkt?: string; geomPointWkt?: string;
    attributes: Record<string, unknown>;
    featureTypeId: string; layerId: string;
  }) => post<unknown>("/plant-structures/draft", input),
  createProject: (input: {
    name: string; description?: string; workRef?: string; layerId: string;
  }) => post<Project>("/projects", input),
  getProject: (id: string) => get<Project>(`/projects/${id}`),
  openForServiceOrder: (input: {
    serviceOrderRef: string; affectsNetwork: boolean; layerId: string;
  }) => post<string | null>("/service-orders/open-change-set", input),
  syncDrawnConnection: (gisConnectionId: string, layerId: string,
    consumerUnitId: string, domainKey: string) =>
    post<{ gisConnectionId: string; serviceConnectionId: string | null;
      status: string; idempotencyKey: string }>(
      `/drawn-connections/${gisConnectionId}/sync`
      + `?layerId=${encodeURIComponent(layerId)}`
      + `&consumerUnitId=${encodeURIComponent(consumerUnitId)}`
      + `&domainKey=${encodeURIComponent(domainKey)}`, {}),
};

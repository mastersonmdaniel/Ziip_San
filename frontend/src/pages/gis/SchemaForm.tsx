import React from "react";
import type { FormField } from "../../services/gis/webEdit";

/**
 * Formulario gerado do JSON Schema (GIS-09, ED-53): os campos vem
 * do descritor que a api deriva de
 * gis_feature_type.attributes_schema — nenhum campo e desenhado
 * a mao por tipo de feicao.
 */
export function SchemaForm(props: {
  fields: FormField[];
  values: Record<string, unknown>;
  onChange: (name: string, value: unknown) => void;
}) {
  return (
    <form className="gis-schema-form">
      {props.fields.map((f) => (
        <label key={f.name}>
          <span>{f.label}{f.required ? " *" : ""}</span>
          {f.options.length > 0 ? (
            <select
              value={String(props.values[f.name] ?? "")}
              onChange={(e) => props.onChange(f.name, e.target.value)}
            >
              <option value="" />
              {f.options.map((o) => (
                <option key={String(o)} value={String(o)}>{String(o)}</option>
              ))}
            </select>
          ) : f.type === "boolean" ? (
            <input
              type="checkbox"
              checked={Boolean(props.values[f.name])}
              onChange={(e) => props.onChange(f.name, e.target.checked)}
            />
          ) : (
            <input
              type={f.type === "string" ? "text" : "number"}
              required={f.required}
              min={f.minimum ?? undefined}
              max={f.maximum ?? undefined}
              maxLength={f.maxLength ?? undefined}
              value={String(props.values[f.name] ?? "")}
              onChange={(e) =>
                props.onChange(
                  f.name,
                  f.type === "string" ? e.target.value : Number(e.target.value),
                )
              }
            />
          )}
        </label>
      ))}
    </form>
  );
}

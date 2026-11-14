package br.com.ziipsan.commercial.fiscal.relief;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leiautes DANFAG para emissao em campo (PA-01 V-45 = PARCIAL; insumo (d)
 * do coordenador — CONDICIONADO ao MOC).
 *
 * <p><b>Fonte localizada (14/11/2026)</b>: MOC da NFAg, <b>Anexo II —
 * DANFAG</b> (especificacoes tecnicas do documento auxiliar): modelos de
 * referencia em bobina "Opcao 1", "Opcao 2" e "Cofaturamento" (e modelos A4,
 * fora do ambito de campo). Versoes: v1.00 (out/2025), v1.01 (jan/2026),
 * v1.02 (25/08/2026). <b>Caveat</b>: o Anexo II circula como MINUTA,
 * aguardando ato conjunto normativo para publicacao definitiva (registo
 * honesto; a frente fiscal confirma a versao vigente antes de homologar
 * leiaute).</p>
 *
 * <p>Os tres modelos de bobina entram como <b>modelos de referencia</b>
 * (nome + fonte). A especificacao fisica completa (larguras, margens) NAO
 * foi lida integralmente nesta producao: {@link PhysicalLayout} continua a
 * exigir fonte e dimensoes declaradas caso a caso — dimensao sem fonte
 * lida fica [SEM FONTE] e fora da implementacao.</p>
 */
public final class DanfagLayoutRegistry {

    /** Modelo fisico de impressao (so existe com fonte MOC citada). */
    public record PhysicalLayout(String modelId, int paperWidthMm, String sourceRef) {
        public PhysicalLayout {
            if (modelId == null || modelId.isBlank()) {
                throw new IllegalArgumentException("modelo do leiaute e obrigatorio");
            }
            if (paperWidthMm <= 0) {
                throw new IllegalArgumentException("largura da bobina deve ser positiva");
            }
            if (sourceRef == null || sourceRef.isBlank()) {
                throw new IllegalArgumentException(
                        "leiaute DANFAG sem fonte (MOC) declarada nao se regista: [SEM FONTE]");
            }
        }
    }

    /** Modelo de referencia nomeado pelo MOC Anexo II (sem dimensao fisica presumida). */
    public record ReferenceModel(String modelId, String displayName, String sourceRef) {
        public ReferenceModel {
            if (modelId == null || modelId.isBlank() || displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("modelo de referencia DANFAG exige id e nome");
            }
            if (sourceRef == null || sourceRef.isBlank()) {
                throw new IllegalArgumentException(
                        "modelo DANFAG sem fonte (MOC) declarada nao se regista: [SEM FONTE]");
            }
        }
    }

    /** Fonte dos modelos de referencia: MOC NFAg Anexo II — DANFAG. */
    public static final String MOC_ANNEX_II_SOURCE =
            "MOC NFAg, Anexo II — DANFAG (v1.00 out/2025; v1.01 jan/2026; v1.02 25/08/2026) — minuta";

    private final Map<String, PhysicalLayout> layouts = new LinkedHashMap<>();
    private final Map<String, ReferenceModel> referenceModels = new LinkedHashMap<>();

    /** Regista os tres modelos de bobina nomeados pelo MOC Anexo II, com a fonte declarada. */
    public void registerMocCoilReferenceModels(String sourceRef) {
        registerReference(new ReferenceModel("BOBINA-OPCAO-1", "Leiaute DANFAG Bobina — Opcao 1", sourceRef));
        registerReference(new ReferenceModel("BOBINA-OPCAO-2", "Leiaute DANFAG Bobina — Opcao 2", sourceRef));
        registerReference(new ReferenceModel("BOBINA-COFATURAMENTO", "Leiaute DANFAG Bobina — Cofaturamento", sourceRef));
    }

    public void registerReference(ReferenceModel model) {
        if (referenceModels.putIfAbsent(model.modelId(), model) != null) {
            throw new IllegalStateException("Modelo de referencia DANFAG ja registado: " + model.modelId());
        }
    }

    public List<ReferenceModel> referenceModels() {
        return List.copyOf(referenceModels.values());
    }

    public void register(PhysicalLayout layout) {
        if (layouts.putIfAbsent(layout.modelId(), layout) != null) {
            throw new IllegalStateException("Modelo DANFAG ja registado: " + layout.modelId());
        }
    }

    public List<PhysicalLayout> registered() {
        return List.copyOf(layouts.values());
    }

    /** Sem modelo registado com fonte, a emissao em campo nao ganha leiaute novo. */
    public boolean isEmpty() {
        return layouts.isEmpty();
    }
}

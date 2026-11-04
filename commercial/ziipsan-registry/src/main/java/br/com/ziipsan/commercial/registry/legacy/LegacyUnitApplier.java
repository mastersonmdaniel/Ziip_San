package br.com.ziipsan.commercial.registry.legacy;

import java.util.List;
import java.util.UUID;

/**
 * Aplicador da v0 (P1.2): transforma uma unidade encenada numa linha de
 * {@code consumer_units}.
 *
 * <p>Limite honesto da v0, documentado para a P1.3: o aplicador grava a
 * unidade mínima — matrícula (preservada do legado, com o DV da regra
 * {@code Matricula} quando o legado não o traz), quadra/lote/sublote,
 * rota de leitura e sequência. Ele NÃO cria cliente, endereço, categoria
 * nem vínculo de titularidade (regras da P1.3), NÃO roda as regras de
 * consistência da unidade e NÃO cifra documento (o staging nem o tem).
 * Unidades aplicadas sem titular são exatamente o caso VACANT da P1.41,
 * que as recebe com a situação dela. A P1.3 substitui este aplicador pelo
 * caminho completo de criação sem mudar o protocolo da OS de lote.
 *
 * <p>Resolução de referências: rota por código (único por tenant);
 * quadra por localidade + setor + quadra (códigos do legado). Rota ou
 * quadra irresolúvel é aviso (a unidade nasce sem rota/quadra — a
 * identidade da unidade é a matrícula, não a quadra); quadra resolvida
 * sem lote/sublote, matrícula ausente ou matrícula não numérica são
 * bloqueios — a linha fica fora do lote, com o motivo (o banco recusa
 * quadra sem lote/sublote pelo CHECK de inscrição). Matrícula já
 * existente é reutilizada (reaplicação não duplica).
 */
public interface LegacyUnitApplier {

    record Outcome(UUID unitId, List<LegacyIssue> blockingIssues) {
        public boolean applied() {
            return unitId != null;
        }
    }

    Outcome apply(StagedUnit unit, UUID actorId);
}

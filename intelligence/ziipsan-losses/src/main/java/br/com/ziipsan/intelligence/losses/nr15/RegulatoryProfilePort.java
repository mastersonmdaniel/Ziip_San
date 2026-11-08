package br.com.ziipsan.intelligence.losses.nr15;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura do perfil regulatorio do tenant (ED-78): a
 * aplicabilidade da NR 15 NAO e parametro solto nem interruptor
 * proprio - ela decorre do perfil regulatorio do tenant (P2.31), que
 * registra, por agencia (ERI), a adocao de cada NR da ANA.
 *
 * <p>O modelo espelha {@code RegulatoryProfileDef} da P2.31 SEM
 * depender do modulo platform (precedente ED-48: ligacao por chave,
 * sem dependencia nova de modulo): {@link ProfileView} traz o codigo
 * da agencia e o mapa de adocao {@code NR -> Adoption}; {@link
 * Adoption} espelha {@code RegulatoryProfileDef.AnaAdoption} (status
 * + atos). Nos perfis de fabrica da P2.31 conferidos nesta VM, os
 * status existentes sao {@code ADOPTED}, {@code PARTIAL} e {@code
 * NO_ACT_LOCATED} - e a NR15 consta {@code NO_ACT_LOCATED} (sem
 * atos) nos perfis ARIS e AGIR: hoje, nenhum tenant desses perfis e
 * aplicavel, e e exatamente esse o comportamento fail-closed aqui.
 *
 * <p>A fiacao (pendencia P-ED78-05) implementa esta porta sobre o
 * perfil vigente do tenant na P2.31 ({@code reg.ana_nr_adoption}).
 */
public interface RegulatoryProfilePort {

    /** Adocao de uma NR por uma agencia: status declarado + atos publicados. */
    record Adoption(String status, List<String> acts) {
        public Adoption {
            acts = acts == null ? List.of() : List.copyOf(acts);
        }

        /** Ha ao menos um ato de adocao publicado (criterio da P2.31). */
        public boolean hasActs() {
            return !acts.isEmpty();
        }
    }

    /** Visao do perfil vigente do tenant: agencia + mapa de adocao das NR. */
    record ProfileView(String agencyCode, Map<String, Adoption> anaAdoption) {
        public ProfileView {
            anaAdoption = anaAdoption == null ? Map.of() : Map.copyOf(anaAdoption);
        }

        public Optional<Adoption> adoptionOf(String nrKey) {
            return Optional.ofNullable(anaAdoption.get(nrKey));
        }
    }

    /** Perfil vigente do tenant, ou vazio quando o tenant nao tem perfil. */
    Optional<ProfileView> profileOf(UUID tenantId);
}

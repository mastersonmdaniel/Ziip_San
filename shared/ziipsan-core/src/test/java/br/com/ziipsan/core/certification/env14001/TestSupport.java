package br.com.ziipsan.core.certification.env14001;

import java.util.UUID;

/** Fixtures 100% sinteticas (prefixo SYN-) dos testes da ED-70. */
final class TestSupport {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000001401");
    static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-0000-0000-000000009999");
    static final UUID PROPOSER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    static final UUID ACCEPTER = UUID.fromString("00000000-0000-0000-0000-000000000102");
    static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000103");
    static final String SCOPE = "SYN-escopo: ETA, ETE e rede do tenant sintetico";

    private TestSupport() {
    }

    static Iso14001Activation enabledTarget() {
        return Iso14001Activation.enabledTarget(SCOPE, PROPOSER, ACCEPTER);
    }

    static Iso14001Activation enabledTransition() {
        return Iso14001Activation.enabledInTransition(SCOPE, PROPOSER, ACCEPTER);
    }

    static Iso14001Activation disabled() {
        return Iso14001Activation.disabled();
    }

    static UUID id() {
        return UUID.randomUUID();
    }
}

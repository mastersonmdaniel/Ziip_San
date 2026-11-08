package br.com.ziipsan.core.certification.assetmgmt55001;

import java.util.UUID;

final class Iso55001Fixtures {
    static final UUID PROPOSER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID ACCEPTER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID USER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID ASSET = UUID.fromString("44444444-4444-4444-4444-444444444444");

    static Iso55001Activation on() {
        return Iso55001Activation.enabled("Ativos de água e esgoto do município", PROPOSER, ACCEPTER);
    }

    private Iso55001Fixtures() {
    }
}

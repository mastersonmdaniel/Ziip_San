package br.com.ziipsan.core.protocol;

/**
 * Channel of a service protocol (P1.36, plan line 663). The channel is
 * where the external request arrived; it never changes the protocol's
 * numbering or deadline, only its provenance (and, for REGULATOR and
 * OMBUDSMAN, the agency reference the protocol carries).
 */
public enum ProtocolChannel {
    COUNTER,
    PHONE,
    PORTAL,
    APP,
    EMAIL,
    WHATSAPP,
    REGULATOR,
    OMBUDSMAN
}

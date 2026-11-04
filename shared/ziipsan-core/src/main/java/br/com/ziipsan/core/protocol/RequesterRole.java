package br.com.ziipsan.core.protocol;

/**
 * Role of the protocol's requester (P1.36, plan line 663): the holder
 * of the consumer unit, a user of it, a third party, or an internal
 * requester (a backoffice change that still arrives as a protocol is
 * INTERNAL here; a backoffice change with no protocol at all generates
 * an INTERNAL order directly — ADR-0039 item 7 — and never reaches
 * this table).
 */
public enum RequesterRole {
    HOLDER,
    USER,
    THIRD_PARTY,
    INTERNAL
}

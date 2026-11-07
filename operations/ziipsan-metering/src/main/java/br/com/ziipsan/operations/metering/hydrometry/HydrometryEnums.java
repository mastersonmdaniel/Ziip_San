package br.com.ziipsan.operations.metering.hydrometry;

/** Domínios da hidrometria (ED-14, Portaria Inmetro 155/2022). Espelham os CHECKs do SQL pendente. */
public final class HydrometryEnums {
    private HydrometryEnums() {}
    /** Regime do modelo aprovado (RTM da Portaria 155/2022). */
    public enum Regime { INITIAL_VERIFICATION, SUBSEQUENT_VERIFICATION }
    /** Posição de instalação admitida pelo modelo aprovado. */
    public enum InstallationPosition { HORIZONTAL, VERTICAL, ANY }
    public enum VerificationType { INITIAL, SUBSEQUENT, AFTER_REPAIR }
    public enum VerificationResult { APPROVED, REJECTED }
    /** Executante da verificação/ensaio. */
    public enum PerformedBy { OWN_BENCH, EXTERNAL_LAB }
    public enum RemovalDestination { AVAILABLE, SCRAP }
    public enum MovementType { RECEIPT, INSTALL, REMOVE, REINSTALL, MOVE_TO_MAINTENANCE, RETURN_FROM_MAINTENANCE, WRITE_OFF, STOLEN }
    public enum SizingVerdict { UNDERSIZED, ADEQUATE, OVERSIZED }
    public enum BenchFlowPoint { Q1, Q2, Q3, Q4 }
}

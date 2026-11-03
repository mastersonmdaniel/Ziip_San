package br.com.ziipsan.core.serviceorder;

/**
 * Small domains of the work-order engine (V9_1 CHECKs mirror them).
 *
 * <p>{@link ExecutionMode#CONTRACTOR} is field work done by a contractor:
 * it is only valid for a type whose indicator is FIELD. {@link Origin}
 * is the protocol contract: an EXTERNAL order always carries a protocol
 * id, an INTERNAL (backoffice) order never does; the protocol table and
 * its FK arrive with P1.36, the rule is enforced from now.
 */
public final class ServiceOrderEnums {

    private ServiceOrderEnums() {
    }

    public enum ExecutionMode {
        FIELD, INTERNAL, CONTRACTOR
    }

    public enum Origin {
        EXTERNAL, INTERNAL
    }

    public enum Priority {
        LOW, NORMAL, HIGH, URGENT
    }

    /** Internal note visibility: backoffice only, or also shown to the user. */
    public enum NoteVisibility {
        INTERNAL, USER_VISIBLE
    }

    /** Indicator of a service type: backoffice work or field work. */
    public enum TypeIndicator {
        INTERNAL, FIELD
    }

    /** Origin of an operational type (no commercial effect): where the job came from. */
    public enum OperationalOrigin {
        NETWORK_INCIDENT, PREVENTIVE_PLAN, INSPECTION, COMPLAINT
    }

    /**
     * Safety controls a type may require. The factory controls come from the
     * type's hazard flags (V9_1 CHECKs); the tenant parameter
     * {@code sst.required_controls_by_service_type} may only ADD controls
     * (class c), never remove one.
     */
    public enum ControlCode {
        RISK_ANALYSIS,
        ENTRY_PERMIT,
        WORK_PERMIT,
        QUALIFIED_RELEASE,
        TRAINING_VALID,
        MIN_CREW
    }
}

package br.com.ziipsan.core.serviceorder;

/**
 * Target classes of a work order (ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS §3).
 *
 * <p>A target is a typed contract: the class names the kind of entity, and
 * the engine only accepts a class for a service type when an existence
 * verifier is registered for it (see {@link TargetVerifierRegistry}).
 * {@link #LOCATION_ONLY} is the exception: it carries a location instead
 * of an entity, so it needs no verifier.
 *
 * <p>Which classes have a verifier today is a runtime fact, not a property
 * of this enum: the GIS classes become declarable when the GIS module
 * registers its verifiers, the asset classes when the asset registry
 * (P1.35) does, and so on. Declaring a class without a verifier is refused
 * by {@link ServiceOrderService#declareType}.
 */
public enum TargetType {
    CONSUMER_UNIT,
    WATER_CONNECTION,
    SEWER_CONNECTION,
    METER,
    GIS_LINEAR,
    GIS_POINT,
    OPERATIONAL_SITE,
    EQUIPMENT,
    COMPONENT,
    BUILDING,
    FLEET_ASSET,
    NETWORK_INCIDENT,
    ZONE,
    LOCATION_ONLY;

    /** True only for the class that carries a location instead of an entity. */
    public boolean isLocationOnly() {
        return this == LOCATION_ONLY;
    }
}

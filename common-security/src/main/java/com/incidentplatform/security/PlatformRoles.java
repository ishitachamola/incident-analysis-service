package com.incidentplatform.security;

/**
 * The platform's roles.
 *
 * <p>{@code USER} can see incidents and past analyses. {@code SRE} adds the operational surface:
 * raw logs, running an analysis, asking follow-up questions. {@code ADMIN} adds managing the
 * knowledge base. {@code SERVICE} is for internal service-to-service calls, never for people.
 */
public final class PlatformRoles {

    public static final String USER = "USER";
    public static final String SRE = "SRE";
    public static final String ADMIN = "ADMIN";
    public static final String SERVICE = "SERVICE";

    /** Roles allowed to read; internal calls included. */
    public static final String[] READERS = {USER, SRE, ADMIN, SERVICE};

    /** Roles allowed to act on operational data. */
    public static final String[] OPERATORS = {SRE, ADMIN, SERVICE};

    private PlatformRoles() {
    }
}

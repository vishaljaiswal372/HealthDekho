package com.healthDekho.backend.service;

import com.healthDekho.backend.config.PolicyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Issues sequential, human-readable policy numbers per year: HDP-2026-0001, HDP-2026-0002, ...
 * <p>
 * A transaction-scoped Postgres advisory lock serializes registrations, so two concurrent
 * requests can neither get the same number nor both pass the duplicate-registration check.
 * The lock is released when the surrounding transaction commits or rolls back.
 * The UNIQUE constraint on policy_number remains the final safety net.
 */
@Component
@RequiredArgsConstructor
public class PolicyNumberGenerator {

    private static final long REGISTRATION_LOCK_KEY = 0x48445F504F4CL; // "HD_POL"

    private final JdbcTemplate jdbc;
    private final PolicyProperties properties;
    private final Clock clock;

    /** Must run inside the registration transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String lockAndGenerateNext() {
        jdbc.queryForList("select pg_advisory_xact_lock(?)", REGISTRATION_LOCK_KEY);

        String prefix = "%s-%d-".formatted(properties.numberPrefix(), LocalDate.now(clock).getYear());
        Integer lastSerial = jdbc.queryForObject("""
                select max(cast(substring(policy_number from ?) as integer))
                from customer_policy
                where policy_number like ? and substring(policy_number from ?) ~ '^[0-9]+$'
                """, Integer.class, prefix.length() + 1, prefix + "%", prefix.length() + 1);

        int next = (lastSerial == null ? 0 : lastSerial) + 1;
        return prefix + String.format("%04d", next);
    }
}

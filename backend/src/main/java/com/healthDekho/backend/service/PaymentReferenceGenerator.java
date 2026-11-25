package com.healthDekho.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Issues sequential transaction references (TXN-HD-000004, ...), continuing from the highest one stored.
 * A transaction-scoped advisory lock serializes payment recording, and the UNIQUE constraint on
 * transaction_reference is the final safety net.
 */
@Component
@RequiredArgsConstructor
public class PaymentReferenceGenerator {

    private static final long PAYMENT_LOCK_KEY = 0x48445F504159L; // "HD_PAY"
    private static final String PREFIX = "TXN-HD-";

    private final JdbcTemplate jdbc;

    /** Locks payment recording until the surrounding transaction ends, then reserves {@code count} references. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> lockAndReserve(int count) {
        jdbc.queryForList("select pg_advisory_xact_lock(?)", PAYMENT_LOCK_KEY);
        Long last = jdbc.queryForObject("""
                select max(cast(substring(transaction_reference from ?) as bigint))
                from premium_payment
                where transaction_reference like ? and substring(transaction_reference from ?) ~ '^[0-9]+$'
                """, Long.class, PREFIX.length() + 1, PREFIX + "%", PREFIX.length() + 1);
        long start = (last == null ? 0 : last) + 1;
        return IntStream.range(0, count)
                .mapToObj(i -> PREFIX + String.format("%06d", start + i))
                .toList();
    }
}

-- Sample data from spec section 5.5. Loaded only in the dev profile.
--
-- Dev login for all three customers: password  Dekho@Dev2026
-- (the spec's "$2a$10$sample0N" values are placeholders; these are real BCrypt hashes, cost 10).
--
-- FE-1 claim sample data (spec 5.5.8) is intentionally NOT loaded.

-- 5.5.1 customer
INSERT INTO customer (customer_id, email, password_hash, full_name, date_of_birth, phone, status) VALUES
    (1001, 'aarav.sharma@example.com', '$2a$10$k.UU0QRdGGW32F7y.zjyFe35W/unnQ/2elmPS0XePSQosfOIdtMji', 'Aarav Sharma', DATE '1990-04-15', '9876501001', 'ACTIVE'),
    (1002, 'meera.iyer@example.com',   '$2a$10$pfQwwPlTKqgu6UQce8RI.uJGK2Yk2LfO0IV9j3BrnKvL0zZlr52o6', 'Meera Iyer',   DATE '1985-09-22', '9876501002', 'ACTIVE'),
    (1003, 'rohan.verma@example.com',  '$2a$10$1Kvr82snVdy0SppLPtcjGeEixbncBmZBZOh1KjYxTHYhfH8Vil5LS', 'Rohan Verma',  DATE '2000-01-10', '9876501003', 'ACTIVE');

-- 5.5.2 insurance_product
INSERT INTO insurance_product (product_id, product_code, policy_name, policy_type, term_months, premium_frequency,
                               premium_amount, sum_insured, min_entry_age, max_entry_age, active) VALUES
    (201, 'HD-TERM-001',   'HealthDekho Secure Term',   'TERM',    240, 'MONTHLY',   1250.00,  5000000.00, 18, 60, TRUE),
    (202, 'HD-HEALTH-001', 'HealthDekho Family Health', 'HEALTH',   12, 'YEARLY',   18000.00,  1000000.00, 18, 65, TRUE),
    (203, 'HD-SAV-001',    'HealthDekho Smart Savings', 'SAVINGS', 120, 'QUARTERLY', 7500.00,  1500000.00, 21, 55, TRUE);

-- 5.5.3 customer_policy
INSERT INTO customer_policy (policy_id, policy_number, customer_id, product_id, term_months, start_date, maturity_date,
                             premium_frequency, premium_amount, sum_insured, total_premium, status) VALUES
    (3001, 'HDP-2026-0001', 1001, 201, 240, DATE '2026-11-20', DATE '2046-11-19', 'MONTHLY', 1250.00,  5000000.00, 300000.00, 'ACTIVE'),
    (3002, 'HDP-2026-0002', 1002, 202,  12, DATE '2026-11-20', DATE '2027-11-19', 'YEARLY', 18000.00, 1000000.00,  18000.00, 'ACTIVE');

-- 5.5.4 premium_installment (rows exactly as in the spec)
INSERT INTO premium_installment (installment_id, policy_id, installment_no, due_date, grace_end_date,
                                 scheduled_amount, paid_amount, payment_status) VALUES
    (4001, 3001, 1, DATE '2026-11-20', DATE '2026-12-20',  1250.00,  1250.00, 'PAID'),
    (4002, 3001, 2, DATE '2026-12-20', DATE '2027-01-20',  1250.00,   500.00, 'PARTIALLY_PAID'),
    (4003, 3001, 3, DATE '2027-01-20', DATE '2027-02-20',  1250.00,     0.00, 'PENDING'),
    (4004, 3002, 1, DATE '2026-11-20', DATE '2026-12-20', 18000.00, 18000.00, 'PAID');

-- The spec lists only the first 3 of policy 3001's 240 monthly installments.
-- Generate 4..240 so the schedule matches total_premium (240 x 1250 = 300000)
-- and the "fully paid only when all installments are paid" rule works.
INSERT INTO premium_installment (installment_id, policy_id, installment_no, due_date, grace_end_date,
                                 scheduled_amount, paid_amount, payment_status)
SELECT 4001 + n,
       3001,
       n,
       (DATE '2026-11-20' + make_interval(months => n - 1))::date,
       (DATE '2026-11-20' + make_interval(months => n))::date,
       1250.00,
       0.00,
       'PENDING'
FROM generate_series(4, 240) AS n;

-- 5.5.5 premium_payment
INSERT INTO premium_payment (payment_id, installment_id, transaction_reference, payment_date,
                             amount_paid, penalty_amount, payment_method) VALUES
    (5001, 4001, 'TXN-HD-000001', DATE '2026-11-20',  1250.00, 0.00, 'UPI'),
    (5002, 4002, 'TXN-HD-000002', DATE '2026-12-20',   500.00, 0.00, 'NET_BANKING'),
    (5003, 4004, 'TXN-HD-000003', DATE '2026-11-20', 18000.00, 0.00, 'CARD');

-- 5.5.6 ai_recommendation_request
INSERT INTO ai_recommendation_request (recommendation_request_id, customer_id, insurance_goals, preferred_term_months,
                                       preferred_policy_type, calculated_age, model_name, model_version,
                                       prompt_version, requested_at) VALUES
    (6001, 1001, 'Family income protection',             240, 'TERM',   36, 'configured-chat-model', 'deployment-v1', 'rec-v1', TIMESTAMPTZ '2026-11-20 10:15:00+05:30'),
    (6002, 1003, 'Health coverage for self and parents',  12, 'HEALTH', 26, 'configured-chat-model', 'deployment-v1', 'rec-v1', TIMESTAMPTZ '2026-11-20 10:30:00+05:30');

-- 5.5.7 ai_recommendation_item
INSERT INTO ai_recommendation_item (recommendation_item_id, recommendation_request_id, product_id, rank_order,
                                    recommendation_reason, suitability, selected_for_registration) VALUES
    (7001, 6001, 201, 1, 'Long-term protection aligned with income-replacement goal', 'HIGH',   TRUE),
    (7002, 6001, 203, 2, 'Savings option supports a secondary wealth goal',           'MEDIUM', FALSE),
    (7003, 6002, 202, 1, 'Annual family health cover matches stated preference',      'HIGH',   FALSE);

-- Move identity sequences past the explicit ids so new rows don't collide.
SELECT setval(pg_get_serial_sequence('customer', 'customer_id'), (SELECT max(customer_id) FROM customer));
SELECT setval(pg_get_serial_sequence('insurance_product', 'product_id'), (SELECT max(product_id) FROM insurance_product));
SELECT setval(pg_get_serial_sequence('customer_policy', 'policy_id'), (SELECT max(policy_id) FROM customer_policy));
SELECT setval(pg_get_serial_sequence('premium_installment', 'installment_id'), (SELECT max(installment_id) FROM premium_installment));
SELECT setval(pg_get_serial_sequence('premium_payment', 'payment_id'), (SELECT max(payment_id) FROM premium_payment));
SELECT setval(pg_get_serial_sequence('ai_recommendation_request', 'recommendation_request_id'), (SELECT max(recommendation_request_id) FROM ai_recommendation_request));
SELECT setval(pg_get_serial_sequence('ai_recommendation_item', 'recommendation_item_id'), (SELECT max(recommendation_item_id) FROM ai_recommendation_item));

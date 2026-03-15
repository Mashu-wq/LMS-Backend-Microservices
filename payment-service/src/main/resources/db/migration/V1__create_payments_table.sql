-- Payment Service Schema
-- Each row represents a single payment attempt for a course purchase.
-- idempotency_key prevents duplicate charges if the client retries a request.

CREATE TABLE payments (
    payment_id      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    course_id       UUID            NOT NULL,
    amount          DECIMAL(10, 2)  NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED','REFUND_PENDING','REFUNDED')),
    payment_method  VARCHAR(20)     NOT NULL
                                    CHECK (payment_method IN ('CREDIT_CARD','DEBIT_CARD','PAYPAL')),
    transaction_id  VARCHAR(255),
    failure_reason  VARCHAR(500),
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

-- Fast look-up of all payments for a given user (billing history)
CREATE INDEX idx_payments_user_id    ON payments (user_id);

-- Fast look-up of all payments for a given course (revenue reporting)
CREATE INDEX idx_payments_course_id  ON payments (course_id);

-- Filter by status (e.g. find all PENDING payments for a retry job)
CREATE INDEX idx_payments_status     ON payments (status);

-- Composite: check whether a user has already paid for a course
CREATE INDEX idx_payments_user_course ON payments (user_id, course_id);

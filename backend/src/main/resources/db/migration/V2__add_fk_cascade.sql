-- V2__add_fk_cascade.sql
-- Add ON DELETE CASCADE to all parent→child foreign keys so that deleting
-- a user/portfolio/security cascades cleanly rather than raising a FK constraint
-- violation (CR-07).  ON DELETE RESTRICT is added for security_id references
-- (positions and transactions) — a security should not be deleted while it has
-- open positions; application code must remove positions first.
--
-- PostgreSQL requires dropping and recreating FK constraints to change their
-- ON DELETE behaviour.

-- portfolios.user_id → app_users(id)
ALTER TABLE portfolios
    DROP CONSTRAINT portfolios_user_id_fkey,
    ADD  CONSTRAINT portfolios_user_id_fkey
         FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE;

-- positions.portfolio_id → portfolios(id)
ALTER TABLE positions
    DROP CONSTRAINT positions_portfolio_id_fkey,
    ADD  CONSTRAINT positions_portfolio_id_fkey
         FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE;

-- positions.security_id → securities(id)
ALTER TABLE positions
    DROP CONSTRAINT positions_security_id_fkey,
    ADD  CONSTRAINT positions_security_id_fkey
         FOREIGN KEY (security_id) REFERENCES securities(id) ON DELETE RESTRICT;

-- transactions.portfolio_id → portfolios(id)
ALTER TABLE transactions
    DROP CONSTRAINT transactions_portfolio_id_fkey,
    ADD  CONSTRAINT transactions_portfolio_id_fkey
         FOREIGN KEY (portfolio_id) REFERENCES portfolios(id) ON DELETE CASCADE;

-- transactions.security_id → securities(id)
ALTER TABLE transactions
    DROP CONSTRAINT transactions_security_id_fkey,
    ADD  CONSTRAINT transactions_security_id_fkey
         FOREIGN KEY (security_id) REFERENCES securities(id) ON DELETE RESTRICT;

-- ohlcv_bars.security_id → securities(id)
ALTER TABLE ohlcv_bars
    DROP CONSTRAINT ohlcv_bars_security_id_fkey,
    ADD  CONSTRAINT ohlcv_bars_security_id_fkey
         FOREIGN KEY (security_id) REFERENCES securities(id) ON DELETE CASCADE;

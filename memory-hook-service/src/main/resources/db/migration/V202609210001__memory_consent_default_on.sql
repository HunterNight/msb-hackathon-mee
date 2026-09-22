-- Long-term memory is on by default; a customer who turns it off keeps an explicit row with false.
-- The service already treats a missing row as on (ConsentServiceImpl.defaults); this keeps a raw insert
-- that omits the column consistent with it. Existing rows are left exactly as the customer set them.
alter table memory_consent alter column long_term set default true;

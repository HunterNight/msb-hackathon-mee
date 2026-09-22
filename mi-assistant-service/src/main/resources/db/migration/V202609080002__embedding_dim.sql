-- Widen the vector column so a trained embedding model can be used instead of the hashing
-- fallback. The existing vectors are dropped rather than converted: they came from a different
-- function and mean nothing to the new one. mi re-indexes from the CMS on the next backfill.
delete from document_chunk;
alter table document_chunk alter column embedding type vector(1024);

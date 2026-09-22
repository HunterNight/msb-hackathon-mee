-- Retrieval was returning the same document twice.
--
-- Asked about credit-card annual fees, mi's own retrieval answered with "Biểu phí thẻ 09/2026" in two
-- of six slots at an identical score, because the document was indexed twice with byte-identical
-- content. Eight documents were affected — every one of the originally seeded set. There is no
-- constraint stopping it: the indexer inserts, and a second pass over a document it has already seen
-- simply adds another row.
--
-- The cost is not cosmetic. RAG_TOP_K is 6, so each duplicate spends a slot that a different document
-- would have filled, and both the answer and its citations narrow to fewer real sources than the
-- corpus holds. It matters more now that the VRM agent reads this same index: a duplicate crowds the
-- passages it is told to answer from.

-- Keep the earliest row of each identical group; ctid breaks the tie when ids sort equally.
delete from mi.document_chunk c
 using mi.document_chunk keep
 where c.doc_id = keep.doc_id
   and coalesce(c.section, '') = coalesce(keep.section, '')
   and coalesce(c.content, '') = coalesce(keep.content, '')
   and c.locale = keep.locale
   and c.collection_code = keep.collection_code
   and c.id > keep.id;

-- And stop it recurring. coalesce is required: Postgres treats NULLs as distinct in a unique index, so
-- a plain unique(doc_id, section, ...) would still admit duplicates of any chunk with no section.
create unique index if not exists ux_document_chunk_identity
    on mi.document_chunk (doc_id, collection_code, locale, coalesce(section, ''));

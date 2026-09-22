package com.app.service.job;

import com.app.service.ProposalService;
import com.app.service.rag.KnowledgeIndexer;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Proposal expiry, which is what turns an unanswered card into a BLOCKED log entry (§3.2). */
@Component
public class MiJobs {

  private static final Logger log = LoggerFactory.getLogger(MiJobs.class);
  private static final int BATCH = 200;

  private final ProposalService proposalService;
  private final KnowledgeIndexer indexer;

  public MiJobs(
      ProposalService proposalService,
      KnowledgeIndexer indexer) {
    this.proposalService = proposalService;
    this.indexer = indexer;
  }

  /**
   * Self-healing index. Normally a document is embedded the moment the CMS publishes it; this
   * catches everything that never produced a webhook — a fresh environment, a restored database, or
   * knowledge seeded by a migration.
   *
   * <p>It reconciles rather than bootstraps. The previous version returned as soon as the index held
   * any chunk, which meant only the very first empty-index run ever did anything: documents added by
   * a later migration stayed unsearchable indefinitely.
   */
  @Scheduled(initialDelayString = "PT45S", fixedDelayString = "PT30M")
  @SchedulerLock(name = "mi-knowledge-backfill", lockAtLeastFor = "PT1M", lockAtMostFor = "PT15M")
  public void backfillIndex() {
    int docs = indexer.indexMissing();
    if (docs > 0) {
      log.info("indexed {} published documents that had no chunks", docs);
    }
  }

  @Scheduled(fixedDelayString = "PT1M")
  @SchedulerLock(name = "mi-proposal-expiry", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
  public void expireProposals() {
    int expired = proposalService.expireOverdue(BATCH);
    if (expired > 0) {
      log.info("expired {} proposals past their TTL", expired);
    }
  }
}

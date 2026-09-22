package com.app.service.client;

import com.app.dto.internal.MiInternal.AgentSpec;
import com.app.dto.internal.MiInternal.ModelProfile;
import com.app.dto.internal.MiInternal.RouterConfig;
import com.app.dto.internal.MiInternal.ToolSpec;
import com.app.dto.internal.MiInternal.TriggerRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything Mi knows or may do is authored in the CMS, so this is the widest of Mi's
 * dependencies — and the one that must never take a request down (design §M3.5).
 */
public interface CmsClient {

  List<AgentSpec> agents();

  Optional<AgentSpec> agent(String code);

  Optional<RouterConfig> router();

  List<ToolSpec> tools();

  List<TriggerRule> triggers();

  List<ModelProfile> modelProfiles();

  Optional<CmsDocument> currentDoc(UUID docId);

  List<UUID> publishedDocIds(String collectionCode);

  /** Thumbs from the app; the CMS evaluation dashboard reads them. */
  void feedback(
      UUID conversationId, UUID messageId, String agentCode, String rating, String comment,
      UUID customerId);

  record CmsSection(String heading, String body, String deepLink) {}

  record CmsDocument(
      UUID id,
      String collectionCode,
      String title,
      String locale,
      int version,
      List<CmsSection> sections) {}
}

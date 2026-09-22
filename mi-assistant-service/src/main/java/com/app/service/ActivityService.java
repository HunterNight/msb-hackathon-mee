package com.app.service;

import com.app.dto.request.MiRequests.LogActivityRequest;
import com.app.dto.response.MiResponses.ActivityGroupDto;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/** Screen 10. Keys go in, localised sentences come out (design §M2.5). */
public interface ActivityService {

  UUID log(LogActivityRequest request);

  void log(
      UUID customerId,
      String kind,
      String titleKey,
      Map<String, Object> titleArgs,
      String subtitleKey,
      Map<String, Object> subtitleArgs,
      UUID proposalId,
      String ref,
      String deepLink);

  List<ActivityGroupDto> feed(UUID customerId, String filter, Pageable pageable, Locale locale);
}

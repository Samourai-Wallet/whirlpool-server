package com.samourai.whirlpool.server.controllers.websocket;

import com.google.common.collect.ImmutableMap;
import com.samourai.javaserver.exceptions.NotifiableException;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.config.websocket.IpHandshakeInterceptor;
import com.samourai.whirlpool.server.exceptions.IllegalInputException;
import com.samourai.whirlpool.server.services.ExportService;
import com.samourai.whirlpool.server.services.RegisterInputService;
import com.samourai.whirlpool.server.services.WebSocketService;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

public abstract class AbstractWebSocketController {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private WebSocketService webSocketService;
  private ExportService exportService;

  public AbstractWebSocketController(
      WebSocketService webSocketService, ExportService exportService) {
    this.webSocketService = webSocketService;
    this.exportService = exportService;
  }

  protected void validateHeaders(StompHeaderAccessor headers) throws Exception {
    String clientProtocolVersion =
        headers.getFirstNativeHeader(WhirlpoolProtocol.HEADER_PROTOCOL_VERSION);
    if (!WhirlpoolProtocol.PROTOCOL_VERSION.equals(clientProtocolVersion)) {
      throw new IllegalInputException(
          "Version mismatch: server="
              + WhirlpoolProtocol.PROTOCOL_VERSION
              + ", client="
              + (clientProtocolVersion != null ? clientProtocolVersion : "unknown"));
    }
  }

  private boolean noStackTrace(Exception e) {
    return NotifiableException.class.isAssignableFrom(e.getClass());
  }

  protected void handleException(
      Exception e,
      Principal principal,
      SimpMessageHeaderAccessor messageHeaderAccessor,
      String activity) {

    NotifiableException notifiable = NotifiableException.computeNotifiableException(e);
    String message = notifiable.getMessage();
    String username = principal.getName();
    webSocketService.sendPrivateError(username, message);

    // skip local checkCheck
    if (!RegisterInputService.ERROR_INVALID_HASH.equals(message)) {
      if (noStackTrace(e)) {
        log.warn("handleException: " + e.getMessage());
      } else {
        log.error("handleException", e);
      }

      // log activity
      Map<String, String> clientDetails = computeClientDetails(messageHeaderAccessor);
      clientDetails.put("u", username);
      Map<String, String> details = ImmutableMap.of("error", message);
      String ip = IpHandshakeInterceptor.getIp(messageHeaderAccessor);
      ActivityCsv activityCsv = new ActivityCsv(activity, null, details, ip, clientDetails);
      getExportService().exportActivity(activityCsv);
    }
  }

  protected Map<String, String> computeClientDetails(
      SimpMessageHeaderAccessor messageHeaderAccessor) {
    Map<String, List<String>> nativeHeaders =
        (Map) messageHeaderAccessor.getHeader("nativeHeaders");
    if (nativeHeaders == null) {
      return null;
    }
    String[] ignoreHeaders =
        new String[] {"content-type", "content-length", "destination", "protocolVersion"};

    Map<String, String> clientDetails = new LinkedHashMap<>();
    nativeHeaders
        .entrySet()
        .stream()
        .filter(e -> !ArrayUtils.contains(ignoreHeaders, e.getKey()))
        .forEach(
            e -> {
              String value =
                  e.getValue().size() == 1 ? e.getValue().get(0) : e.getValue().toString();
              clientDetails.put(e.getKey(), value);
            });
    return clientDetails;
  }

  protected WebSocketService getWebSocketService() {
    return webSocketService;
  }

  protected ExportService getExportService() {
    return exportService;
  }
}

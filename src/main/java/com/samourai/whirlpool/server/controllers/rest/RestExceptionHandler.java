package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.javaserver.rest.AbstractRestExceptionHandler;
import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import com.samourai.whirlpool.server.beans.export.ActivityCsv;
import com.samourai.whirlpool.server.services.ExportService;
import java.lang.invoke.MethodHandles;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class RestExceptionHandler extends AbstractRestExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private ExportService exportService;

  @Autowired
  public RestExceptionHandler(ExportService exportService) {
    super();
    this.exportService = exportService;
  }

  @Override
  protected Object handleError(
      com.samourai.javaserver.exceptions.NotifiableException e, HttpServletRequest request) {
    log.warn("RestException -> " + e.getMessage());

    // log activity
    ActivityCsv activityCsv =
        new ActivityCsv("REST:ERROR", request.getRequestURI(), e.getMessage(), null, request);
    exportService.exportActivity(activityCsv);

    return new RestErrorResponse(e.getMessage());
  }
}

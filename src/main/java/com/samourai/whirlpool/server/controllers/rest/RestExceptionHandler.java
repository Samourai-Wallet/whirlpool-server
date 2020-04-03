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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
  protected Object handleError(com.samourai.javaserver.exceptions.NotifiableException e) {
    log.warn("RestException -> " + e.getMessage());

    HttpServletRequest request =
        ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

    // log activity
    ActivityCsv activityCsv = new ActivityCsv("REST:ERROR", null, e.getMessage(), null, request);
    exportService.exportActivity(activityCsv);

    return new RestErrorResponse(e.getMessage());
  }
}

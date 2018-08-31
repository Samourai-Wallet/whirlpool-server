package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import javax.servlet.http.HttpServletResponse;

@RestController
public class RestErrorController implements ErrorController {
    private static final String ENDPOINT = "/error";

    @Autowired
    private ErrorAttributes errorAttributes;

    @RequestMapping(value = ENDPOINT)
    ResponseEntity<RestErrorResponse> error(WebRequest webRequest, HttpServletResponse response){
        Throwable cause = errorAttributes.getError(webRequest);
        RestErrorResponse restErrorResponse = new RestErrorResponse();
        restErrorResponse.message = (cause != null ? cause.getMessage() : "system error");
        return ResponseEntity.status(response.getStatus()).body(restErrorResponse);
    }

    @Override
    public String getErrorPath(){
        return ENDPOINT;
    }
}
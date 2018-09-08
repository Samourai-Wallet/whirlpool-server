package com.samourai.whirlpool.server.controllers.rest;

import com.samourai.whirlpool.protocol.rest.RestErrorResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;

@RestController
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {
    private static final String ENDPOINT = "/error";

    @Autowired
    private ErrorAttributes errorAttributes;

    // error for REST
    @RequestMapping(value = ENDPOINT, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RestErrorResponse> errorJson(WebRequest webRequest, HttpServletResponse response){
        RestErrorResponse restErrorResponse = new RestErrorResponse();
        restErrorResponse.message = getErrorMessage(webRequest, response.getStatus());
        return ResponseEntity.status(response.getStatus()).body(restErrorResponse);
    }

    // error for HTML
    @RequestMapping(value = ENDPOINT)
    public ModelAndView errorHtml(WebRequest webRequest, HttpServletResponse response, Model model){
        String errorMessage = getErrorMessage(webRequest, response.getStatus());
        model.addAttribute("errorMessage", errorMessage);
        return new ModelAndView("/error.html");
    }

    private String getErrorMessage(WebRequest webRequest, int status) {
        Throwable cause = errorAttributes.getError(webRequest);
        return (cause != null ? cause.getMessage() : String.valueOf(status));
    }

    @Override
    public String getErrorPath(){
        return ENDPOINT;
    }
}
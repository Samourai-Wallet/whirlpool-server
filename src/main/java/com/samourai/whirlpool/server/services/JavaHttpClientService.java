package com.samourai.whirlpool.server.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samourai.wallet.api.backend.IBackendClient;
import com.samourai.wallet.api.backend.beans.HttpException;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class JavaHttpClientService implements IBackendClient {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String USER_AGENT = "whirlpool-server " + WhirlpoolProtocol.PROTOCOL_VERSION;

  private ObjectMapper objectMapper;

  public JavaHttpClientService() {
    this.objectMapper = new ObjectMapper();
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Override
  public <T> T getJson(String urlStr, Class<T> responseType) throws HttpException {
    try {
      HttpEntity request = new HttpEntity(computeHeaders());

      // execute
      ResponseEntity<T> response =
          new RestTemplate().exchange(urlStr, HttpMethod.GET, request, responseType);
      checkSuccess(response);
      return response.getBody();
    } catch (Exception e) {
      if (!(e instanceof HttpException)) {
        e = new HttpException(e, null);
      }
      throw (HttpException) e;
    }
  }

  @Override
  public <T> T postUrlEncoded(String urlStr, Class<T> responseType, Map<String, String> body)
      throws HttpException {
    try {
      HttpHeaders headers = computeHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
      HttpEntity<Map<String, String>> request = new HttpEntity<Map<String, String>>(body, headers);

      // execute
      ResponseEntity<T> response =
          new RestTemplate().exchange(urlStr, HttpMethod.POST, request, responseType);
      checkSuccess(response);
      return response.getBody();
    } catch (Exception e) {
      if (!(e instanceof HttpException)) {
        e = new HttpException(e, null);
      }
      throw (HttpException) e;
    }
  }

  private HttpHeaders computeHeaders() {
    final HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
    return headers;
  }

  private void checkSuccess(ResponseEntity response) throws HttpException {
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new HttpException(
          new Exception("Request failed: statusCode=" + response.getStatusCodeValue()), null);
    }
  }
}

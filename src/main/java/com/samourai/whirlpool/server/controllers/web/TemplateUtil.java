package com.samourai.whirlpool.server.controllers.web;

import com.samourai.whirlpool.server.utils.Utils;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

// used in thymeleaf templates
@Component
public class TemplateUtil {
  public BigDecimal satoshisToBtc(long sats) {
    return Utils.satoshisToBtc(sats);
  }
}

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

  public String duration(int seconds) {
    StringBuffer sb = new StringBuffer();
    if (seconds > 60) {
      int minutes = (int) Math.floor(seconds / 60);

      if (minutes > 60) {
        int hours = (int) Math.floor(minutes / 60);
        sb.append(hours + "h");
        minutes -= hours * 60;
      }

      sb.append(minutes + "m");
      seconds -= minutes * 60;
    }
    sb.append(seconds + "s");
    return sb.toString();
  }
}

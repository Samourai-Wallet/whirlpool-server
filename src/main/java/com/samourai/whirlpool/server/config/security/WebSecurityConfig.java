package com.samourai.whirlpool.server.config.security;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.config.websocket.WebSocketConfig;
import com.samourai.whirlpool.server.controllers.web.HistoryWebController;
import com.samourai.whirlpool.server.controllers.web.StatusWebController;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    private static final String[] REST_MIX_ENDPOINTS = new String[]{WhirlpoolProtocol.ENDPOINT_POOLS, WhirlpoolProtocol.ENDPOINT_REGISTER_OUTPUT};
    private static final String[] ADMIN_ENDPOINTS = new String[]{StatusWebController.ENDPOINT, HistoryWebController.ENDPOINT};

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        String[] statics = new String[]{"/css/**.css", "/webjars/bootstrap/**", "/webjars/jquery/**"};

        // disable csrf for mixing
        http.csrf().ignoringAntMatchers(REST_MIX_ENDPOINTS)

          .and().authorizeRequests()

          // public statics
          .antMatchers(statics).permitAll()

          // public mixing websocket
          .antMatchers(WebSocketConfig.WEBSOCKET_ENDPOINTS).permitAll()
          .antMatchers(REST_MIX_ENDPOINTS).permitAll();
    }
}
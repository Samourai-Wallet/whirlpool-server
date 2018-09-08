package com.samourai.whirlpool.server.config.security;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.config.websocket.WebSocketConfig;
import com.samourai.whirlpool.server.controllers.web.ConfigWebController;
import com.samourai.whirlpool.server.controllers.web.HistoryWebController;
import com.samourai.whirlpool.server.controllers.web.StatusWebController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    private static final String[] REST_MIX_ENDPOINTS = new String[]{WhirlpoolProtocol.ENDPOINT_POOLS, WhirlpoolProtocol.ENDPOINT_REGISTER_OUTPUT};

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
        .antMatchers(REST_MIX_ENDPOINTS).permitAll()

        // restrict admin
        .antMatchers(StatusWebController.ENDPOINT).hasAnyAuthority(WhirlpoolPrivilege.STATUS.toString(), WhirlpoolPrivilege.ALL.toString())
        .antMatchers(HistoryWebController.ENDPOINT).hasAnyAuthority(WhirlpoolPrivilege.HISTORY.toString(), WhirlpoolPrivilege.ALL.toString())
        .antMatchers(ConfigWebController.ENDPOINT).hasAnyAuthority(WhirlpoolPrivilege.CONFIG.toString(), WhirlpoolPrivilege.ALL.toString())

        // reject others
        .anyRequest().denyAll()
        .and()
        .formLogin().successHandler(successHandler());
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(WhirlpoolUserDetailsService whirlpoolUserDetailsService) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(whirlpoolUserDetailsService);
        authProvider.setPasswordEncoder(encoder());
        return authProvider;
    }

    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder(11);
    }

    @Bean
    public AuthenticationSuccessHandler successHandler() {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler();
        handler.setUseReferer(true);
        handler.setDefaultTargetUrl(StatusWebController.ENDPOINT);
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }
}
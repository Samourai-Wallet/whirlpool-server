package com.samourai.whirlpool.server.config.security;

import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import com.samourai.whirlpool.server.config.websocket.WebSocketConfig;
import com.samourai.whirlpool.server.controllers.web.ConfigWebController;
import com.samourai.whirlpool.server.controllers.web.HistoryWebController;
import com.samourai.whirlpool.server.controllers.web.LoginWebController;
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

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    private static final String[] REST_MIX_ENDPOINTS = new String[]{WhirlpoolProtocol.ENDPOINT_POOLS, WhirlpoolProtocol.ENDPOINT_REGISTER_OUTPUT};
    private static final String[] STATICS = new String[]{"/css/**.css", "/webjars/bootstrap/**", "/webjars/jquery/**"};

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        // disable csrf for mixing
        http.csrf().ignoringAntMatchers(REST_MIX_ENDPOINTS)

        .and().authorizeRequests()

        // public statics
        .antMatchers(STATICS).permitAll()

        // public login form
        .antMatchers(LoginWebController.ENDPOINT).permitAll()
        .antMatchers(LoginWebController.PROCESS_ENDPOINT).permitAll()

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

        // custom login form
        .formLogin()
        .loginProcessingUrl(LoginWebController.PROCESS_ENDPOINT)
        .loginPage(LoginWebController.ENDPOINT)
        .defaultSuccessUrl(StatusWebController.ENDPOINT,true);
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
}
package com.samourai.whirlpool.server.config.security;

import com.samourai.javaserver.config.security.ServerUserDetailsService;
import com.samourai.whirlpool.server.persistence.repositories.UserRepository;
import com.samourai.whirlpool.server.persistence.to.UserTO;
import org.springframework.stereotype.Service;

@Service
public class WhirlpoolUserDetailsService extends ServerUserDetailsService<UserTO, UserRepository> {

  public WhirlpoolUserDetailsService(UserRepository userRepository) {
    super(userRepository);
  }
}

package com.samourai.whirlpool.server.config.security;

import com.samourai.whirlpool.server.persistence.repositories.UserRepository;
import com.samourai.whirlpool.server.persistence.to.UserTO;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class WhirlpoolUserDetailsService implements UserDetailsService {
  UserRepository userRepository;

  public WhirlpoolUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
    UserTO userTO =
        userRepository
            .findByLogin(login)
            .orElseThrow(() -> new UsernameNotFoundException("no such user"));
    List<GrantedAuthority> authorities =
        userTO
            .getPrivilegesList()
            .parallelStream()
            .map(privilege -> new SimpleGrantedAuthority(privilege))
            .collect(Collectors.toList());
    return new User(
        userTO.getLogin(), userTO.getPasswordHash(), true, true, true, true, authorities);
  }
}

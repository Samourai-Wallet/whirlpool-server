package com.samourai.whirlpool.server.persistence.repositories;

import com.samourai.javaserver.persistence.repositories.ServerUserRepository;
import com.samourai.whirlpool.server.persistence.to.UserTO;

public interface UserRepository extends ServerUserRepository<UserTO> {}

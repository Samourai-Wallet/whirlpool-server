package com.samourai.whirlpool.server.persistence.repositories;

import com.samourai.whirlpool.server.persistence.to.RoundTO;
import org.springframework.data.repository.CrudRepository;

public interface RoundRepository extends CrudRepository<RoundTO, Long> {

}
package com.samourai.whirlpool.server.persistence.repositories;

import com.samourai.whirlpool.server.persistence.to.MixOutputTO;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface MixOutputRepository extends CrudRepository<MixOutputTO, Long> {

  Optional<MixOutputTO> findByAddress(String address);
}

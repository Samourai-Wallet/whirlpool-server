package com.samourai.whirlpool.server.persistence.repositories;

import com.samourai.whirlpool.server.persistence.to.BlameTO;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface BlameRepository extends CrudRepository<BlameTO, Long> {

  List<BlameTO> findByIdentifierOrderByCreatedAsc(String identifier);
}

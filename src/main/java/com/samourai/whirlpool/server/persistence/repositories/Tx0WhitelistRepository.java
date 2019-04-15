package com.samourai.whirlpool.server.persistence.repositories;

import com.samourai.whirlpool.server.persistence.to.Tx0WhitelistTO;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface Tx0WhitelistRepository extends CrudRepository<Tx0WhitelistTO, Long> {

  Optional<Tx0WhitelistTO> findByTxid(String txid);
}

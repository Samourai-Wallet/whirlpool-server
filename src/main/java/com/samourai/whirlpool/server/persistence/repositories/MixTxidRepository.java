package com.samourai.whirlpool.server.persistence.repositories;

import com.samourai.whirlpool.server.persistence.to.MixTxidTO;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface MixTxidRepository extends CrudRepository<MixTxidTO, Long> {

  Optional<MixTxidTO> findByTxidAndDenomination(String txid, long denomination);
}

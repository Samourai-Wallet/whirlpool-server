package com.samourai.whirlpool.server.persistence.repositories;

import com.samourai.whirlpool.server.persistence.to.BanTO;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface BanRepository extends CrudRepository<BanTO, Long> {

  @Query(
      "SELECT b from ban b WHERE identifier=:identifier AND (expiration IS NULL OR expiration >= :expirationMin)")
  List<BanTO> findByIdentifierAndExpirationAfterOrNull(
      @Param("identifier") String identifier, @Param("expirationMin") Timestamp expirationMin);

  @Query("SELECT b from ban b WHERE (expiration IS NULL OR expiration >= :expirationMin)")
  Page<BanTO> findByExpirationAfterOrNull(
      @Param("expirationMin") Timestamp expirationMin, Pageable pageable);
}

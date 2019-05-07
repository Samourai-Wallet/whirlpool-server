package com.samourai.whirlpool.server.persistence.repositories;

import com.samourai.whirlpool.protocol.websocket.notifications.MixStatus;
import com.samourai.whirlpool.server.persistence.to.MixTO;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

public interface MixRepository extends PagingAndSortingRepository<MixTO, Long> {
  @Query("SELECT COUNT(m) from mix m WHERE mixStatus=:mixStatus")
  Long countByMixStatus(@Param("mixStatus") MixStatus mixStatus);

  @Query("SELECT SUM(nbMustMix*denomination) from mix WHERE mixStatus=:mixStatus")
  Long sumMustMixByMixStatus(@Param("mixStatus") MixStatus mixStatus);

  @Query("SELECT SUM(amountOut) from mix WHERE mixStatus=:mixStatus")
  Long sumAmountOutByMixStatus(@Param("mixStatus") MixStatus mixStatus);
}

package com.samourai.whirlpool.server.persistence.repositories;

import com.samourai.whirlpool.server.persistence.to.RoundTO;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RoundRepository extends PagingAndSortingRepository<RoundTO, Long> {



}
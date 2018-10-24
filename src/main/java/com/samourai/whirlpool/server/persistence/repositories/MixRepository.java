package com.samourai.whirlpool.server.persistence.repositories;

import com.samourai.whirlpool.server.persistence.to.MixTO;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface MixRepository extends PagingAndSortingRepository<MixTO, Long> {}

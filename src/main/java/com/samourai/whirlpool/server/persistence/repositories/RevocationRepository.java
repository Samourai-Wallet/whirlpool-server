package com.samourai.whirlpool.server.persistence.repositories;

import com.samourai.whirlpool.server.beans.RevocationType;
import com.samourai.whirlpool.server.persistence.to.RevocationTO;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface RevocationRepository extends CrudRepository<RevocationTO, Long> {

    Optional<RevocationTO> findByRevocationTypeAndValue(RevocationType revocationType, String value);
}
package com.bank.bootcamp.fixedaccounts.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import com.bank.bootcamp.fixedaccounts.entity.Account;
import reactor.core.publisher.Mono;

public interface AccountRepository extends ReactiveMongoRepository<Account, String> {

  Mono<Account> findByCustomerId(String customerId);

}

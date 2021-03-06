package com.bank.bootcamp.fixedaccounts.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import com.bank.bootcamp.fixedaccounts.entity.Transaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransactionRepository extends ReactiveMongoRepository<Transaction, String> {

  Flux<Transaction> findByAccountId(String accountId);
  Flux<Transaction> findByAccountIdAndRegisterDateBetween(String accountId, LocalDateTime from, LocalDateTime to);
  
  @Aggregation(pipeline = {
      "{ $match: { accountId: ?0 }}",
      "{ $group: { _id: '', total: {$sum: $amount }}}"
  })
  public Mono<Double> getBalanceByAccountId(String accountId);
  
  @Aggregation(pipeline = {
      "{ $match: { accountId: ?0, registerDate : { $lt: ?1 } }}",
      "{ $group: { _id: '', total: {$sum: $amount }}}"
  })
  public Mono<Double> getBalanceByAccountIdToDate(String accountId, LocalDate toDate);
}

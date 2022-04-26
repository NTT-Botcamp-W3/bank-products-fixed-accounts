package com.bank.bootcamp.fixedaccounts.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.bank.bootcamp.fixedaccounts.dto.BalanceDTO;
import com.bank.bootcamp.fixedaccounts.dto.CreateAccountDTO;
import com.bank.bootcamp.fixedaccounts.dto.CreateTransactionDTO;
import com.bank.bootcamp.fixedaccounts.entity.Account;
import com.bank.bootcamp.fixedaccounts.entity.Transaction;
import com.bank.bootcamp.fixedaccounts.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("savingAccounts")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;
  private ObjectMapper mapper = new ObjectMapper();
  
  @GetMapping("/balance/{accountId}")
  public Mono<BalanceDTO> getBalanceByAccountId(@PathVariable("accountId") String accountId) {
    return accountService.getBalanceByAccountId(accountId);
  }
  
  @GetMapping("/balance/byCustomer/{customerId}")
  public Flux<BalanceDTO> getBalancesByCustomerId(@PathVariable("customerId") String customerId) {
    return accountService.getBalancesByCustomerId(customerId);
  }
  
  @PostMapping
  public Mono<String> createAccount(@RequestBody CreateAccountDTO dto) throws Exception {
    var account = mapper.readValue(mapper.writeValueAsString(dto), Account.class);
    return accountService.createAccount(account).map(Account::getId);
  }
  
  @PostMapping("/transaction")
  public Mono<Integer> createTransaction(@RequestBody CreateTransactionDTO dto) {
    return accountService.createTransaction(dto).map(Transaction::getOperationNumber);
  }
  
  @GetMapping("/byCustomer/{customerId}")
  public Flux<Account> getAccountsByCustomer(@PathVariable("customerId") String customerId) {
    return accountService.getAccountsByCustomer(customerId);
  }
  
  @GetMapping("movements/{accountId}/{year}/{month}")
  public Flux<Transaction> getMovementsByAccountAndPeriod(
      @PathVariable("accountId") String accountId,
      @PathVariable("year") Integer year, @PathVariable("month") Integer month) {
    return accountService.getTransactionsByAccountIdAndPeriod(accountId, LocalDate.of(year, month, 1));
  }
  
}
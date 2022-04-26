package com.bank.bootcamp.fixedaccounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.bank.bootcamp.fixedaccounts.dto.CreateTransactionDTO;
import com.bank.bootcamp.fixedaccounts.entity.Account;
import com.bank.bootcamp.fixedaccounts.entity.Transaction;
import com.bank.bootcamp.fixedaccounts.exception.BankValidationException;
import com.bank.bootcamp.fixedaccounts.repository.AccountRepository;
import com.bank.bootcamp.fixedaccounts.repository.TransactionRepository;
import com.bank.bootcamp.fixedaccounts.service.AccountService;
import com.bank.bootcamp.fixedaccounts.service.NextSequenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class FixedAcountsApplicationTests {

  private static AccountService accountService;
  private static AccountRepository accountRepository;
  private static TransactionRepository transactionRepository;
  private static NextSequenceService nextSequenceService;
  private ObjectMapper mapper = new ObjectMapper();
  
  @BeforeAll
  public static void setup() {
    accountRepository = mock(AccountRepository.class);
    transactionRepository = mock(TransactionRepository.class);
    nextSequenceService = mock(NextSequenceService.class);
    accountService = new AccountService(accountRepository, transactionRepository, nextSequenceService);
  }
  
  private Account getAccount() {
    var account = new Account();
    account.setCustomerId("id123456");
    account.setMonthlyMovementLimit(1);
    account.setAssignedDayNumberForMovement(20);
    return account;
  }

  @Test
  public void createAccountWithAllData() throws Exception {
    
    var account = getAccount();
    
    var savedAccount = mapper.readValue(mapper.writeValueAsString(account), Account.class);
    savedAccount.setId(UUID.randomUUID().toString());
    
    when(accountRepository.findByCustomerId(account.getCustomerId())).thenReturn(Mono.empty());
    when(accountRepository.save(account)).thenReturn(Mono.just(savedAccount));
    
    var mono = accountService.createAccount(account);
    StepVerifier.create(mono).assertNext(acc -> {
      assertThat(acc.getMonthlyMovementLimit()).isEqualTo(1);
      assertThat(acc.getId()).isNotNull();
    }).verifyComplete();
    
  }
  
  @Test
  public void createPositiveTransactionWithExistentAccount() throws Exception {
    var accountId = "acc123";
    
    var account = new Account();
    account.setId(accountId);
    account.setMonthlyMovementLimit(1);
    account.setAssignedDayNumberForMovement(LocalDateTime.now().get(ChronoField.DAY_OF_MONTH));
    
    var createTransactionDTO = new CreateTransactionDTO();
    createTransactionDTO.setAgent("BCP Huacho - Ventanilla 021");
    createTransactionDTO.setAmount(100d);
    createTransactionDTO.setAccountId(accountId);
    createTransactionDTO.setDescription("Deposito ventanilla");
    
    var transactionSaved = mapper.readValue(mapper.writeValueAsString(createTransactionDTO), Transaction.class);
    transactionSaved.setId(UUID.randomUUID().toString());
    transactionSaved.setOperationNumber(1);
    transactionSaved.setRegisterDate(LocalDateTime.now());
    
    when(nextSequenceService.getNextSequence("TransactionSequences")).thenReturn(Mono.just(1));
    when(transactionRepository.getBalanceByAccountId(accountId)).thenReturn(Mono.just(0d));
    when(accountRepository.findById(accountId)).thenReturn(Mono.just(account));
    Mockito.doReturn(Flux.empty()).when(transactionRepository).findByAccountIdAndRegisterDateBetween(Mockito.anyString(), Mockito.any(LocalDateTime.class), Mockito.any(LocalDateTime.class));
    Mockito.doReturn(Mono.just(transactionSaved)).when(transactionRepository).save(Mockito.any());
    
    var mono = accountService.createTransaction(createTransactionDTO);
    StepVerifier.create(mono).assertNext((saved) -> {
      assertThat(saved).isNotNull();
    }).verifyComplete();
  }
  
  @Test
  public void createPositiveTransactionWithExistentAccountAndOtherDay() throws Exception {
    var accountId = "acc123";
    
    var account = new Account();
    account.setId(accountId);
    account.setMonthlyMovementLimit(1);
    account.setAssignedDayNumberForMovement(LocalDateTime.now().plusDays(1L).get(ChronoField.DAY_OF_MONTH));
    
    var createTransactionDTO = new CreateTransactionDTO();
    createTransactionDTO.setAgent("BCP Huacho - Ventanilla 021");
    createTransactionDTO.setAmount(100d);
    createTransactionDTO.setAccountId(accountId);
    createTransactionDTO.setDescription("Deposito ventanilla");
    
    var transactionSaved = mapper.readValue(mapper.writeValueAsString(createTransactionDTO), Transaction.class);
    transactionSaved.setId(UUID.randomUUID().toString());
    transactionSaved.setOperationNumber(1);
    transactionSaved.setRegisterDate(LocalDateTime.now());
    
    when(nextSequenceService.getNextSequence("TransactionSequences")).thenReturn(Mono.just(1));
    when(transactionRepository.getBalanceByAccountId(accountId)).thenReturn(Mono.just(0d));
    when(accountRepository.findById(accountId)).thenReturn(Mono.just(account));
    Mockito.doReturn(Flux.empty()).when(transactionRepository).findByAccountIdAndRegisterDateBetween(Mockito.anyString(), Mockito.any(LocalDateTime.class), Mockito.any(LocalDateTime.class));
    Mockito.doReturn(Mono.just(transactionSaved)).when(transactionRepository).save(Mockito.any());
    
    var mono = accountService.createTransaction(createTransactionDTO);
    StepVerifier.create(mono).expectError(BankValidationException.class).verify();
  }
  
  @Test
  public void createMoreThanOneTransactionInTheMonth() throws Exception {
    var accountId = "acc123";
    
    var account = new Account();
    account.setId(accountId);
    account.setAssignedDayNumberForMovement(LocalDate.now().get(ChronoField.DAY_OF_MONTH));
    account.setMonthlyMovementLimit(1);
    
    var createTransaction1DTO = new CreateTransactionDTO();
    createTransaction1DTO.setAgent("BCP Huacho - Ventanilla 021");
    createTransaction1DTO.setAmount(100d);
    createTransaction1DTO.setAccountId(accountId);
    createTransaction1DTO.setDescription("Deposito ventanilla");
    
    var createTransaction2DTO = new CreateTransactionDTO();
    createTransaction2DTO.setAgent("BCP Huacho - Ventanilla 002");
    createTransaction2DTO.setAmount(50d);
    createTransaction2DTO.setAccountId(accountId);
    createTransaction2DTO.setDescription("Deposito ventanilla");
    
    var transactionSaved = mapper.readValue(mapper.writeValueAsString(createTransaction1DTO), Transaction.class);
    transactionSaved.setId(UUID.randomUUID().toString());
    transactionSaved.setOperationNumber(1);
    transactionSaved.setRegisterDate(LocalDateTime.now());
    
    Mockito.doReturn(Mono.just(transactionSaved)).when(transactionRepository).save(Mockito.any());
    Mockito.doReturn(Flux.empty()).when(transactionRepository)
      .findByAccountIdAndRegisterDateBetween(Mockito.anyString(), Mockito.any(LocalDateTime.class), Mockito.any(LocalDateTime.class));
    when(nextSequenceService.getNextSequence("TransactionSequences")).thenReturn(Mono.just(1));
    when(transactionRepository.getBalanceByAccountId(accountId)).thenReturn(Mono.just(0d));
    when(accountRepository.findById(createTransaction1DTO.getAccountId())).thenReturn(Mono.just(account));
    
    var mono1 = accountService.createTransaction(createTransaction1DTO);
    StepVerifier.create(mono1)
    .assertNext((saved) -> {
      assertThat(saved).isNotNull();
    }).verifyComplete();
    
    Mockito.doReturn(Flux.just(new Transaction())).when(transactionRepository)
      .findByAccountIdAndRegisterDateBetween(Mockito.anyString(), Mockito.any(LocalDateTime.class), Mockito.any(LocalDateTime.class));
    when(nextSequenceService.getNextSequence("TransactionSequences")).thenReturn(Mono.just(2));
    when(transactionRepository.getBalanceByAccountId(accountId)).thenReturn(Mono.just(100d));
    when(accountRepository.findById(createTransaction2DTO.getAccountId())).thenReturn(Mono.just(account));
    
    var mono2 = accountService.createTransaction(createTransaction2DTO);
    
    StepVerifier.create(mono2)
    .expectError(BankValidationException.class)
    .verify();
  }
  
  @Test
  public void createImposibleTransactionWithExistentAccount() throws Exception {
    var accountId = "acc123";
    var createTransactionDTO = new CreateTransactionDTO();
    createTransactionDTO.setAgent("BCP Huacho - Cajero 021");
    createTransactionDTO.setAmount(-100d); // negative tx with balance 0
    createTransactionDTO.setAccountId(accountId);
    createTransactionDTO.setDescription("Deposito cajero");
    
    var transactionSaved = mapper.readValue(mapper.writeValueAsString(createTransactionDTO), Transaction.class);
    transactionSaved.setId(UUID.randomUUID().toString());
    transactionSaved.setOperationNumber(1);
    transactionSaved.setRegisterDate(LocalDateTime.now());
    
    when(nextSequenceService.getNextSequence("TransactionSequences")).thenReturn(Mono.just(1));
    when(transactionRepository.getBalanceByAccountId(accountId)).thenReturn(Mono.just(0d));
    when(accountRepository.findById(accountId)).thenReturn(Mono.just(new Account()));
    Mockito.doReturn(Mono.just(transactionSaved)).when(transactionRepository).save(Mockito.any());
    
    var mono = accountService.createTransaction(createTransactionDTO);
    StepVerifier.create(mono).expectError().verify();
  }
  
  @Test
  public void createPositiveTransactionWithInexistentAccount() throws Exception {
    var accountId = "acc123";
    var createTransactionDTO = new CreateTransactionDTO();
    createTransactionDTO.setAgent("BCP Huacho - Cajero 021");
    createTransactionDTO.setAmount(100d);
    createTransactionDTO.setAccountId(accountId);
    createTransactionDTO.setDescription("Deposito cajero");
    
    var transactionSaved = mapper.readValue(mapper.writeValueAsString(createTransactionDTO), Transaction.class);
    transactionSaved.setId(UUID.randomUUID().toString());
    transactionSaved.setOperationNumber(1);
    transactionSaved.setRegisterDate(LocalDateTime.now());
    
    when(nextSequenceService.getNextSequence("TransactionSequences")).thenReturn(Mono.just(1));
    when(transactionRepository.getBalanceByAccountId(accountId)).thenReturn(Mono.just(0d));
    when(accountRepository.findById(accountId)).thenReturn(Mono.empty()); // inexistent account
    Mockito.doReturn(Mono.just(transactionSaved)).when(transactionRepository).save(Mockito.any());
    
    var mono = accountService.createTransaction(createTransactionDTO);
    StepVerifier.create(mono).expectError().verify();
  }
  
  @Test
  public void getBalanceTest() {
    var accountId = "account_123";
    var account = new Account();
    account.setId(accountId);
    account.setMonthlyMovementLimit(1);
    
    when(transactionRepository.getBalanceByAccountId(accountId)).thenReturn(Mono.just(100d));
    when(accountRepository.findById(accountId)).thenReturn(Mono.just(account));
    var transaction = new Transaction();
    transaction.setAmount(100d);
    when(transactionRepository.findByAccountIdAndRegisterDateBetween(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Flux.just(transaction));
    var mono = accountService.getBalanceByAccountId(accountId);
    StepVerifier.create(mono).assertNext(balance -> {
      assertThat(balance.getAmount()).isEqualTo(100d);
    }).verifyComplete();
  }
  
  @Test
  public void getTransactionsByAccountAndPeriod() {
    
    String accountId = "ACC123";
    when(transactionRepository.findByAccountIdAndRegisterDateBetween(Mockito.any(), Mockito.any(), Mockito.any()))
      .thenReturn(Flux.just(new Transaction()));
    var flux = accountService.getTransactionsByAccountIdAndPeriod(accountId, LocalDate.of(2022, 4, 1));
    StepVerifier.create(flux).assertNext(tx -> {
      assertThat(tx).isNotNull();
    }).verifyComplete();
  }
  

}
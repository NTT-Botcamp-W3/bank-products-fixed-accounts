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
import org.modelmapper.ModelMapper;
import org.springframework.core.env.Environment;
import com.bank.bootcamp.fixedaccounts.dto.AccountType;
import com.bank.bootcamp.fixedaccounts.dto.CreateAccountDTO;
import com.bank.bootcamp.fixedaccounts.dto.CreateTransactionDTO;
import com.bank.bootcamp.fixedaccounts.dto.TransferDTO;
import com.bank.bootcamp.fixedaccounts.entity.Account;
import com.bank.bootcamp.fixedaccounts.entity.Transaction;
import com.bank.bootcamp.fixedaccounts.exception.BankValidationException;
import com.bank.bootcamp.fixedaccounts.repository.AccountRepository;
import com.bank.bootcamp.fixedaccounts.repository.TransactionRepository;
import com.bank.bootcamp.fixedaccounts.service.AccountService;
import com.bank.bootcamp.fixedaccounts.service.NextSequenceService;
import com.bank.bootcamp.fixedaccounts.webclient.AccountWebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class FixedAcountsApplicationTests {

  private static AccountService accountService;
  private static AccountRepository accountRepository;
  private static TransactionRepository transactionRepository;
  private static NextSequenceService nextSequenceService;
  private static Environment env;
  private static AccountWebClient accountWebClient;
  
  private ModelMapper mapper = new ModelMapper();
  
  @BeforeAll
  public static void setup() {
    accountRepository = mock(AccountRepository.class);
    transactionRepository = mock(TransactionRepository.class);
    nextSequenceService = mock(NextSequenceService.class);
    env = mock(Environment.class);
    accountWebClient = mock(AccountWebClient.class);
    accountService = new AccountService(accountRepository, transactionRepository, nextSequenceService, env, accountWebClient);
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
    var accountDTO = mapper.map(account, CreateAccountDTO.class);
    accountDTO.setOpeningAmount(100d);
    
    var savedAccount = mapper.map(account, Account.class);
    savedAccount.setId(UUID.randomUUID().toString());
    
    when(accountRepository.findByCustomerId(account.getCustomerId())).thenReturn(Mono.empty());
    when(accountRepository.save(Mockito.any(Account.class))).thenReturn(Mono.just(savedAccount));
    
    var mono = accountService.createAccount(accountDTO);
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
    
    var transactionSaved = mapper.map(createTransactionDTO, Transaction.class);
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
    
    var transactionSaved = mapper.map(createTransactionDTO, Transaction.class);
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
    
    var transactionSaved = mapper.map(createTransaction1DTO, Transaction.class);
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
    
    var transactionSaved = mapper.map(createTransactionDTO, Transaction.class);
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
    
    var transactionSaved = mapper.map(createTransactionDTO, Transaction.class);
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
  
  @Test
  public void transfer() {
    var transferDTO = new TransferDTO();
    transferDTO.setAmount(100d);
    transferDTO.setSourceAccountId("CA-001");
    transferDTO.setTargetAccountType(AccountType.SAVING);
    transferDTO.setTargetAccountId("SA-001");
    var amount = 100d;
    //  /transfer
    when(nextSequenceService.getNextSequence("TransactionSequences")).thenReturn(Mono.just(1));
    when(transactionRepository.getBalanceByAccountId(transferDTO.getSourceAccountId())).thenReturn(Mono.just(amount));
    var account = new Account();
    account.setMonthlyMovementLimit(10);
    account.setAssignedDayNumberForMovement(LocalDate.now().get(ChronoField.DAY_OF_MONTH));
    when(accountRepository.findById(transferDTO.getSourceAccountId())).thenReturn(Mono.just(account));
    
    var existentTransaction = new Transaction();
    existentTransaction.setAmount(100d);
    
    when(transactionRepository.findByAccountIdAndRegisterDateBetween(Mockito.anyString(), Mockito.any(LocalDateTime.class), Mockito.any(LocalDateTime.class))).thenReturn(Flux.just(existentTransaction));
    
    var tx = new Transaction();
    tx.setAccountId(transferDTO.getSourceAccountId());
    tx.setAgent("-");
    tx.setOperationNumber(1);
    tx.setAmount(amount);
    tx.setId(UUID.randomUUID().toString());
    tx.setRegisterDate(LocalDateTime.now());
    
    when(transactionRepository.save(Mockito.any(Transaction.class))).thenReturn(Mono.just(tx));
    when(accountWebClient.createTransaction(Mockito.any(AccountType.class), Mockito.any(CreateTransactionDTO.class))).thenReturn(Mono.just(4));
    var mono = accountService.transfer(transferDTO);
    StepVerifier.create(mono).assertNext(operationNumber -> {
      assertThat(operationNumber).isNotNull();
    }).verifyComplete();
  }
  
//  public void averageDailyReport() {
//    var accountId = "Account-001";
//    var existentTransaction1 = new Transaction();
//    existentTransaction1.setAmount(100d);
//    
//    when(transactionRepository.findByAccountIdAndRegisterDateBetween(Mockito.anyString(), Mockito.any(LocalDateTime.class), Mockito.any(LocalDateTime.class))).thenReturn(Flux.just(existentTransaction));
//    var flux = accountService.getAverageDailyReportByAccount(accountId);
//  }
  

}

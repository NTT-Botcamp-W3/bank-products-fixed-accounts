package com.bank.bootcamp.fixedaccounts.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoField;
import java.util.Optional;
import java.util.function.Predicate;
import org.modelmapper.ModelMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import com.bank.bootcamp.fixedaccounts.dto.BalanceDTO;
import com.bank.bootcamp.fixedaccounts.dto.CreateAccountDTO;
import com.bank.bootcamp.fixedaccounts.dto.CreateTransactionDTO;
import com.bank.bootcamp.fixedaccounts.entity.Account;
import com.bank.bootcamp.fixedaccounts.entity.Transaction;
import com.bank.bootcamp.fixedaccounts.entity.TransactionSequences;
import com.bank.bootcamp.fixedaccounts.exception.BankValidationException;
import com.bank.bootcamp.fixedaccounts.repository.AccountRepository;
import com.bank.bootcamp.fixedaccounts.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AccountService {
  
  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;
  private final NextSequenceService nextSequenceService;
  private final Environment env;
  
  private ModelMapper mapper = new ModelMapper();

  public Mono<Account> createAccount(CreateAccountDTO dto) {
    var minimumOpeningAmount = Double.parseDouble(Optional.ofNullable(env.getProperty("account.minimum-opening-amount")).orElse("0"));
    return Mono.just(dto)
        .then(check(dto, acc -> Optional.of(acc).isEmpty(), "Account has not data"))
        .then(check(dto, acc -> ObjectUtils.isEmpty(acc.getCustomerId()), "Customer ID is required"))
        .then(check(dto, acc -> ObjectUtils.isEmpty(acc.getAssignedDayNumberForMovement()), "Assigned day number for movement is required"))
        .then(check(dto, acc -> acc.getAssignedDayNumberForMovement() < 1 && acc.getAssignedDayNumberForMovement() > 28, "Assigned day number for movement must be between 1 and 28"))
        .then(check(dto, acc -> ObjectUtils.isEmpty(acc.getOpeningAmount()), "Opening amount is required"))
        .then(check(dto, acc -> acc.getOpeningAmount() < minimumOpeningAmount, String.format("The minimum opening amount is %s", minimumOpeningAmount)))
        .then(accountRepository.findByCustomerId(dto.getCustomerId())
            .<CreateAccountDTO>handle((record, sink) -> sink.error(new BankValidationException("Customer already has an saving account")))
        )
        .switchIfEmpty(Mono.just(dto))
        .flatMap(accountDTO -> {
          var account = mapper.map(accountDTO, Account.class);
          account.setMonthlyMovementLimit(1); // maximo movimientos mensuales
          return accountRepository.save(account)
              .flatMap(savedAccount -> {
                return nextSequenceService.getNextSequence(TransactionSequences.class.getSimpleName())
                    .map(nextSeq -> {
                      var openingTransaction = new Transaction();
                      openingTransaction.setAccountId(savedAccount.getId());
                      openingTransaction.setAgent("-");
                      openingTransaction.setAmount(accountDTO.getOpeningAmount());
                      openingTransaction.setDescription("Opening account");
                      openingTransaction.setOperationNumber(nextSeq);
                      openingTransaction.setRegisterDate(LocalDateTime.now());
                      return openingTransaction;
                    })
                    .flatMap(tx -> {
                      return transactionRepository.save(tx).map(tt -> savedAccount);
                    });
          });
        });
  }
  
  private <T> Mono<Void> check(T customer, Predicate<T> predicate, String messageForException) {
    return Mono.create(sink -> {
      if (predicate.test(customer)) {
        sink.error(new BankValidationException(messageForException));
        return;
      } else {
        sink.success();
      }
    });
  }

  public Mono<Transaction> createTransaction(CreateTransactionDTO createTransactionDTO) {
    
    return Mono.just(createTransactionDTO)
        .then(check(createTransactionDTO, dto -> Optional.of(dto).isEmpty(), "No data for create transaction"))
        .then(check(createTransactionDTO, dto -> ObjectUtils.isEmpty(dto.getAccountId()), "Account ID is required"))
        .then(check(createTransactionDTO, dto -> ObjectUtils.isEmpty(dto.getAgent()), "Agent is required"))
        .then(check(createTransactionDTO, dto -> ObjectUtils.isEmpty(dto.getAmount()), "Amount is required"))
        .then(check(createTransactionDTO, dto -> ObjectUtils.isEmpty(dto.getDescription()), "Description is required"))
        .then(accountRepository.findById(createTransactionDTO.getAccountId())
            .switchIfEmpty(Mono.error(new BankValidationException("Account not found")))
            .<Account>handle((register, sink) -> {
              var now = LocalDate.now();
              if (now.get(ChronoField.DAY_OF_MONTH) != register.getAssignedDayNumberForMovement()) 
                sink.error(new BankValidationException(String.format("Can only register a movement on the %s of the month", register.getAssignedDayNumberForMovement())));
              else
                sink.next(register);              
            })
            .flatMap(acc -> {
              var yearMonth = YearMonth.from(LocalDateTime.now());
              var currentMonthStart = yearMonth.atDay(1).atStartOfDay();
              var currentMonthEnd = yearMonth.atEndOfMonth().atTime(23, 59, 59);
              return transactionRepository.findByAccountIdAndRegisterDateBetween(acc.getId(), currentMonthStart, currentMonthEnd)
                  .count()
                  .<Long>handle((register, sink) -> {
                    if (register >= acc.getMonthlyMovementLimit()) 
                      sink.error(new BankValidationException(String.format("You can only register a maximum of %s monthly movements", acc.getMonthlyMovementLimit())));
                    else
                      sink.next(register);
                  });
            })
        )
        .flatMap(acc -> {
          return transactionRepository.getBalanceByAccountId(createTransactionDTO.getAccountId()).switchIfEmpty(Mono.just(0d));
        })
        .flatMap(balance -> {
          if (balance + createTransactionDTO.getAmount() < 0)
            return Mono.error(new BankValidationException("Insuficient balance"));
          else {
            return nextSequenceService.getNextSequence(TransactionSequences.class.getSimpleName()).<Transaction>flatMap(nextSeq -> {
                var transaction = mapper.map(createTransactionDTO, Transaction.class);
                transaction.setOperationNumber(nextSeq);
                transaction.setRegisterDate(LocalDateTime.now());
                return transactionRepository.save(transaction);
            });
          }
        });
  }

  public Mono<BalanceDTO> getBalanceByAccountId(String accountId) {
    return Mono.just(accountId)
    .switchIfEmpty(Mono.error(new BankValidationException("Account Id is required")))
    .flatMap(accId -> accountRepository.findById(accId))
    .switchIfEmpty(Mono.error(new BankValidationException("Account not found")))
    .flatMap(account -> {
      var x = transactionRepository.getBalanceByAccountId(account.getId()).switchIfEmpty(Mono.just(0d))
          .flatMap(balance -> {
            var yearMonth = YearMonth.from(LocalDateTime.now());
            var currentMonthStart = yearMonth.atDay(1).atStartOfDay();
            var currentMonthEnd = yearMonth.atEndOfMonth().atTime(23, 59, 59);
            
            return transactionRepository.findByAccountIdAndRegisterDateBetween(accountId, currentMonthStart, currentMonthEnd)
                .count().switchIfEmpty(Mono.just(0L))
                .map(qty -> {
                  var balanceDTO = new BalanceDTO();
                  balanceDTO.setAccountId(account.getId());
                  balanceDTO.setMonthlyMovementLimit(account.getMonthlyMovementLimit());
                  balanceDTO.setMonthlyMovementsAvailable(account.getMonthlyMovementLimit() - qty);
                  //balanceDTO.setAccountNumber(account.getAccountNumber());
                  balanceDTO.setType("Fixed Account");
                  balanceDTO.setAmount(balance);
                  return balanceDTO;
                });
          });
      return x;
    });
  }

  public Flux<BalanceDTO> getBalancesByCustomerId(String customerId) {
    return Mono.just(customerId)
    .switchIfEmpty(Mono.error(new BankValidationException("Customer ID is required")))
    .flatMap(custId -> accountRepository.findByCustomerId(custId)
        .flatMap(account -> getBalanceByAccountId(account.getId())))
    .flux();
  }

  public Flux<Account> getAccountsByCustomer(String customerId) {
    return Mono.just(customerId)
        .switchIfEmpty(Mono.error(new BankValidationException("Customer ID is required")))
        .flatMap(custId -> {
          return accountRepository.findByCustomerId(custId);
        })
        .flux();
  }

  public Flux<Transaction> getTransactionsByAccountIdAndPeriod(String accountId, LocalDate period) {
    return Flux.just(accountId)
        .switchIfEmpty(Flux.error(new BankValidationException("Account Id is required")))
        .map(accId -> {
          if (Optional.ofNullable(period).isEmpty())
            return Flux.error(new BankValidationException("Period is required"));
          else
            return accId;
        }).flatMap(accId -> {
          var yearMonth = YearMonth.from(period);
          var currentMonthStart = yearMonth.atDay(1).atStartOfDay();
          var currentMonthEnd = yearMonth.atEndOfMonth().atTime(23, 59, 59);
          return transactionRepository.findByAccountIdAndRegisterDateBetween(accountId, currentMonthStart, currentMonthEnd);
        });
  }
}

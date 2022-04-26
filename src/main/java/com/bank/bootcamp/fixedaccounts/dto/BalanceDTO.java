package com.bank.bootcamp.fixedaccounts.dto;

import lombok.Data;

@Data
public class BalanceDTO {
  private String accountId;
  private String type;
  private Integer accountNumber;
  private Double amount;
  private Integer monthlyMovementLimit;
  private Long monthlyMovementsAvailable;
}
package com.bank.bootcamp.fixedaccounts.dto;

import lombok.Data;

@Data
public class CreateTransactionDTO {
  
  private String accountId;
  private String agent;
  private String description;
  private Double amount;
  private Double openingAmount;
  private Boolean createByComission = Boolean.FALSE;
}

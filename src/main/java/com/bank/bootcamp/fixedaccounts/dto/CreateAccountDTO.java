package com.bank.bootcamp.fixedaccounts.dto;

import lombok.Data;

@Data
public class CreateAccountDTO {

  private String customerId;
  private Integer assignedDayNumberForMovement;
  private Double openingAmount;
}

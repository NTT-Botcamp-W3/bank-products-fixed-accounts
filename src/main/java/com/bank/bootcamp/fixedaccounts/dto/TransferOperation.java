package com.bank.bootcamp.fixedaccounts.dto;

import lombok.Data;

@Data
public class TransferOperation {

  private String sourceTransactionId;
  private String targetTransactionId;
}

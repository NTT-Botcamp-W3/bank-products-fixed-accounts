package com.bank.bootcamp.fixedaccounts.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Document("Accounts")
@Data
public class Account {

  @Id
  private String id;
  
  private String customerId;
  private Integer monthlyMovementLimit = 1;
  private Integer assignedDayNumberForMovement;
}

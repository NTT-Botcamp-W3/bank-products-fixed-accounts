package com.bank.bootcamp.fixedaccounts.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class AverageDTO {

  private LocalDate date;
  private Double average;
}

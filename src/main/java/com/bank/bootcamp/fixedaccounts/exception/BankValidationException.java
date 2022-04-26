package com.bank.bootcamp.fixedaccounts.exception;

public class BankValidationException extends Exception {

  private static final long serialVersionUID = 1L;

  public BankValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  public BankValidationException(String message) {
    super(message);
  }

}

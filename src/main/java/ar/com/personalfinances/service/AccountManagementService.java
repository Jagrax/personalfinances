package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.util.CommonResult;

public interface AccountManagementService {

    CommonResult syncCreditCardAccountMovements(Account creditCardAccount);
}
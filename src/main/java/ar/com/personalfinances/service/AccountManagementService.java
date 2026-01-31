package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.util.CommonResult;

public interface AccountManagementService {

    long GALICIA_CURRENCY_ARS_ID = 1;

    CommonResult syncCreditCardAccountMovements(Account creditCardAccount);

    CommonResult syncAccountMovements(Account account, String aspNetSessionId);
}
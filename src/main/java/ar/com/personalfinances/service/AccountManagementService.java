package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.Account;
import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.util.CommonResult;

public interface AccountManagementService {

    long GALICIA_CURRENCY_ARS_ID = 1;

    CommonResult syncCreditCardAccountMovements(Account creditCardAccount, String cookies);

    CommonResult learnFromBankMovements(Account account, String cookies);

    CommonResult syncAccountMovements(Account account, String cookies);

    CommonResult syncUserAccounts(User user, String cookies);
}

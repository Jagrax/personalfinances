package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.User;
import ar.com.personalfinances.util.CommonResult;

public interface GaliciaSyncService {

    CommonResult discoverAccounts(User user, String cookies);

    CommonResult importSelectedAccounts(User user, String accountsJson);

    CommonResult syncAllAccounts(User user, String cookies);
}

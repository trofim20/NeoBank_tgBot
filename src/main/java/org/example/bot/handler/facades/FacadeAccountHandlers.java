package org.example.bot.handler.facades;

import lombok.RequiredArgsConstructor;
import org.example.bot.handler.account.AccountsHandler;
import org.example.bot.handler.account.CloseAccountHandler;
import org.example.bot.handler.account.NewAccountHandler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FacadeAccountHandlers {
    private final AccountsHandler accountsHandler;
    private final CloseAccountHandler closeAccountHandler;
    private final NewAccountHandler newAccountHandler;

    public AccountsHandler getAccountsHandler() {
        return accountsHandler;
    }

    public CloseAccountHandler getCloseAccountHandler() {
        return closeAccountHandler;
    }

    public NewAccountHandler getNewAccountHandler() {
        return newAccountHandler;
    }
}

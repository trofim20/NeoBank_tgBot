package org.example.bot.handler.facades;

import lombok.RequiredArgsConstructor;
import org.example.bot.handler.deposit.CloseDepositHandler;
import org.example.bot.handler.deposit.DepositsHandler;
import org.example.bot.handler.deposit.NewDepositHandler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FacadeDepositHandlers {
    private final DepositsHandler depositsHandler;
    private final NewDepositHandler newDepositHandler;
    private final CloseDepositHandler closeDepositHandler;

    public DepositsHandler getDepositsHandler() {
        return depositsHandler;
    }

    public NewDepositHandler getNewDepositHandler() {
        return newDepositHandler;
    }

    public CloseDepositHandler getCloseDepositHandler() {
        return closeDepositHandler;
    }
}

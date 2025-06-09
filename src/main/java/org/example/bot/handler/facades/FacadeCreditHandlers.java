package org.example.bot.handler.facades;

import lombok.RequiredArgsConstructor;
import org.example.bot.handler.credit.CloseCreditHandler;
import org.example.bot.handler.credit.CreditsHandler;
import org.example.bot.handler.credit.NewCreditHandler;
import org.example.bot.handler.credit.PaymentPlanHandler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FacadeCreditHandlers {
    private final CreditsHandler creditsHandler;
    private final NewCreditHandler newCreditHandler;
    private final CloseCreditHandler closeCreditHandler;
    private final PaymentPlanHandler paymentPlanHandler;

    public CreditsHandler getCreditsHandler() {
        return creditsHandler;
    }

    public NewCreditHandler getNewCreditHandler() {
        return newCreditHandler;
    }

    public CloseCreditHandler getCloseCreditHandler() {
        return closeCreditHandler;
    }

    public PaymentPlanHandler getPaymentPlanHandler() {
        return paymentPlanHandler;
    }
}

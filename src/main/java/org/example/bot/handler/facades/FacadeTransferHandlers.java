package org.example.bot.handler.facades;

import lombok.RequiredArgsConstructor;
import org.example.bot.handler.transfer.TransferHandler;
import org.example.bot.handler.transfer.TransferHistoryHandler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FacadeTransferHandlers {
    private final TransferHandler transferHandler;
    private final TransferHistoryHandler transferHistoryHandler;

    public TransferHandler getTransferHandler() {
        return transferHandler;
    }

    public TransferHistoryHandler getTransferHistoryHandler() {
        return transferHistoryHandler;
    }
}

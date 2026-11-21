package com.nhantr.chat_service.websocket;

import java.time.Instant;

public record ChatReceiptEvent(
        Long conversationId,
        Long participantId,
        String receiptType,
        Instant timestamp,
        int updatedCount
) {}


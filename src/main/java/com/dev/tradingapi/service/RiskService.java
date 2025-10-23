package com.dev.tradingapi.service;

import com.dev.tradingapi.model.Quote;
import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.RiskException;
public class RiskService {

    public void validate(CreateOrderRequest req, Quote mark) {
        if (req.getQty() > mark.getVolume()) {
            throw new RiskException("Quantity is greater than the market quantity");
        }
    }
}
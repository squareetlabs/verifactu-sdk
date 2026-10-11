package com.squareetlabs.verifactu.services;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Formato de importes que acepta la AEAT: punto decimal, dos decimales y nunca "-0.00". */
public class AeatClientAmountFormatTest {

    @Test
    public void alwaysTwoDecimalsDotAndNoNegativeZero() {
        assertEquals("0.00", AeatClient.formatAmount(0));
        assertEquals("0.00", AeatClient.formatAmount(-0.0));
        assertEquals("0.00", AeatClient.formatAmount(-0.004));
        assertEquals("-0.01", AeatClient.formatAmount(-0.005));
        assertEquals("1.01", AeatClient.formatAmount(1.005));
        assertEquals("21.00", AeatClient.formatAmount(21));
        assertEquals("10000000.00", AeatClient.formatAmount(1e7));
        assertEquals("123456789.99", AeatClient.formatAmount(123456789.99));
    }
}

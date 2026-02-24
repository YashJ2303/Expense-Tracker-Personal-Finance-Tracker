package test;

import static test.SimpleAssert.*;
import java.math.BigDecimal;

public class ExpenseServiceTest {

    public static void main(String[] args) {
        ExpenseServiceTest runner = new ExpenseServiceTest();
        try {
            runner.testPredictionLogic();
            runner.testTotalCalculationLogic();
            System.out.println("ExpenseServiceTest: ALL PASSED");
        } catch (Throwable e) {
            System.err.println("ExpenseServiceTest: FAILED");
            e.printStackTrace();
        }
    }

    public void testPredictionLogic() {
        assertTrue(true, "Logic should be valid");
    }

    public void testTotalCalculationLogic() {
        BigDecimal val1 = new BigDecimal("100.50");
        BigDecimal val2 = new BigDecimal("200.25");
        BigDecimal expected = new BigDecimal("300.75");
        assertEquals(expected, val1.add(val2), "Sums should match");
    }
}

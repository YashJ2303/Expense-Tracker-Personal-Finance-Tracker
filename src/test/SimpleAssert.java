package test;

public class SimpleAssert {
    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion Failed: " + message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError("Assertion Failed: " + message);
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null)
            return;
        if (expected != null && expected.equals(actual))
            return;
        throw new AssertionError(
                "Assertion Failed: " + message + " (Expected: " + expected + ", Actual: " + actual + ")");
    }

    public static void assertNotEquals(Object val1, Object val2, String message) {
        if (val1 == null && val2 == null || val1 != null && val1.equals(val2)) {
            throw new AssertionError("Assertion Failed: " + message + " (Values should not be equal: " + val1 + ")");
        }
    }

    public static void assertNotNull(Object val, String message) {
        if (val == null) {
            throw new AssertionError("Assertion Failed: " + message + " (Value is null)");
        }
    }
}

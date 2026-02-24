package test;

import security.SecurityUtils;

import static test.SimpleAssert.*;

public class SecurityUtilsTest {

    public static void main(String[] args) {
        SecurityUtilsTest runner = new SecurityUtilsTest();
        try {
            runner.testGenerateSalt();
            runner.testHashPassword();
            runner.testVerifyPassword();
            System.out.println("SecurityUtilsTest: ALL PASSED");
        } catch (Throwable e) {
            System.err.println("SecurityUtilsTest: FAILED");
            e.printStackTrace();
        }
    }

    public void testGenerateSalt() {
        String salt1 = SecurityUtils.generateSalt();
        String salt2 = SecurityUtils.generateSalt();
        assertNotNull(salt1, "Salt 1 should not be null");
        assertNotNull(salt2, "Salt 2 should not be null");
        assertNotEquals(salt1, salt2, "Salts should be unique");
    }

    public void testHashPassword() {
        String password = "testPassword";
        String salt = SecurityUtils.generateSalt();
        String hash1 = SecurityUtils.hashPassword(password, salt);
        String hash2 = SecurityUtils.hashPassword(password, salt);

        assertEquals(hash1, hash2, "Same password and salt should produce same hash");
        assertNotEquals(password, hash1, "Hash should not be same as password");
    }

    public void testVerifyPassword() {
        String password = "securePassword123";
        String salt = SecurityUtils.generateSalt();
        String hash = SecurityUtils.hashPassword(password, salt);

        assertTrue(SecurityUtils.verifyPassword(password, salt, hash), "Password should be verified");
        assertFalse(SecurityUtils.verifyPassword("wrongPassword", salt, hash), "Wrong password should fail");
    }
}

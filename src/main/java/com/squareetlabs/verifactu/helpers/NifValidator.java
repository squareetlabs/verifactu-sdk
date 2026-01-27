package com.squareetlabs.verifactu.helpers;

import java.util.regex.Pattern;

/**
 * Validator for Spanish NIF (Número de Identificación Fiscal) and CIF (Código
 * de Identificación Fiscal).
 * Validates format and check digit according to Spanish tax authority
 * specifications.
 */
public class NifValidator {

    private static final Pattern NIF_PATTERN = Pattern.compile("^[0-9]{8}[A-Z]$");
    private static final Pattern NIE_PATTERN = Pattern.compile("^[XYZ][0-9]{7}[A-Z]$");
    private static final Pattern CIF_PATTERN = Pattern.compile("^[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]$");

    private static final String NIF_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final String CIF_LETTERS = "JABCDEFGHI";

    /**
     * Validates a Spanish NIF, NIE, or CIF.
     * 
     * @param id The identification number to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }

        id = id.toUpperCase().trim();

        if (NIF_PATTERN.matcher(id).matches()) {
            return isValidNif(id);
        } else if (NIE_PATTERN.matcher(id).matches()) {
            return isValidNie(id);
        } else if (CIF_PATTERN.matcher(id).matches()) {
            return isValidCif(id);
        }

        return false;
    }

    /**
     * Validates a NIF (Número de Identificación Fiscal).
     * Format: 8 digits + 1 letter
     */
    private static boolean isValidNif(String nif) {
        try {
            int number = Integer.parseInt(nif.substring(0, 8));
            char letter = nif.charAt(8);
            char expectedLetter = NIF_LETTERS.charAt(number % 23);
            return letter == expectedLetter;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validates a NIE (Número de Identidad de Extranjero).
     * Format: X/Y/Z + 7 digits + 1 letter
     */
    private static boolean isValidNie(String nie) {
        try {
            // Replace first letter with corresponding number
            char firstChar = nie.charAt(0);
            int prefix = firstChar == 'X' ? 0 : (firstChar == 'Y' ? 1 : 2);

            String numberPart = prefix + nie.substring(1, 8);
            int number = Integer.parseInt(numberPart);
            char letter = nie.charAt(8);
            char expectedLetter = NIF_LETTERS.charAt(number % 23);
            return letter == expectedLetter;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validates a CIF (Código de Identificación Fiscal).
     * Format: 1 letter + 7 digits + 1 digit or letter
     */
    private static boolean isValidCif(String cif) {
        try {
            char orgType = cif.charAt(0);
            String digits = cif.substring(1, 8);
            char checkChar = cif.charAt(8);

            // Calculate check digit
            int sum = 0;
            for (int i = 0; i < 7; i++) {
                int digit = Character.getNumericValue(digits.charAt(i));
                if (i % 2 == 0) {
                    // Even positions (0, 2, 4, 6): double and sum digits
                    int doubled = digit * 2;
                    sum += (doubled / 10) + (doubled % 10);
                } else {
                    // Odd positions (1, 3, 5): add as is
                    sum += digit;
                }
            }

            int checkDigit = (10 - (sum % 10)) % 10;

            // Some organization types use letter, others use digit
            if ("NPQRSW".indexOf(orgType) >= 0) {
                // Must be a letter
                return checkChar == CIF_LETTERS.charAt(checkDigit);
            } else if ("ABCDEFGHJUV".indexOf(orgType) >= 0) {
                // Can be either letter or digit
                return checkChar == CIF_LETTERS.charAt(checkDigit) ||
                        checkChar == Character.forDigit(checkDigit, 10);
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the type of identification number.
     * 
     * @param id The identification number
     * @return "NIF", "NIE", "CIF", or "UNKNOWN"
     */
    public static String getIdType(String id) {
        if (id == null || id.isEmpty()) {
            return "UNKNOWN";
        }

        id = id.toUpperCase().trim();

        if (NIF_PATTERN.matcher(id).matches()) {
            return "NIF";
        } else if (NIE_PATTERN.matcher(id).matches()) {
            return "NIE";
        } else if (CIF_PATTERN.matcher(id).matches()) {
            return "CIF";
        }

        return "UNKNOWN";
    }
}

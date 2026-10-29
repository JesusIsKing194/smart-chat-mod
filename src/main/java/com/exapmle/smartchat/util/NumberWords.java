package com.example.smartchat.util;

import java.util.HashMap;
import java.util.Map;

/** Turns number words, digits, roman numerals and "x5" style tokens into numbers. */
public final class NumberWords {

    private static final Map<String, Integer> WORDS = new HashMap<>();

    static {
        String[] small = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
                "nineteen", "twenty"};
        for (int i = 0; i < small.length; i++) WORDS.put(small[i], i);
        WORDS.put("thirty", 30);
        WORDS.put("forty", 40);
        WORDS.put("fifty", 50);
        WORDS.put("sixty", 60);
        WORDS.put("hundred", 100);
    }

    private NumberWords() {}

    /** Digits (up to 4) or a number word. Returns null when the token is not a number. */
    public static Integer parse(String token) {
        if (token == null || token.isEmpty()) return null;
        char c = token.charAt(0);
        if (c >= '0' && c <= '9') {
            if (token.length() > 4) return null;
            for (int i = 0; i < token.length(); i++) {
                char d = token.charAt(i);
                if (d < '0' || d > '9') return null;
            }
            return Integer.parseInt(token);
        }
        return WORDS.get(token);
    }

    /** Roman numerals II to X (a lone "i" is never treated as a number because it is the word "I"). */
    public static Integer roman(String token) {
        if (token == null) return null;
        switch (token) {
            case "ii": return 2;
            case "iii": return 3;
            case "iv": return 4;
            case "v": return 5;
            case "vi": return 6;
            case "vii": return 7;
            case "viii": return 8;
            case "ix": return 9;
            case "x": return 10;
            default: return null;
        }
    }

    /** Handles "x5" and "5x". */
    public static Integer times(String token) {
        if (token == null) return null;
        if (token.length() >= 2 && token.length() <= 4) {
            if (token.charAt(0) == 'x') {
                Integer n = parse(token.substring(1));
                if (n != null && token.substring(1).chars().allMatch(Character::isDigit)) return n;
            }
            if (token.charAt(token.length() - 1) == 'x') {
                String num = token.substring(0, token.length() - 1);
                if (num.chars().allMatch(Character::isDigit)) return parse(num);
            }
        }
        return null;
    }
}

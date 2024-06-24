package com.redes;

public enum TerminalColors {
    //Color end string, color reset
    RESET("\033[0m"),

    BLACK("\033[0;30m"),    // BLACK
    RED("\033[0;31m"),      // RED
    GREEN("\033[0;32m"),    // GREEN
    YELLOW("\033[0;33m"),   // YELLOW
    BLUE("\033[0;34m"),     // BLUE
    MAGENTA("\033[0;35m"),  // MAGENTA
    CYAN("\033[0;36m"),     // CYAN
    WHITE("\033[0;37m"),
    ;    // WHITE



    private final String code;

    TerminalColors(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return code;
    }

}

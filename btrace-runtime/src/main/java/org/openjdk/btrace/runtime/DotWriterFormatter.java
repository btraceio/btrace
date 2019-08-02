package org.openjdk.btrace.runtime;

class DotWriterFormatter {
    // Maximum number of string characters displayed.
    private int stringLimit = 32;

    DotWriterFormatter() {
    }

    // Set maximum number of string characters displayed.
    void stringLimit(int stringLimit) {
        this.stringLimit = stringLimit;
    }

    // Formats a string value, truncating if needed.
    String formatString(String string, String quote) {
        boolean isLong = string.length() > stringLimit;
        if (isLong) string = string.substring(0, stringLimit - 1);
        string = quote + string + quote;
        if (isLong) string += "...";
        return string;
    }
}

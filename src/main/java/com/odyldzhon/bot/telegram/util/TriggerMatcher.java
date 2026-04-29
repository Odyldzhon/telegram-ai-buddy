package com.odyldzhon.bot.telegram.util;

import lombok.experimental.UtilityClass;

import java.util.Locale;

@UtilityClass
public class TriggerMatcher {

    public boolean contains(String text, String triggerName) {
        if (text == null || triggerName == null || triggerName.isBlank()) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT)
                .contains(triggerName.toLowerCase(Locale.ROOT));
    }
}

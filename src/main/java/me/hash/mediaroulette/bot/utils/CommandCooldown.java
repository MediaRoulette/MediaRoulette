package me.hash.mediaroulette.bot.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// ============================================================================
// ANNOTATION DEFINITION
// ============================================================================
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface CommandCooldown {
    int value(); // cooldown in seconds
    String[] commands() default {}; // specific command names (optional)
}

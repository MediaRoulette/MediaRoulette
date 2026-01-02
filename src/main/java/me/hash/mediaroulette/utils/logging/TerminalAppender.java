package me.hash.mediaroulette.utils.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import me.hash.mediaroulette.utils.terminal.TerminalInterface;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Custom console appender that integrates with JLine terminal.
 * This appender clears the current input line before printing log messages
 * and signals the terminal to redraw the prompt afterward.
 */
public class TerminalAppender extends ConsoleAppender<ILoggingEvent> {
    
    // ANSI escape sequences
    private static final String CLEAR_LINE = "\r\033[K";
    private static final String CURSOR_UP = "\033[A";
    
    private boolean useTerminalIntegration = true;
    
    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) {
            return;
        }
        
        try {
            TerminalInterface terminal = TerminalInterface.getInstance();
            
            if (useTerminalIntegration && terminal != null && terminal.isRunning()) {
                // Get the encoded log message
                byte[] encoded = getEncoder().encode(event);
                String logMessage = new String(encoded, StandardCharsets.UTF_8);
                
                // Use terminal's method to print with proper line handling
                terminal.printAboveLine(logMessage);
            } else {
                // Fall back to standard console output
                super.append(event);
            }
        } catch (Exception e) {
            // Fall back to standard console output on any error
            super.append(event);
        }
    }
    
    @Override
    public void start() {
        super.start();
        addInfo("Terminal-aware console appender started");
    }
    
    public void setUseTerminalIntegration(boolean useTerminalIntegration) {
        this.useTerminalIntegration = useTerminalIntegration;
    }
    
    public boolean isUseTerminalIntegration() {
        return useTerminalIntegration;
    }
}

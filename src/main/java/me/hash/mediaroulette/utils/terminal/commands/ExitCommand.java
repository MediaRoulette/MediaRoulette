package me.hash.mediaroulette.utils.terminal.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.util.List;

public class ExitCommand extends Command {
    private static final Logger logger = LoggerFactory.getLogger(ExitCommand.class);

    public ExitCommand() {
        super("exit", "Exit the application", "exit", List.of("quit", "q"));
    }

    @Override
    public CommandResult execute(String[] args) {
        logger.info("Shutting down...");
        // Use the proper shutdown method instead of System.exit(0)
        me.hash.mediaroulette.Main.shutdown();
        return CommandResult.success("Goodbye!");
    }
}

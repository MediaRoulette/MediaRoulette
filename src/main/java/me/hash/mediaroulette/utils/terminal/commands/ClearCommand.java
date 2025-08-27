package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;
import java.util.List;

public class ClearCommand extends Command {
    public ClearCommand() {
        super("clear", "Clear the terminal screen", "clear", List.of("cls"));
    }

    @Override
    public CommandResult execute(String[] args) {
        System.out.print("\033[2J\033[H"); // Keep as console control
        System.out.flush();
        return new CommandResult(true, "");
    }
}
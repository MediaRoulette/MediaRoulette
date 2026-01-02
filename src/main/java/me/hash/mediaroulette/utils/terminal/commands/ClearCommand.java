package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;
import me.hash.mediaroulette.utils.terminal.TerminalInterface;

import java.util.List;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

public class ClearCommand extends Command {
    public ClearCommand() {
        super("clear", "Clear the terminal screen", "clear", List.of("cls"));
    }

    @Override
    public CommandResult execute(String[] args) {
        TerminalInterface terminal = TerminalInterface.getInstance();
        
        if (terminal != null && terminal.getTerminal() != null) {
            try {
                // Use JLine's terminal to clear
                terminal.getTerminal().puts(org.jline.utils.InfoCmp.Capability.clear_screen);
                terminal.getTerminal().flush();
            } catch (Exception e) {
                // Fallback to ANSI escape codes
                System.out.print("\033[2J\033[H");
                System.out.flush();
            }
        } else {
            // Fallback to ANSI escape codes
            System.out.print("\033[2J\033[H");
            System.out.flush();
        }
        
        return new CommandResult(true, "");
    }
    
    @Override
    public String getDetailedHelp() {
        StringBuilder help = new StringBuilder();
        help.append(header("Command: ")).append(command("clear")).append("\n");
        help.append("Clears the terminal screen.\n\n");
        
        help.append(header("Usage:")).append("\n");
        help.append("  ").append(cyan("clear")).append("\n\n");
        
        help.append(header("Aliases:")).append("\n");
        help.append("  cls\n");
        
        return help.toString();
    }
}

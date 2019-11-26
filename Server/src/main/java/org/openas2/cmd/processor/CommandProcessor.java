package org.openas2.cmd.processor;


import org.openas2.cmd.Command;
import org.openas2.cmd.CommandRegistry;

import java.util.List;


public interface CommandProcessor {
    List<Command> getCommands();

    void addCommands(CommandRegistry reg);

    void terminate() throws Exception;

    void processCommand() throws Exception;
}

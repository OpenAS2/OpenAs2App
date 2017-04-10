package org.openas2.cmd.processor;


import java.util.List;

import org.openas2.cmd.Command;
import org.openas2.cmd.CommandRegistry;


public interface CommandProcessor {
    List<Command> getCommands();

    void addCommands(CommandRegistry reg);

    void terminate() throws Exception;

    void processCommand() throws Exception;
}
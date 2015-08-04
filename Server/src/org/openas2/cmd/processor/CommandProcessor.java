package org.openas2.cmd.processor;


import java.util.List;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.Command;
import org.openas2.cmd.CommandRegistry;



public interface CommandProcessor {
    public List<Command> getCommands();

    public boolean isTerminated();

    public void addCommands(CommandRegistry reg);

    public void deInit() throws OpenAS2Exception;

    public void init() throws OpenAS2Exception;

    public void terminate();
    
    public void processCommand()  throws OpenAS2Exception;
}
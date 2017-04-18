package org.openas2.cmd;

import java.util.LinkedList;
import java.util.List;

import org.openas2.BaseComponent;

public class BaseCommandRegistry extends BaseComponent implements CommandRegistry {
    private List<Command> commands = new LinkedList<Command>();

    public List<Command> getCommands()
    {
        return commands;
    }

    public void setCommands(List<Command> commands)
    {
        this.commands = commands;
    }

}

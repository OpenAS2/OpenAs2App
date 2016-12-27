package org.openas2.cmd;

import java.util.ArrayList;
import java.util.List;

import org.openas2.BaseComponent;

public class BaseCommandRegistry extends BaseComponent implements CommandRegistry {
	private List<Command> commands;
	    
	public List<Command> getCommands() {
		if (commands == null) {
			commands = new ArrayList<Command>();
		}
		return commands;
	}

	public void setCommands(List<Command> commands) {
		this.commands = commands;
	}
	
}

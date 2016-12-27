package org.openas2.cmd.processor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cmd.Command;
import org.openas2.cmd.CommandRegistry;


public abstract class BaseCommandProcessor extends Thread implements CommandProcessor, Component {

	public Map<String, String> getParameters() {
		// TODO Auto-generated method stub
		return null;
	}

	public Session getSession() {
		// TODO Auto-generated method stub
		return null;
	}

	public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
		// TODO Auto-generated method stub
		
	}

	private List<Command> commands;
    private boolean terminated;

    public BaseCommandProcessor() {
        super();
        terminated = false;
    }

    public void setCommands(List<Command> list) {
        commands = list;
    }

    public List<Command> getCommands() {
        if (commands == null) {
            commands = new ArrayList<Command>();
        }

        return commands;
    }
	
	public Command getCommand(String name) {
		Command currentCmd;
		Iterator<Command> commandIt = getCommands().iterator();
		while (commandIt.hasNext()) {
			currentCmd = commandIt.next();
			if (currentCmd.getName().equals(name)) {
				return currentCmd;
			}
		}
		return null;
	}
	
    public boolean isTerminated() {
        return terminated;
    }

    public void processCommand() throws OpenAS2Exception {
    	throw new OpenAS2Exception("super class method call, not initialized correctly");
    }
    
    public void addCommands(CommandRegistry reg) {
        ;

        List<Command> regCmds = reg.getCommands();

        if (regCmds.size() > 0) {
            getCommands().addAll(regCmds);
        }
    }

    public void terminate() {
        terminated = true;
    }
}

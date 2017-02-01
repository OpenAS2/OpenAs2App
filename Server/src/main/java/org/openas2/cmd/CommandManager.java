package org.openas2.cmd;

import java.util.ArrayList;
import java.util.List;

import org.openas2.cmd.processor.BaseCommandProcessor;

/**
 * command calls the registered command processors
 * 
 * @author joseph mcverry
 *
 */
public class CommandManager {
	private static CommandManager defaultManager;
	private List<BaseCommandProcessor> processors;

	public static CommandManager getCmdManager() {
		if (defaultManager == null) {
			defaultManager = new CommandManager();
		}

		return defaultManager;
	}

	public void setProcessors(List<BaseCommandProcessor> listeners) {
		this.processors = listeners;
	}

	public List<BaseCommandProcessor> getProcessors() {
		if (processors == null) {
			processors = new ArrayList<BaseCommandProcessor>();
		}

		return processors;
	}

	public void addProcessor(BaseCommandProcessor processor) {
		List<BaseCommandProcessor> processors = getProcessors();
		processors.add(processor);
	}

}

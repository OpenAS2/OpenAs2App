package org.openas2.cmd;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.processor.BaseCommandProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * command calls the registered command processors
 *
 * @author joseph mcverry
 */
public class CommandManager {

    private List<BaseCommandProcessor> processors = new ArrayList<BaseCommandProcessor>();

    public void addProcessor(BaseCommandProcessor processor) {
        processors.add(processor);
    }

    public void registerCommands(CommandRegistry reg) throws OpenAS2Exception {
        for (BaseCommandProcessor processor : processors) {
            processor.addCommands(reg);
        }
    }
}

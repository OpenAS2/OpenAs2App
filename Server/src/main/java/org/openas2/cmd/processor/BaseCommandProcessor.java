package org.openas2.cmd.processor;

import org.apache.commons.lang3.ClassUtils;
import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cmd.Command;
import org.openas2.cmd.CommandRegistry;
import org.openas2.schedule.HasSchedule;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;


public abstract class BaseCommandProcessor implements CommandProcessor, Component, HasSchedule {

    private static final Void VOID = null;
    private List<Command> commands = new ArrayList<Command>();
    private Session session;
    private Map<String, String> parameters;
    private boolean running = true;


    public List<Command> getCommands() {
        return commands;
    }

    @Override
    public String getName() {
        return ClassUtils.getSimpleName(getClass());
    }

    @Override
    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        this.session = session;
        this.parameters = parameters;
    }

    @Override
    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Nullable
    Command getCommand(String name) {
        Command currentCmd;
        for (Command command : getCommands()) {
            currentCmd = command;
            if (currentCmd.getName().equals(name)) {
                return currentCmd;
            }
        }
        return null;
    }

    public abstract void processCommand() throws Exception;

    public void addCommands(CommandRegistry reg) {

        List<Command> regCmds = reg.getCommands();

        if (regCmds.size() > 0) {
            commands.addAll(regCmds);
        }
    }

    public void terminate() throws Exception {
        running = false;
        getSession().stop();
    }

    @Override
    public void destroy() throws Exception {
        running = false;
    }

    @Override
    public void schedule(ScheduledExecutorService executor) throws OpenAS2Exception {
        executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                while (running) {
                    processCommand();
                }
                return VOID;
            }
        });
    }
}

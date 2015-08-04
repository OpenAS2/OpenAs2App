package org.openas2.cmd;

import org.openas2.Component;
import org.openas2.Session;


public interface Command extends Component {
    public void setDescription(String desc);

    public String getDescription();

    public void setName(String name);

    public String getName();

    public Session getSession();

    public void setUsage(String usage);

    public String getUsage();

    public CommandResult execute(Object[] params);
}

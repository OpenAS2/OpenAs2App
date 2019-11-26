package org.openas2.cmd;

import org.openas2.Component;
import org.openas2.Session;


public interface Command extends Component {
    void setDescription(String desc);

    String getDescription();

    void setName(String name);

    String getName();

    Session getSession();

    void setUsage(String usage);

    String getUsage();

    CommandResult execute(Object[] params);
}

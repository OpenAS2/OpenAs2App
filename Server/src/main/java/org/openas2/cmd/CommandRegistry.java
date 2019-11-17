package org.openas2.cmd;

import org.openas2.Component;

import java.util.List;

public interface CommandRegistry extends Component {

    List<Command> getCommands();

}

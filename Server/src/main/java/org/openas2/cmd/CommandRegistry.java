package org.openas2.cmd;

import java.util.List;

import org.openas2.Component;

public interface CommandRegistry extends Component {

	List<Command> getCommands();

}

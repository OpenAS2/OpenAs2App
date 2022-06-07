package org.openas2.app.message;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.BaseCommand;
import org.openas2.cmd.CommandResult;
import org.openas2.message.MessageFactory;


public abstract class AliasedMessagesCommand extends BaseCommand {

    public CommandResult execute(Object[] params) {

        try {
            MessageFactory messageFactory = getSession().getMessageFactory();

            if (messageFactory instanceof MessageFactory) {
                return execute(messageFactory, params);
            }
            return new CommandResult(CommandResult.TYPE_COMMAND_NOT_SUPPORTED, "Not supported by messages store");
        } catch (OpenAS2Exception oae) {
            oae.log();
            return new CommandResult(oae);
        }
    }

    protected abstract CommandResult execute(MessageFactory messageFactory, Object[] params) throws OpenAS2Exception;

}

package org.openas2.app.message;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.message.MessageFactory;
import org.openas2.processor.msgtracking.DbTrackingModule;

import java.util.HashMap;

/**
 * view the detail message entries
 *
 * @author Cristiam Henriquez
 */
public class ViewMessageCommand extends AliasedMessagesCommand {
    public String getDefaultDescription() {
        return "View the detail message.";
    }

    public String getDefaultName() {
        return "view";
    }

    public String getDefaultUsage() {
        return "view <msg_id>";
    }

    protected CommandResult execute(MessageFactory messageFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 1) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }
        synchronized (messageFx) {

            String msg_id = params[0].toString();

            DbTrackingModule db = new DbTrackingModule();
            HashMap<String,String> message = db.showMessage(msg_id);

            if(message.isEmpty()){
                return new CommandResult(CommandResult.TYPE_ERROR, "Unknown message");
            }

            return new CommandResult(CommandResult.TYPE_OK, message);
        }
    }
}

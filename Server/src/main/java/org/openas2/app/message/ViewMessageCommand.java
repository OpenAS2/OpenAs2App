package org.openas2.app.message;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.message.MessageFactory;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.msgtracking.DbTrackingModule;
import org.openas2.processor.msgtracking.TrackingModule;

import java.util.HashMap;
import java.util.List;

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

            List<ProcessorModule> mpl = getSession().getProcessor().getModulesSupportingAction(TrackingModule.DO_TRACK_MSG);
            if (mpl == null || mpl.isEmpty()) {
                CommandResult cmdRes = new CommandResult(CommandResult.TYPE_ERROR);
                cmdRes.getResults().add("No DB tracking module available.");
            }
            // Assume we only load one DB tracking module - not sure it makes sense if more than 1 was loaded
            DbTrackingModule db = (DbTrackingModule) mpl.get(0);
            HashMap<String,String> message = db.showMessage(msg_id);

            if(message.isEmpty()){
                return new CommandResult(CommandResult.TYPE_ERROR, "Unknown message");
            }

            return new CommandResult(CommandResult.TYPE_OK, message);
        }
    }
}

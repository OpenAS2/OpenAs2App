package org.openas2.app.message;

import org.openas2.OpenAS2Exception;

import org.openas2.cmd.CommandResult;

import org.openas2.message.MessageFactory;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.msgtracking.DbTrackingModule;
import org.openas2.processor.msgtracking.TrackingModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * list messages entries
 *
 * @author cristiam henriquez
 */
public class ListMessagesCommand extends AliasedMessagesCommand  {

    public String getDefaultDescription() {
        return "List all messages";
    }


    public String getDefaultName() {
        return "list";
    }

    public String getDefaultUsage() {
        return "list";
    }

    public CommandResult execute(MessageFactory messageFactory, Object[] params) throws OpenAS2Exception {

        synchronized (messageFactory) {
            
            List<ProcessorModule> mpl = getSession().getProcessor().getModulesSupportingAction(TrackingModule.DO_TRACK_MSG);
            if (mpl == null || mpl.isEmpty()) {
                CommandResult cmdRes = new CommandResult(CommandResult.TYPE_ERROR);
                cmdRes.getResults().add("No DB tracking module available.");
            }
            // Assume we only load one DB tracking module - not sure it makes sense if more than 1 was loaded
            DbTrackingModule db = (DbTrackingModule) mpl.get(0);

            ArrayList<HashMap<String,String>> messages = db.listMessages();

            CommandResult cmdRes = new CommandResult(CommandResult.TYPE_OK);

            if(messages.isEmpty()){
                cmdRes.getResults().add("No messages definitions available");
            } else {
                cmdRes.getResults().addAll(messages);
            }

            return cmdRes;
        }
    }
}

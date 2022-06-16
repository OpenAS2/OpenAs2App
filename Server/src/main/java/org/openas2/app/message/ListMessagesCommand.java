package org.openas2.app.message;

import org.openas2.OpenAS2Exception;

import org.openas2.cmd.CommandResult;

import org.openas2.message.MessageFactory;
import org.openas2.processor.msgtracking.DbTrackingModule;

import java.util.ArrayList;
import java.util.HashMap;

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
            
            DbTrackingModule db = new DbTrackingModule();

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

package org.openas2.app.message;

import org.openas2.OpenAS2Exception;

import org.openas2.cmd.CommandResult;

import org.openas2.message.MessageFactory;
import org.openas2.processor.msgtracking.DbTrackingModule;
import org.openas2.processor.msgtracking.TrackingModule;
import org.openas2.processor.ProcessorModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * list messages entries
 *
 * @author cristiam henriquez
 */
public class GetDataForCharts extends AliasedMessagesCommand {

    public String getDefaultDescription() {
        return "View data for charts.";
    }

    public String getDefaultName() {
        return "data_charts";
    }

    public String getDefaultUsage() {
        return "data_charts";
    }

    public CommandResult execute(MessageFactory messageFactory, Object[] params) throws OpenAS2Exception {

        synchronized (messageFactory) {
            HashMap<String, String> map = new HashMap<String, String>();
            for (Object object : params) {
                String name = object.toString().split("=")[0];
                String value = object.toString().split("=")[1];
                map.put(name, value);
            }
            List<ProcessorModule> mpl = getSession().getProcessor().getModulesSupportingAction(TrackingModule.DO_TRACK_MSG);
            if (mpl == null || mpl.isEmpty()) {
                CommandResult cmdRes = new CommandResult(CommandResult.TYPE_ERROR);
                cmdRes.getResults().add("No DB tracking module available.");
            }
            // Assume we only load one DB tracking module - not sure it makes sense if more than 1 was loaded
            DbTrackingModule db = (DbTrackingModule) mpl.get(0);

            ArrayList<HashMap<String, String>> data = db.getDataCharts(map);

            CommandResult cmdRes = new CommandResult(CommandResult.TYPE_OK);

            if (data.isEmpty()) {
                cmdRes.getResults().add("No data definitions available");
            } else {
                cmdRes.getResults().addAll(data);
            }

            return cmdRes;
        }
    }
}

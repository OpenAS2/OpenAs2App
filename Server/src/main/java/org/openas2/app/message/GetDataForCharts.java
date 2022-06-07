package org.openas2.app.message;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.message.MessageFactory;
import org.openas2.processor.msgtracking.DbTrackingModule;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * view data for Charts
 *
 * @author Cristiam Henriquez
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

    protected CommandResult execute(MessageFactory messageFx, Object[] params) throws OpenAS2Exception {
        synchronized (messageFx) {

            DbTrackingModule db = new DbTrackingModule();
            HashMap<String,String> options = new HashMap<String,String>();

            options.put("jdbc_connect_string","jdbc:h2:tcp://localhost:9092/openas2");
            options.put("db_user","sa");
            options.put("db_pwd","OpenAS2");

            db.init(getSession(), options);
            
            ArrayList<HashMap<String,String>> data = db.getDataCharts();

            if(data.isEmpty()){
                return new CommandResult(CommandResult.TYPE_ERROR, "Unknown data");
            }

            return new CommandResult(CommandResult.TYPE_OK, data);
        }
    }
}

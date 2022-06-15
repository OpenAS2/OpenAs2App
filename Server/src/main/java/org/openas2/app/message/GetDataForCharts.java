package org.openas2.app.message;

import org.openas2.OpenAS2Exception;

import org.openas2.cmd.CommandResult;

import org.openas2.message.MessageFactory;
import org.openas2.processor.msgtracking.DbTrackingModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
            DbTrackingModule db = new DbTrackingModule();
            HashMap<String, String> options = new HashMap<String, String>();

            options.put("jdbc_connect_string", "jdbc:h2:tcp://localhost:9092/openas2");
            options.put("db_user", "sa");
            options.put("db_pwd", "OpenAS2");

            db.init(getSession(), options);
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

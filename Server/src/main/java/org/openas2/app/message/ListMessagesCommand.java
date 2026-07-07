package org.openas2.app.message;

import org.openas2.OpenAS2Exception;

import org.openas2.cmd.CommandResult;

import org.openas2.message.MessageFactory;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.msgtracking.DbTrackingModule;
import org.openas2.processor.msgtracking.TrackingModule;
import org.openas2.util.DateUtil;

import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * list messages entries
 *
 * @author cristiam henriquez
 */
public class ListMessagesCommand extends AliasedMessagesCommand  {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public String getDefaultDescription() {
        return "List messages, optionally bounded by from=/to= (yyyy-MM-dd)";
    }


    public String getDefaultName() {
        return "list";
    }

    public String getDefaultUsage() {
        return "list [from=yyyy-MM-dd] [to=yyyy-MM-dd]";
    }

    public CommandResult execute(MessageFactory messageFactory, Object[] params) throws OpenAS2Exception {

        synchronized (messageFactory) {

            List<ProcessorModule> mpl = getSession().getProcessor().getModulesSupportingAction(TrackingModule.DO_TRACK_MSG);
            if (mpl == null || mpl.isEmpty()) {
                CommandResult cmdRes = new CommandResult(CommandResult.TYPE_ERROR);
                cmdRes.getResults().add("No DB tracking module available.");
                return cmdRes;
            }
            // Assume we only load one DB tracking module - not sure it makes sense if more than 1 was loaded
            DbTrackingModule db = (DbTrackingModule) mpl.get(0);

            HashMap<String, String> filters = parseParams(params);

            Timestamp from;
            Timestamp to;
            try {
                from = parseBound(filters.get("from"), " 00:00:00");
                to = parseBound(filters.get("to"), " 23:59:59");
            } catch (ParseException e) {
                return new CommandResult(CommandResult.TYPE_ERROR,
                        "Invalid date - 'from'/'to' must be in yyyy-MM-dd format: " + e.getMessage());
            }

            ArrayList<HashMap<String,String>> messages = db.listMessages(from, to);

            CommandResult cmdRes = new CommandResult(CommandResult.TYPE_OK);

            if(messages.isEmpty()){
                cmdRes.getResults().add("No messages definitions available");
            } else {
                cmdRes.getResults().addAll(messages);
            }

            return cmdRes;
        }
    }

    private HashMap<String, String> parseParams(Object[] params) {
        HashMap<String, String> filters = new HashMap<String, String>();
        for (Object param : params) {
            String s = param.toString();
            int equalsPos = s.indexOf('=');
            if (equalsPos > 0) {
                filters.put(s.substring(0, equalsPos), s.substring(equalsPos + 1));
            }
        }
        return filters;
    }

    /**
     * @param value     - the raw "yyyy-MM-dd" parameter value, or null if the caller didn't supply one
     * @param timeOfDay - " 00:00:00" or " 23:59:59" to make the bound inclusive of the whole day
     * @return the parsed bound, or null if value was null (i.e. unbounded on that side)
     */
    private Timestamp parseBound(String value, String timeOfDay) throws ParseException {
        if (value == null) {
            return null;
        }
        return new Timestamp(DateUtil.parseDate(DATE_FORMAT, value + timeOfDay).getTime());
    }
}

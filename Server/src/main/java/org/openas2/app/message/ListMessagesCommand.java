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
        return "List messages, optionally bounded by from=/to= (yyyy-MM-dd) and fromTime=/toTime= (HH:mm:ss)";
    }


    public String getDefaultName() {
        return "list";
    }

    public String getDefaultUsage() {
        return "list [from=yyyy-MM-dd] [to=yyyy-MM-dd] [fromTime=HH:mm:ss] [toTime=HH:mm:ss]";
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

            DateRange range;
            try {
                range = resolveDateRange(filters);
            } catch (InvalidRangeException | ParseException e) {
                return new CommandResult(CommandResult.TYPE_ERROR, e.getMessage());
            }

            ArrayList<HashMap<String,String>> messages = db.listMessages(range.from, range.to);

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
     * Resolves the from=/to=/fromTime=/toTime= filters into a concrete date range, applying:
     * - fromTime requires from, toTime requires to (a time with no matching date is invalid)
     * - to/toTime together require from as well
     * - from+fromTime with no to at all defaults the upper bound to right now
     * - a resolved from after a resolved to is rejected
     */
    DateRange resolveDateRange(HashMap<String, String> filters) throws InvalidRangeException, ParseException {
        String fromStr = filters.get("from");
        String toStr = filters.get("to");
        String fromTimeStr = filters.get("fromTime");
        String toTimeStr = filters.get("toTime");

        if (fromTimeStr != null && fromStr == null) {
            throw new InvalidRangeException("'fromTime' parameter requires 'from' to also be specified");
        }
        if (toTimeStr != null && toStr == null) {
            throw new InvalidRangeException("'toTime' parameter requires 'to' to also be specified");
        }

        Timestamp from = parseBound(fromStr, fromTimeStr, "00:00:00");
        Timestamp to;
        if (toStr == null && fromStr != null && fromTimeStr != null) {
            // from+fromTime given with no 'to' at all: treat as "from that moment until now"
            to = new Timestamp(System.currentTimeMillis());
        } else {
            to = parseBound(toStr, toTimeStr, "23:59:59");
        }

        if (from != null && to != null && from.after(to)) {
            throw new InvalidRangeException("start time cannot be after the end time");
        }

        return new DateRange(from, to);
    }

    /**
     * @param dateValue        - the raw "yyyy-MM-dd" parameter value, or null if the caller didn't supply one
     * @param timeValue        - the raw "HH:mm:ss" parameter value, or null if the caller didn't supply one
     * @param defaultTimeOfDay - "00:00:00" or "23:59:59", used when timeValue is null
     * @return the parsed bound, or null if dateValue was null (i.e. unbounded on that side)
     */
    private Timestamp parseBound(String dateValue, String timeValue, String defaultTimeOfDay) throws ParseException {
        if (dateValue == null) {
            return null;
        }
        String timeOfDay = timeValue != null ? timeValue : defaultTimeOfDay;
        return new Timestamp(DateUtil.parseDate(DATE_FORMAT, dateValue + " " + timeOfDay).getTime());
    }

    static final class DateRange {
        final Timestamp from;
        final Timestamp to;

        DateRange(Timestamp from, Timestamp to) {
            this.from = from;
            this.to = to;
        }
    }

    static final class InvalidRangeException extends Exception {
        InvalidRangeException(String message) {
            super(message);
        }
    }
}

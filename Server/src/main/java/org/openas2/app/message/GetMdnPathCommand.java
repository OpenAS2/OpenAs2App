package org.openas2.app.message;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.BaseCommand;
import org.openas2.cmd.CommandResult;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.msgtracking.DbTrackingModule;
import org.openas2.processor.msgtracking.TrackingModule;

import java.util.List;

/**
 * Returns the stored MDN file path for a message identified by its payload filename. Backed by the
 * message tracking database, so it does not depend on the (optional) message factory component.
 */
public class GetMdnPathCommand extends BaseCommand {

    public String getDefaultDescription() {
        return "Get the stored MDN file path for a message by its payload filename.";
    }

    public String getDefaultName() {
        return "mdnpath";
    }

    public String getDefaultUsage() {
        return "mdnpath <filename>";
    }

    public CommandResult execute(Object[] params) {
        if (params.length < 1) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }
        try {
            String filename = params[0].toString();

            List<ProcessorModule> mpl = getSession().getProcessor().getModulesSupportingAction(TrackingModule.DO_TRACK_MSG);
            if (mpl == null || mpl.isEmpty()) {
                return new CommandResult(CommandResult.TYPE_ERROR, "No DB tracking module available.");
            }
            // Assume we only load one DB tracking module - not sure it makes sense if more than 1 was loaded
            DbTrackingModule db = (DbTrackingModule) mpl.get(0);

            String mdnPath = db.getMdnFilePath(filename);
            if (mdnPath == null) {
                return new CommandResult(CommandResult.TYPE_ERROR, "No MDN found for filename: " + filename);
            }
            return new CommandResult(CommandResult.TYPE_OK, mdnPath);
        } catch (OpenAS2Exception oae) {
            oae.log();
            return new CommandResult(oae);
        }
    }
}

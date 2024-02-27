package fr.gouv.education.foad.userworkspaces.automation;

import fr.gouv.education.foad.userworkspaces.UserProfileCreationWork;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.Work.State;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;

import net.sf.json.JSONObject;

/**
 * Ask for a userProfile creation, run creation if param doCreate is true.
 * Return the state of the work that creates the item (UNKNOWN, SCHEDULED, RUNNING, COMPLETED).
 *
 * @author Loïc Billon
 */
@Operation(id = AskUserProfileCreation.ID, category = Constants.CAT_SERVICES,
        label = "Ask for a user profile creation", description = "Ask for a User profile creation, return a work state .")
public class AskUserProfileCreation {

    public final static String ID = "Services.AskUserProfileCreation";

    private static final Log uLog = LogFactory.getLog("userprofiles");

    @Param(name = "userId", required = true)
    protected String userId;


    @OperationMethod
    public void run() throws Exception {

        WorkManager workManager = Framework.getLocalService(WorkManager.class);

        Work work = new UserProfileCreationWork(userId);

        workManager.schedule(work);

        uLog.info("Connexion de " + userId + "/ taille file d'attente :" +
                workManager.getQueueSize(UserProfileCreationWork.UWS_QUEUE_ID, null));

    }

}

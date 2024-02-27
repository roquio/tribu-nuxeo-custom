package fr.gouv.education.foad.userworkspaces;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.IdUtils;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.platform.userworkspace.core.service.DefaultUserWorkspaceServiceImpl;

import java.security.Principal;

public class TribuUserWorkspacesServiceImpl extends DefaultUserWorkspaceServiceImpl {


    private static final Log uLog = LogFactory.getLog("userprofiles");


    /**
     * ================ debranchement création automatique =================
     * On vérifie l'existance du UserworkspaceRoot puis du Userworkspace sans provoquer d'erreur et sans lancer de création
     * s'ils n'existent pas.
     *
     */
    protected DocumentModel getCurrentUserPersonalWorkspace(
            Principal principal, String userName, CoreSession userCoreSession,
            DocumentModel context) throws ClientException {
        if (principal == null && StringUtils.isEmpty(userName)) {
            return null;
        }

        String usedUsername;
        if (principal instanceof NuxeoPrincipal) {
            usedUsername = ((NuxeoPrincipal) principal).getActingUser();
        } else {
            usedUsername = userName;
        }

        PathRef rootref = getExistingUserWorkspaceRoot(userCoreSession, usedUsername, context);

        if(rootref != null) {

            PathRef uwref = getExistingUserWorkspace(userCoreSession, rootref, principal, usedUsername);
            if(uwref != null) {

                DocumentModel uw = userCoreSession.getDocument(uwref);
                return uw;
            }
        }

        return null;
    }

    /**
     * Surcharge : Pas de création de UserworkspaceRoot en synchrone
     */
    protected PathRef getExistingUserWorkspaceRoot(CoreSession session, String username, DocumentModel context) {
        PathRef rootref = new PathRef(computePathUserWorkspaceRoot(session, username, context));
        if (session.exists(rootref)) {
            return rootref;
        }
        return null;
    }

    /**
     * Surcharge : Pas de création de Userworkspace en synchrone
     */
    protected PathRef getExistingUserWorkspace(CoreSession session, PathRef rootref, Principal principal, String username) {
        String workspacename = getUserWorkspaceNameForUser(username);
        PathRef uwref = resolveUserWorkspace(session, rootref, username, workspacename, maxsize);
        if (session.exists(uwref)) {
            return uwref;
        }
        PathRef uwcompatref = resolveUserWorkspace(session, rootref, username, IdUtils.generateId(username, "-", false, 30), 30);
        if (uwcompatref != null && session.exists(uwcompatref)) {
            return uwcompatref;
        }

        return null;
    }


    public void asyncCreateUserWorkspace(CoreSession session, String username) {

        DocumentModel context = session.getRootDocument();

        PathRef rootref = getExistingUserWorkspaceRoot(session, username, context);
        if (rootref == null) {
            PathRef ref = new PathRef(computePathUserWorkspaceRoot(session, username, null));
            DocumentModel userWorkspaceRoot = doCreateUserWorkspacesRoot(session, ref);
            rootref = new PathRef(userWorkspaceRoot.getPathAsString());
        }

        PathRef existingUserWorkspace = getExistingUserWorkspace(session, rootref, session.getPrincipal(), username);
        if(existingUserWorkspace == null) {
            PathRef uwcompatref = resolveUserWorkspace(session, rootref, username, IdUtils.generateId(username, "-", false, 30), 30);
            DocumentModel documentModel = doCreateUserWorkspace(session, uwcompatref, session.getPrincipal(), username);

            uLog.info("Création workspace pour "+username+", chemin:"+documentModel.getPathAsString());

        }
        else {
            uLog.info("Workspace existant pour "+username+", chemin:"+existingUserWorkspace);
        }


    }

}

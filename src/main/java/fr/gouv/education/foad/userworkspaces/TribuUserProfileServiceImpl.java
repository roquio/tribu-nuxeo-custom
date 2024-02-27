package fr.gouv.education.foad.userworkspaces;

import fr.toutatice.ecm.platform.collab.tools.userprofile.TtcUserProfileServiceImpl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;

/**
 * Détection des profils et mise en file de leur création
 *
 * @author Loïc Billon
 */
public class TribuUserProfileServiceImpl extends TtcUserProfileServiceImpl {


	private static final Log uLog = LogFactory.getLog("userprofiles");

    /**
     * Method called by works
     * @param session
     * @param username
     */
    public void asyncCreateUserProfile(CoreSession session, String username) {

        if (controls(session, username)) {

            DocumentModel userProfile = super.getUserProfileDocument(username, session);

            if (userProfile == null) {

                UserWorkspaceService service = Framework.getService(UserWorkspaceService.class);
                if (service instanceof TribuUserWorkspacesServiceImpl) {
                    TribuUserWorkspacesServiceImpl tuw = (TribuUserWorkspacesServiceImpl) service;
                    tuw.asyncCreateUserWorkspace(session, username);
                }

                DocumentModel documentModel = getOrCreateUserProfileDocument(username, session);

                uLog.info("Création profil pour " + username + ", chemin:" + documentModel.getPathAsString());

            } else {
                uLog.info("Profil existant pour " + username + ", chemin:" + userProfile.getPathAsString());
            }
        }

    }

    private boolean controls(CoreSession session, String userName) {

        boolean ret = true;

        ElasticSearchService service = Framework.getService(ElasticSearchService.class);

        NxQueryBuilder queryBuilder = new NxQueryBuilder(session);
        queryBuilder.nxql("SELECT * FROM UserProfile WHERE ttc_userprofile:login = '" + userName + "' AND ecm:isProxy = 0  AND ecm:isCheckedInVersion = 0 AND ecm:currentLifeCycleState != 'deleted'");
        DocumentModelList esResponse = service.query(queryBuilder);

        // Profil inconnu dans ES
        if(esResponse.size() == 0) {
            DocumentModelList vcsResponse = session.query("SELECT * FROM UserProfile WHERE ecm:acl/*1/principal = '" + userName + "' AND ecm:isProxy = 0  AND ecm:isCheckedInVersion = 0 AND ecm:currentLifeCycleState != 'deleted'");

            if(vcsResponse.size() == 1) {
                // Problème de réindexation
                uLog.warn("Err1 - Profil non indexé pour "+userName+", chemin:"+vcsResponse.get(0).getPathAsString());
                ret = false;
            }
            else if (vcsResponse.size() > 1) {
                uLog.warn("Err2 - Plusieurs profils non indexés pour "+userName+", chemin:"+vcsResponse.get(0).getPathAsString());
                ret = false;

            }

        }
        // Profil doublon
        else if (esResponse.size() > 1) {

            uLog.warn("Err3 - Plusieurs profils dans l'index pour "+userName+", nb:"+esResponse.size());
            ret = false;

        }

        return ret;

    }

}

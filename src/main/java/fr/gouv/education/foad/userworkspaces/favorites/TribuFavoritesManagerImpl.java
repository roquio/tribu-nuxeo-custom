/**
 * 
 */
package fr.gouv.education.foad.userworkspaces.favorites;

import org.nuxeo.ecm.collections.api.FavoritesConstants;
import org.nuxeo.ecm.collections.core.FavoritesManagerImpl;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;


/**
 * @author david
 */
public class TribuFavoritesManagerImpl extends FavoritesManagerImpl {

    @Override
    public DocumentModel getFavorites(final DocumentModel context, final CoreSession session) throws ClientException {
        final UserWorkspaceService userWorkspaceService = Framework.getLocalService(UserWorkspaceService.class);
        final DocumentModel userWorkspace = userWorkspaceService.getCurrentUserPersonalWorkspace(session, context);
        if (userWorkspace != null) {
            final DocumentRef lookupRef = new PathRef(userWorkspace.getPath().toString(), FavoritesConstants.DEFAULT_FAVORITES_NAME);
            if (session.exists(lookupRef)) {
                return session.getChild(userWorkspace.getRef(), FavoritesConstants.DEFAULT_FAVORITES_NAME);
            } else {
                // does not exist yet, let's create it
                synchronized (this) {
                    TransactionHelper.commitOrRollbackTransaction();
                    TransactionHelper.startTransaction();
                    if (!session.exists(lookupRef)) {
                        boolean succeed = false;
                        try {
                            createFavorites(session, userWorkspace);
                            succeed = true;
                        } finally {
                            if (succeed) {
                                TransactionHelper.commitOrRollbackTransaction();
                                TransactionHelper.startTransaction();
                            }
                        }
                    }
                    return session.getDocument(lookupRef);
                }
            }
        } else {
            // Empty model
            return new DocumentModelImpl(FavoritesConstants.FAVORITES_TYPE);
        }
    }

}

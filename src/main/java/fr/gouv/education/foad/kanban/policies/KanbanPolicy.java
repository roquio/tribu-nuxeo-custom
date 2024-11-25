package fr.gouv.education.foad.kanban.policies;

import fr.toutatice.ecm.platform.core.security.ToutaticeOwnerSecurityPolicy;
import org.apache.commons.lang.ArrayUtils;
import org.nuxeo.ecm.core.api.DocumentException;
import org.nuxeo.ecm.core.api.security.Access;
import org.nuxeo.ecm.core.model.Document;

import java.security.Principal;

/**
 * Personnalisation du droit contribuer.
 * Il permet aux contributeurs de déplacer et modifier des cartes d'un tableau Kanban, au même niveau que les éditeurs.
 *
 * @author Loïc Billon
 */
public class KanbanPolicy extends ToutaticeOwnerSecurityPolicy {


    public static final String KANBAN_BOARD = "KanbanBoard";
    public static final String KANBAN_CARD = "KanbanCard";

    @Override
    protected Access applyPolicy(Document doc, Principal principal, String[] resolvedPermissions) throws DocumentException {

        if(doc.getType().getName().equals(KANBAN_BOARD) || doc.getType().getName().equals(KANBAN_CARD)) {
            String[] simulatedPerms = (String[]) ArrayUtils.addAll(this.getSimulatedDocumentPermissions(), this.getSimulatedParentPermissions());
            String[] allowedPerms = (String[])org.nuxeo.common.utils.ArrayUtils.intersect(new String[][]{simulatedPerms, resolvedPermissions});
            return ArrayUtils.isNotEmpty(allowedPerms) ? Access.GRANT : Access.UNKNOWN;
        }
        else return super.applyPolicy(doc, principal, resolvedPermissions);
    }
}

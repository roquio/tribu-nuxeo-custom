package fr.gouv.education.foad.wf.cleaner;

import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.runtime.api.Framework;


public class TtcWfCleanerListener implements EventListener {

    @Override
    public void handleEvent(Event event) {
//        if (!CLEANUP_WORKFLOW_EVENT_NAME.equals(event.getName())
//                || Framework.isBooleanPropertyTrue(CLEANUP_WORKFLOW_INSTANCES_PROPERTY)) {
//            return;
//        }

//        int batchSize = Integer.parseInt(Framework.getProperty(CLEANUP_WORKFLOW_INSTANCES_BATCH_SIZE_PROPERTY, "1000"));
        //DocumentRoutingService routing = Framework.getLocalService(DocumentRoutingService.class);
        RepositoryManager repositoryManager = Framework.getLocalService(RepositoryManager.class);


        for (String repositoryName : repositoryManager.getRepositoryNames()) {
        	doClean(repositoryName);
        }
        
    }

    private void doClean(String repositoryName) {
        
        TtcWfCleaner cleaner = new TtcWfCleaner();
        int cleanedUpWf = cleaner.doCleanupDoneAndCanceledRouteInstances(repositoryName);
        
//        if (cleanedUpWf == batchSize) {
//            EventContextImpl eCtx = new EventContextImpl();
//            eCtx.setProperty(CLEANUP_WORKFLOW_REPO_NAME_PROPERTY, repositoryName);
//            Event event = eCtx.newEvent(CLEANUP_WORKFLOW_EVENT_NAME);
//            EventProducer eventProducer = Framework.getService(EventProducer.class);
//            eventProducer.fireEvent(event);
//        }
    }

}

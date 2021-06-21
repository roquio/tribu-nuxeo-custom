package fr.gouv.education.foad.wf.cleaner;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.elasticsearch.api.ElasticSearchIndexing;
import org.nuxeo.elasticsearch.api.ElasticSearchService;
import org.nuxeo.elasticsearch.query.NxQueryBuilder;
import org.nuxeo.runtime.api.Framework;

import fr.toutatice.ecm.platform.core.helper.ToutaticeDocumentHelper;


public class TtcWfCleaner {


    @Context
    protected ElasticSearchIndexing esi2;


	private static final Log log = LogFactory.getLog("nuxeo.wf.cleaner");

    
	public int doCleanupDoneAndCanceledRouteInstances(String reprositoryName) {
		UnrestrictedTtcWfCleaner unrestrictedSessionRunner = new UnrestrictedTtcWfCleaner(reprositoryName);
        unrestrictedSessionRunner.runUnrestricted();
        return unrestrictedSessionRunner.getNumberOfCleanedUpWf();
	}
	

    private final class UnrestrictedTtcWfCleaner extends UnrestrictedSessionRunner {
    	
    	public static final String DOCUMENT_ROUTE_DONE_CANCEL =  "SELECT * FROM DocumentRoute WHERE (ecm:currentLifeCycleState = 'done' "
                + " OR ecm:currentLifeCycleState = 'canceled') ORDER BY dc:created";


        private static final String TASK_RELATED = "SELECT * FROM Document WHERE ecm:mixinType = 'Task' AND nt:processId = '%s'"
              + " AND ecm:isVersion = 0";
        
        private static final String OLD_PROC_INSTANCES = "SELECT * FROM Document WHERE ecm:primaryType = 'ProcedureInstance' AND dc:modified <= DATE '%s' ORDER BY dc:created";        

        // compteurs
        protected int i = 0, t = 0, p = 0;
        
        // compteurs pour erreurs de dénormalisation
        protected int iErr = 0, tErr = 0, pErr = 0;

    	private int routesLimit;
    	private int proceduresLimit;

        private UnrestrictedTtcWfCleaner(String repositoryName) {
            super(repositoryName);
            
            routesLimit = Integer.parseInt(Framework.getProperty("foad.nuxeo.wf.clean.routes.limit", "1000"));
        	proceduresLimit = Integer.parseInt(Framework.getProperty("foad.nuxeo.wf.clean.procedures.limit", "1000"));

        }

        @Override
        public void run() {
        	
			ElasticSearchService service = Framework.getService(ElasticSearchService.class);
			
			if (service != null) {
				NxQueryBuilder queryBuilder = new NxQueryBuilder(session);
				queryBuilder.nxql(DOCUMENT_ROUTE_DONE_CANCEL);
				queryBuilder.limit(routesLimit);
				DocumentModelList results = service.query(queryBuilder);

				List<String> routeIds = new ArrayList<>();
				for (DocumentModel result : results) {
					routeIds.add(result.getId());
				}
				
				
	            for (String routeDocId : routeIds) {

	            	queryBuilder.nxql(String.format(TASK_RELATED, routeDocId));
	            	
					DocumentModelList tasks = service.query(queryBuilder);
					
					t = 0;
					for (DocumentModel task : tasks) {

						try {
							session.removeDocument(new IdRef(task.getId()));
							t++;
						}
						catch(ClientException e) {
							log.error("Failed to remove task "+task.getId());
							tErr++;
							
							unrefElasticsearchDoc(task.getId());
							
						}
					}
					if(t > 0) {
						log.info("Remove "+t+" task(s). Unlink "+tErr+ " task(s) on ES ");
					}
					
					try {
						session.removeDocument(new IdRef(routeDocId));
						i++;
						
					}
					catch(ClientException e) {
						log.error("Failed to remove documentroute "+routeDocId);
						iErr++;
						
						unrefElasticsearchDoc(routeDocId);
					}
					

	            }
	            log.info("Remove "+i+" route(s). Unlink "+iErr+ " route(s) on ES ");
	            
	    		Date referenceDate = new Date();
	    		Calendar c = Calendar.getInstance(); 
	    		c.setTime(referenceDate); 
	    		c.add(Calendar.MONTH, -2);
	    		referenceDate = c.getTime();
	    		SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd");
	    		String formattedDate = sdf.format(referenceDate);
	    		
	    		queryBuilder.nxql(String.format(OLD_PROC_INSTANCES, formattedDate));
	    		queryBuilder.limit(proceduresLimit);
            	
				DocumentModelList oldPIs = service.query(queryBuilder);
				List<String> procids = new ArrayList<>();
				for (DocumentModel pi : oldPIs) {
					procids.add(pi.getId());
				}
				
				
				for (String id : procids) {

					try {
						session.removeDocument(new IdRef(id));
						p++;
					}
					catch(ClientException e) {
						log.error("Failed to remove procedure "+id);
						pErr++;
						
						unrefElasticsearchDoc(id);
					}
				}
				log.info("Remove "+p+" procedure(s). Unlink "+pErr+ " procedure(s) on ES ");
	    		
			}
			else {
				throw new ClientException("TtcWfCleaner requires ElasticsearchService");
			}
            
        }

        public int getNumberOfCleanedUpWf() {
            return i;
        }
        
        private void unrefElasticsearchDoc(String rootID) {
        	          
            AutomationService automation = Framework.getService(AutomationService.class);
            
            OperationContext ctx = new OperationContext(this.session);
            
            Map<String, Object> parameters = new HashMap<String, Object>();
            parameters.put("type", "ROOT");
            parameters.put("repositoryName", "default");
            parameters.put("rootID", rootID);
            
            
			try {
				ToutaticeDocumentHelper.callOperation(automation, ctx, "Document.ReIndexES", parameters );
			} catch (Exception e) {
				log.error("Failed to document on ES "+rootID);

			}
                       
            
        }
    }
    
}

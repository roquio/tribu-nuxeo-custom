package fr.gouv.education.foad.wf.cleaner;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
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
import org.nuxeo.ecm.core.api.model.impl.primitives.StringProperty;
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
        
        private static final String OLD_INVIT_INSTANCES = "SELECT * FROM Document WHERE ecm:primaryType = 'ProcedureInstance' AND pi:procedureModelWebId <> 'procedure_quota_exceeding' AND dc:modified <= DATE '%s' ORDER BY dc:created";

		private static final String OLD_QUOTA_INSTANCES = "SELECT * FROM Document WHERE ecm:primaryType = 'ProcedureInstance' AND pi:procedureModelWebId = 'procedure_quota_exceeding' AND dc:created <= DATE '%s' ORDER BY dc:created";


    	public static final String OLD_DOCUMENT_ROUTE_RUNNING =  "SELECT * FROM DocumentRoute WHERE ecm:currentLifeCycleState = 'running' "
                + " AND docri:variablesFacet = 'facet-var_generic-model' AND dc:created <= DATE '%s' ORDER BY dc:created";  
        private static final String PROC_RELATED = "SELECT * FROM Document WHERE ecm:primaryType = 'ProcedureInstance' AND ecm:uuid = '%s'";        
    	
        
    	public static final String OLD_TASK_ENDED =  "SELECT * FROM TaskDoc WHERE ecm:currentLifeCycleState = 'ended' "
                + " AND dc:created <= DATE '%s' ORDER BY dc:created";  
        
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
				removeClosedRoutes(service);
				
				removeOpenRoutes(service);
				
				removeEndedTasks(service);

				removeProcedures(service, OLD_INVIT_INSTANCES, "invitations");

				removeProcedures(service, OLD_QUOTA_INSTANCES, "quota");
			}
			else {
				throw new ClientException("TtcWfCleaner requires ElasticsearchService");
			}
            
        }

		private void removeProcedures(ElasticSearchService service, String query, String info) {
			Date referenceDate = new Date();
			Calendar c = Calendar.getInstance();
			c.setTime(referenceDate);
			c.add(Calendar.MONTH, -2);
			referenceDate = c.getTime();
			SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd");
			String formattedDate = sdf.format(referenceDate);

			NxQueryBuilder queryBuilder2 = new NxQueryBuilder(session);

			queryBuilder2.nxql(String.format(query, formattedDate));
			queryBuilder2.limit(proceduresLimit);

			DocumentModelList oldPIs = service.query(queryBuilder2);
			List<String> procids = new ArrayList<>();
			for (DocumentModel pi : oldPIs) {
				procids.add(pi.getId());
			}


			for (String id : procids) {

				boolean success = removeAndSave(id, "Procedure invitation");

				if(success)
					p++;
				else pErr++;
			}
			log.info("Remove "+p+" procedure(s) "+info+". Unlink "+pErr+ " procedure(s) on ES ");
		}

		private void removeOpenRoutes(ElasticSearchService service) {
			
    		Date referenceDate = new Date();
    		Calendar c = Calendar.getInstance(); 
    		c.setTime(referenceDate); 
    		c.add(Calendar.MONTH, -2);
    		referenceDate = c.getTime();
    		SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd");
    		String formattedDate = sdf.format(referenceDate);
			
			NxQueryBuilder queryBuilder = new NxQueryBuilder(session);
			queryBuilder.nxql(String.format(OLD_DOCUMENT_ROUTE_RUNNING, formattedDate));
			queryBuilder.limit(routesLimit);
			DocumentModelList results = service.query(queryBuilder);

			List<String> routeIds = new ArrayList<>();
			for (DocumentModel result : results) {
				
				Collection<StringProperty> participatingDocuments = (Collection) result.getProperty("docri:participatingDocuments");
				for(StringProperty participatingDocument : participatingDocuments) {
					
					log.info("Check participant "+participatingDocument.getValue());
					
					queryBuilder.nxql(String.format(PROC_RELATED, participatingDocument.getValue()));
					DocumentModelList pis = service.query(queryBuilder);
					
					if(pis.size() == 0) {
						routeIds.add(result.getId());
					}
					else {
						log.info("Skip route "+result.getId()+". A procedure is linked : "+pis.get(0).getId());

					}
				}			
			}
			
			
			for (String routeDocId : routeIds) {

				queryBuilder.nxql(String.format(TASK_RELATED, routeDocId));
				
				DocumentModelList tasks = service.query(queryBuilder);
				
				t = 0;
				for (DocumentModel task : tasks) {

					boolean success = removeAndSave(task.getId(), "Task");

					if(success)
						t++;
					else tErr++;

				}
				if(t > 0) {
					log.info("Remove "+t+" opened task(s). Unlink "+tErr+ " task(s) on ES ");
				}

				boolean success = removeAndSave(routeDocId, "Route");

				if(success)
					i++;
				else iErr++;
				

			}
			log.info("Remove "+i+" opened route(s). Unlink "+iErr+ " route(s) on ES ");
			
		}

		private void removeClosedRoutes(ElasticSearchService service) {
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

					boolean success = removeAndSave(task.getId(), "Task");

					if(success)
						t++;
					else tErr++;

				}
				if(t > 0) {
					log.info("Remove "+t+" closed task(s). Unlink "+tErr+ " task(s) on ES ");
				}

				boolean success = removeAndSave(routeDocId, "Route");

				if(success)
					i++;
				else iErr++;
				

			}
			log.info("Remove "+i+" closed or cancelled route(s). Unlink "+iErr+ " route(s) on ES ");
		}

        public int getNumberOfCleanedUpWf() {
            return i;
        }
        
        

		private void removeEndedTasks(ElasticSearchService service) {
			
    		Date referenceDate = new Date();
    		Calendar c = Calendar.getInstance(); 
    		c.setTime(referenceDate); 
    		c.add(Calendar.MONTH, -2);
    		referenceDate = c.getTime();
    		SimpleDateFormat sdf = new SimpleDateFormat("YYYY-MM-dd");
    		String formattedDate = sdf.format(referenceDate);
			
			NxQueryBuilder queryBuilder = new NxQueryBuilder(session);
			queryBuilder.nxql(String.format(OLD_TASK_ENDED, formattedDate));
			queryBuilder.limit(routesLimit);
			DocumentModelList results = service.query(queryBuilder);

			List<String> taskProcessIds = new ArrayList<>();
			for (DocumentModel result : results) {
				StringProperty targetDocumentId = (StringProperty) result.getProperty("nt:targetDocumentId");
				StringProperty processId = (StringProperty) result.getProperty("nt:processId");


				log.info("Check target document "+targetDocumentId.getValue());
				
				queryBuilder.nxql(String.format(PROC_RELATED, targetDocumentId.getValue()));
				DocumentModelList pis = service.query(queryBuilder);
				
				if(pis.size() == 0) {
					taskProcessIds.add(processId.getValue().toString());
				}
				else {
					log.info("Skip task "+result.getId()+". A procedure is linked : "+pis.get(0).getId());

				}
					
			}
			
			
			for (String taskProcessId : taskProcessIds) {
				
				DocumentModelList tasks = session.query("SELECT * from TaskDoc where nt:processId = '"+taskProcessId+"' AND ecm:isVersion = 0");
				
				for(DocumentModel task : tasks) {

					boolean success = removeAndSave(task.getId(), "Task");

					if(success)
						t++;
					else tErr++;
				}

			}
			
			log.info("Remove "+t+" ended task(s). Unlink "+tErr+ " task(s) on ES ");
			

				
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

		private boolean removeAndSave(String docId, String type) throws ClientException {

			try {
				session.removeDocument(new IdRef(docId));
				session.save();

				log.debug("remove "+type+" "+docId);

				return true;
			}
			catch(ClientException e) {
				log.error("Failed to remove "+type+" "+docId);

				unrefElasticsearchDoc(docId);

				return false;

			}

		}
    }
    
}

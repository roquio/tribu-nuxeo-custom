package fr.gouv.education.foad.userworkspaces;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;
import org.nuxeo.ecm.user.center.profile.UserProfileService;
import org.nuxeo.runtime.api.Framework;

public class UserProfileCreationWork extends AbstractWork {


	public static final String UWS_CATEGORY = "USERPROFILE_CREATION";
	public static final String UWS_QUEUE_ID = "userprofile-creation-queue";


	private static final Log uLog = LogFactory.getLog("userprofiles");
    
	/**
	 * 
	 */
	private static final long serialVersionUID = -9034677557639603196L;
	

	private final String username;
	public UserProfileCreationWork(String username) {
		
		super(UWS_CATEGORY + "/" + username);
		
		this.username = username;
		
	}
	
	@Override
	public void work() throws Exception {
		
		initSession();

		UserProfileService service = Framework.getService(UserProfileService.class);
		
		if(service instanceof TribuUserProfileServiceImpl) {
			TribuUserProfileServiceImpl upService = (TribuUserProfileServiceImpl) service;
			upService.asyncCreateUserProfile(session, username);
		}
		else throw new UnsupportedOperationException("Service TribuUserWorkspacesServiceImpl introuvable");
		
		Date now = new Date();
		long elasped = now.getTime() - getStartTime();
		long scheduled = now.getTime() - getSchedulingTime();
		
		WorkManager workManager = Framework.getLocalService(WorkManager.class);
		int queueSize = workManager.getQueueSize(UserProfileCreationWork.UWS_QUEUE_ID, null) - 1;

		uLog.info("Fin traitement pour " + getTitle() +", temps d'attente "+ scheduled + "ms, temps d'exécution : "+ elasped+ "ms, taille file d'attente : "+queueSize);
	}
	
	@Override
	public String getCategory() {
		
		return UWS_CATEGORY;
	}
	@Override
	public String getTitle() {
		return getId();
	}
	
	

}

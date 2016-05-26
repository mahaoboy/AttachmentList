package com.winagile.attachement.impl;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.springframework.beans.factory.InitializingBean;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.context.GlobalIssueContext;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.fields.screen.FieldScreenManager;
import com.atlassian.jira.issue.fields.screen.FieldScreenTab;
import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.issue.index.IssueIndexManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ImportUtils;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.lifecycle.LifecycleAware;

@ExportAsService({ InitField.class })
@Named("InitField")
public class InitField implements LifecycleAware, InitializingBean {
	@ComponentImport
	private final ApplicationProperties applicationProperties;
	@ComponentImport
	private final EventPublisher eventPublisher;
	private static final String TEST_TEXT_CF = "Test Text CF";
	private CustomFieldManager customFieldManager;
	private FieldScreenManager fieldScreenManager;
	private IssueManager issueManger;
	private ProjectManager projectManager;
	private ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getUser();
	private IssueIndexManager indexManager;

	@Inject
	public InitField(ApplicationProperties applicationProperties, EventPublisher eventPublisher) {
		System.out
				.println("################################ InitField ################################################");
		this.applicationProperties = applicationProperties;
		this.eventPublisher = eventPublisher;
		this.eventPublisher.register(this);
	}

	public String getName() {
		if (null != applicationProperties) {
			return "myComponent:" + applicationProperties.getDisplayName();
		}

		return "myComponent";
	}

	@Override
	public void onStart() {

		System.out.println(
				"################################ afterPropertiesSet: Plugin 4.0 ################################################");
		// Create a list of issue types for which the custom field needs to be
		// available
		List<GenericValue> issueTypes = new ArrayList<GenericValue>();
		issueTypes.add(null);

		// Create a list of project contexts for which the custom field needs to
		// be available
		List<JiraContextNode> contexts = new ArrayList<JiraContextNode>();
		contexts.add(GlobalIssueContext.getInstance());

		// Add custom field
		CustomField cField;
		try {
			cField = customFieldManager.getCustomFieldObjectByName(TEST_TEXT_CF);
			if (cField == null) {
				cField = this.customFieldManager.createCustomField(TEST_TEXT_CF, "A Sample Text Field",
						this.customFieldManager
								.getCustomFieldType("com.atlassian.jira.plugin.system.customfieldtypes:textfield"),
						this.customFieldManager.getCustomFieldSearcher(
								"com.atlassian.jira.plugin.system.customfieldtypes:textsearcher"),
						contexts, issueTypes);
				List<Project> pList = projectManager.getProjectObjects();
				for (Project p : pList) {
					Collection<Long> issueIds = issueManger.getIssueIdsForProject(p.getId());
					for (Long issueId : issueIds) {
						MutableIssue isssue = issueManger.getIssueObject(issueId);
						isssue.setCustomFieldValue(cField, "xxxx");
						Issue resultIssue = issueManger.updateIssue(
								ComponentAccessor.getCrowdService().getUser("admin"), isssue,
								EventDispatchOption.DO_NOT_DISPATCH, false);
						reindexIssue(resultIssue);
					}
				}

				FieldScreen defaultScreen = fieldScreenManager.getFieldScreen(FieldScreen.DEFAULT_SCREEN_ID);
				if (!defaultScreen.containsField(cField.getId())) {
					FieldScreenTab firstTab = defaultScreen.getTab(0);
					firstTab.addFieldScreenLayoutItem(cField.getId());
					fieldScreenManager.updateFieldScreen(defaultScreen);
				}

			}
		} catch (GenericEntityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@EventListener
	public void onIssueEvent(IssueEvent issueEvent) throws RemoteException {
		System.out.println(
				"################################ onIssueEvent: Plugin 4.0 ################################################");
		Issue issue = issueEvent.getIssue();

	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.customFieldManager = ComponentAccessor.getCustomFieldManager();
		this.fieldScreenManager = ComponentAccessor.getFieldScreenManager();
		this.issueManger = ComponentAccessor.getIssueManager();
		this.projectManager = ComponentAccessor.getProjectManager();
		this.indexManager = ComponentAccessor.getIssueIndexManager();
		onStart();

	}

	private void reindexIssue(Issue issue) {

		boolean wasIndexing = ImportUtils.isIndexIssues();
		ImportUtils.setIndexIssues(true);
		try {
			indexManager.reIndex(issue);
		} catch (IndexException e) {
			e.printStackTrace();
		}
		ImportUtils.setIndexIssues(wasIndexing);
	}
}
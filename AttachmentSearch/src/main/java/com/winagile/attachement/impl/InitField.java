package com.winagile.attachement.impl;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Named;

import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.InitializingBean;

import com.atlassian.crowd.embedded.api.CrowdService;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.bc.ServiceOutcome;
import com.atlassian.jira.bc.issue.fields.ColumnService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.LocaleManager;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.AttachmentManager;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.issue.context.GlobalIssueContext;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.manager.FieldConfigManager;
import com.atlassian.jira.issue.fields.layout.column.ColumnLayout;
import com.atlassian.jira.issue.fields.layout.column.ColumnLayoutItem;
import com.atlassian.jira.issue.fields.layout.column.ColumnLayoutManager;
import com.atlassian.jira.issue.fields.layout.field.EditableFieldLayout;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.issue.fields.screen.FieldScreenManager;
import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.issue.index.IssueIndexManager;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.user.ApplicationUsers;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.web.action.admin.translation.TranslationManager;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import com.google.common.collect.Lists;

@ExportAsService({ InitField.class })
@Named("InitField")
public class InitField implements InitializingBean {
    @ComponentImport
    private final ApplicationProperties applicationProperties;
    @ComponentImport
    private final EventPublisher eventPublisher;
    private static final String FIELD_NAME = "Attachments List";
    private static final String FIELD_NAME_ZH = "附件";
    private static final String FIELD_DESC = "Attachments List Field";
    private static final String FIELD_DESC_ZH = "附件列表字段";
    private static final String WIKI_RENDERER = "atlassian-wiki-renderer";
    private static final String FIELD_TYPE = "com.winagile.attachement:admintextfield";
    private static final String FIELD_SEACHER = "com.atlassian.jira.plugin.system.customfieldtypes:textsearcher";

    private final CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
    private final FieldScreenManager fieldScreenManager = ComponentAccessor.getFieldScreenManager();
    private final IssueManager issueManger = ComponentAccessor.getIssueManager();
    private final ProjectManager projectManager = ComponentAccessor.getProjectManager();
    // private ApplicationUser user =
    // ComponentAccessor.getJiraAuthenticationContext().getUser();
    private final IssueIndexManager indexManager = ComponentAccessor.getIssueIndexManager();
    private boolean isInitialed = false;
    private final FieldConfigManager fieldConfManager = ComponentAccessor.getComponent(FieldConfigManager.class);
    private final FieldLayoutManager layoutManager = ComponentAccessor.getFieldLayoutManager();
    private final AttachmentManager attchManager = ComponentAccessor.getAttachmentManager();
    private CustomField cField;
    private final ExecutorService es = Executors.newCachedThreadPool();
    private final ColumnService columnService = ComponentAccessor.getComponent(ColumnService.class);
    private User adminUser;
    private final CrowdService crowdService = ComponentAccessor.getCrowdService();
    private final UserUtil uU = ComponentAccessor.getUserUtil();
    private final ColumnLayoutManager clm = ComponentAccessor.getColumnLayoutManager();
    private final LocaleManager localeManager = ComponentAccessor.getLocaleManager();
    private final TranslationManager translationManager = ComponentAccessor.getTranslationManager();
    private volatile String baseUrl;

    @Inject
    public InitField(ApplicationProperties applicationProperties, EventPublisher eventPublisher) {
        System.out.println(
                "################################ InitAttachemmentListField ################################################");
        this.applicationProperties = applicationProperties;
        this.eventPublisher = eventPublisher;
        this.eventPublisher.register(this);
        Collection<User> admins = uU.getJiraSystemAdministrators();
        adminUser = admins.iterator().hasNext() ? admins.iterator().next() : null;
        baseUrl = applicationProperties.getBaseUrl(UrlMode.CANONICAL);
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
    }

    private CustomField createCustomField() {
        List<GenericValue> issueTypes = new ArrayList<GenericValue>();
        issueTypes.add(null);

        List<JiraContextNode> contexts = new ArrayList<JiraContextNode>();
        contexts.add(GlobalIssueContext.getInstance());

        //        while (customFieldManager.getCustomFieldType(FIELD_TYPE) == null) {
        //            try {
        //                System.out.println(MessageFormatter
        //                        .format("Field type [{}] not found ", FIELD_TYPE));
        //                Thread.sleep(20 * 1000);
        //            } catch (InterruptedException e) {
        //                // TODO Auto-generated catch block
        //                e.printStackTrace();
        //            }
        //        }

        try {
            return customFieldManager.createCustomField(FIELD_NAME, FIELD_DESC,
                    customFieldManager.getCustomFieldType(FIELD_TYPE),
                    customFieldManager.getCustomFieldSearcher(FIELD_SEACHER), contexts, issueTypes);
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void updateLayout() {
        List<EditableFieldLayout> layoutList = layoutManager.getEditableFieldLayouts();
        for (EditableFieldLayout layout : layoutList) {
            FieldLayoutItem item = layout.getFieldLayoutItem(cField);
            layout.setRendererType(item, WIKI_RENDERER);
            layoutManager.storeEditableFieldLayout(layout);

            System.out.println(MessageFormatter
                    .format("Update field config [{}] to atlassian-wiki-renderer ", layout.getName()).getMessage());
        }
    }

    private void traverceProject() {
        try {
            List<Project> pList = projectManager.getProjectObjects();
            for (Project p : pList) {
                Collection<Long> issueIds;
                issueIds = issueManger.getIssueIdsForProject(p.getId());
                for (Long issueId : issueIds) {
                    updateAttachementField(issueId);
                }
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        final ClassLoader cl = this.getClass().getClassLoader();
        new Thread(new Runnable() {
            @Override
            public void run() {
                ClassLoader orig = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(cl);
                onStart();
                isInitialed = true;
                /*indexManager
                        .reIndexAllIssuesInBackground(Contexts.percentageReporter(new Sized() {
                            @Override
                            public int size() {
                                return 0;
                            }
                
                            @Override
                            public boolean isEmpty() {
                                return false;
                            }
                        }, new TaskProgressSink() {
                
                            @Override
                            public void makeProgress(long taskProgress, String currentSubTask, String message) {
                
                            }
                        },
                                ComponentAccessor.getI18nHelperFactory().getInstance(ApplicationUsers.from(adminUser)),
                                Logger.getLogger(InitField.class), ""));*/
                try {
                    indexManager.reIndexAll();
                } catch (IndexException e) {
                    e.printStackTrace();
                }
                System.out.println("Updating for all Issues adding AttachmentField is finished ");
                Thread.currentThread().setContextClassLoader(orig);
            }
        }).start();

        System.out.println(
                "################################ InitAttachemmentListField is done ################################################");
    }

    public void onStart() {
        cField = customFieldManager.getCustomFieldObjectByName(FIELD_NAME);
        if (cField == null) {
            cField = createCustomField();
            if (cField != null) {
                updateLayout();
                traverceProject();
                updateNavigatorColumn();
                translateField();
                // Will be removed after plugin completed
                //                FieldScreen defaultScreen = fieldScreenManager.getFieldScreen(FieldScreen.DEFAULT_SCREEN_ID);
                //                if (!defaultScreen.containsField(cField.getId())) {
                //                    FieldScreenTab firstTab = defaultScreen.getTab(0);
                //                    firstTab.addFieldScreenLayoutItem(cField.getId());
                //                    fieldScreenManager.updateFieldScreen(defaultScreen);
                //                }
            }
        }
    }

    private void translateField() {
        Set<Locale> localeSet = localeManager.getInstalledLocales();
        for (Locale l : localeSet) {
            if (l.getLanguage().equals("zh")) {
                translationManager.setCustomFieldTranslation(cField, l, FIELD_NAME_ZH, FIELD_DESC_ZH);
            }
        }
    }

    private void updateNavigatorColumn() {
        final ServiceOutcome<ColumnLayout> outcome = columnService
                .getDefaultColumnLayout(ApplicationUsers.from(adminUser));
        if (outcome.isValid()) {
            final List<ColumnLayoutItem> columnLayoutItems = outcome.getReturnedValue().getColumnLayoutItems();
            List<String> targetColumns = Lists.newArrayList();
            for (ColumnLayoutItem item : columnLayoutItems) {
                targetColumns.add(item.getId());
            }
            targetColumns.add(4, cField.getId());
            columnService.setDefaultColumns(ApplicationUsers.from(adminUser), targetColumns);
            System.out.println(MessageFormatter
                    .format("Inser Column [{}]:[{}] to system navigator", cField.getId(), cField.getName())
                    .getMessage());
        }
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) throws RemoteException {
        if (isInitialed) {
            final Issue issue = issueEvent.getIssue();
            System.out.println("################################ IssueEvent: " + issueEvent.getEventTypeId() + " "
                    + issue.getKey());
            updateAttachementField(issue.getId());
        }
    }

    private void updateAttachementField(Long issueId) {
        MutableIssue isssue = issueManger.getIssueObject(issueId);
        List<Attachment> attList = attchManager.getAttachments(isssue);
        StringBuffer sb = new StringBuffer();

        if (cField != null) {
            for (Attachment att : attList) {
                sb.append("[").append(att.getFilename()).append("|").append(baseUrl).append("secure/attachment/")
                        .append(att.getId()).append("/").append(att.getFilename()).append("]").append("\\\\ ");
            }
            isssue.setCustomFieldValue(cField, sb.toString());
            if (adminUser != null) {
                System.out.println(MessageFormatter
                        .format("Update field [{}] to issue [{}] ", FIELD_NAME, isssue.getKey()).getMessage());
                issueManger.updateIssue(adminUser, isssue, EventDispatchOption.DO_NOT_DISPATCH,
                        false);
                try {
                    indexManager.reIndex(isssue);
                } catch (IndexException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
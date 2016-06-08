package com.winagile.attachement.impl;

import org.slf4j.helpers.MessageFormatter;

import com.atlassian.jira.issue.customfields.impl.GenericTextCFType;
import com.atlassian.jira.issue.customfields.manager.GenericConfigManager;
import com.atlassian.jira.issue.customfields.persistence.CustomFieldValuePersister;

public class JiraCustomField extends GenericTextCFType {

    public JiraCustomField(CustomFieldValuePersister customFieldValuePersister,
            GenericConfigManager genericConfigManager) {
        super(customFieldValuePersister, genericConfigManager);
        System.out.println(MessageFormatter
                .format("Initiallizing Field type [{}] ", this.getClass().getName()));
    }

}

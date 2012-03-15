/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2011 Zimbra, Inc.
 *
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.3 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.soap.mail.message;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.zimbra.common.soap.MailConstants;
import com.zimbra.soap.mail.type.EmailAddrInfo;
import com.zimbra.soap.type.Id;

/**
 * @zm-api-command-description Send share notification
 * <br />
 * The client can list the recipient email addresses for the share, along with the itemId of the item being shared.
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name=MailConstants.E_SEND_SHARE_NOTIFICATION_REQUEST)
public class SendShareNotificationRequest {

    /**
     * @zm-api-field-tag item-id
     * @zm-api-field-description Item ID
     */
    @XmlElement(name=MailConstants.E_ITEM /* item */, required=false)
    private Id item;

    /**
     * @zm-api-field-tag email-addrs
     * @zm-api-field-description Email addresses
     */
    @XmlElement(name=MailConstants.E_EMAIL /* e */, required=false)
    private List<EmailAddrInfo> emailAddresses = Lists.newArrayList();

    /**
     * @zm-api-field-tag notes
     * @zm-api-field-description Notes
     */
    @XmlElement(name=MailConstants.E_NOTES /* notes */, required=false)
    private String notes;

    public SendShareNotificationRequest() {
    }

    public void setItem(Id item) { this.item = item; }
    public void setEmailAddresses(Iterable <EmailAddrInfo> emailAddresses) {
        this.emailAddresses.clear();
        if (emailAddresses != null) {
            Iterables.addAll(this.emailAddresses,emailAddresses);
        }
    }
    public void addEmailAddress(EmailAddrInfo emailAddress) {
        this.emailAddresses.add(emailAddress);
    }
    public void setNotes(String notes) { this.notes = notes; }

    public Id getItem() { return item; }
    public List<EmailAddrInfo> getEmailAddresses() {
        return Collections.unmodifiableList(emailAddresses);
    }
    public String getNotes() { return notes; }

    public Objects.ToStringHelper addToStringInfo(Objects.ToStringHelper helper) {
        return helper
            .add("item", item)
            .add("email", emailAddresses)
            .add("notes", notes);
    }

    @Override
    public String toString() {
        return addToStringInfo(Objects.toStringHelper(this)).toString();
    }
}

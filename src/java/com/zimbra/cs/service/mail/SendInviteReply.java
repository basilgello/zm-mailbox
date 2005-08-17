/*
 * Created on Mar 2, 2005
 */
package com.zimbra.cs.service.mail;

import java.util.*;
import javax.mail.internet.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.zimbra.cs.account.*;
import com.zimbra.cs.mailbox.*;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.mailbox.calendar.IcalXmlStrMap;
import com.zimbra.cs.service.Element;
import com.zimbra.cs.service.ServiceException;
import com.zimbra.cs.service.util.ParsedItemID;
import com.zimbra.cs.stats.StopWatch;
import com.zimbra.soap.ZimbraContext;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.parameter.PartStat;

/**
 * @author tim
 */
public class SendInviteReply extends SendMsg {

    private static Log sLog = LogFactory.getLog(SendInviteReply.class);
    private static StopWatch sWatch = StopWatch.getInstance("SendInviteReply");
    
    /* (non-Javadoc)
     * @see com.zimbra.soap.DocumentHandler#handle(org.dom4j.Element, java.util.Map)
     */
    public Element handle(Element request, Map context) throws ServiceException {
        long startTime = sWatch.start();

        try {
            ZimbraContext lc = getZimbraContext(context);
            Mailbox mbox = getRequestedMailbox(lc);
            Account acct = this.getRequestedAccount(lc);
            OperationContext octxt = lc.getOperationContext();

//            int invId = (int)request.getAttributeLong("id");
            ParsedItemID pid = ParsedItemID.Parse(request.getAttribute("id"));
            int compNum = (int)request.getAttributeLong("compNum");
            
            String verbStr = request.getAttribute(MailService.A_VERB);
            ParsedVerb verb = parseVerb(verbStr);
            
            boolean updateOrg = request.getAttributeBool(MailService.A_APPT_UPDATE_ORGANIZER, true);
            // FIXME -- HACK until client is fixed
            updateOrg = true;
            
            if (sLog.isInfoEnabled()) {
                sLog.info("<SendInviteReply id=" + pid.toString() + " verb=" + verb +" updateOrg="+updateOrg+"> " + lc.toString());
            }
            
            int replyId = 0;
            
            synchronized (mbox) {
                Invite inv = null;
                
                int apptId; 
                int inviteMsgId;
                
                // the user could be accepting EITHER the original-mail-item (id="nnn") OR the
                // appointment (id="aaaa-nnnn") --- work in both cases
                if (pid.hasSubId()) {
                    // directly accepting the appointment
                    apptId = pid.getItemIDInt();
                    inviteMsgId = pid.getSubIdInt();
                    Appointment appt = mbox.getAppointmentById(apptId); 
                    inv = appt.getInvite(inviteMsgId, compNum);
                } else {
                    // accepting the message: go find the appointment and then the invite
                    inviteMsgId = pid.getItemIDInt();
                    Message msg = mbox.getMessageById(inviteMsgId);
                    Message.ApptInfo info = msg.getApptInfo(compNum);
                    apptId = info.getAppointmentId();
                    Appointment appt = mbox.getAppointmentById(apptId);
                    inv = appt.getInvite(inviteMsgId, compNum);  
                }

                if (updateOrg) {
                    String replySubject = this.getReplySubject(verb, inv);
                    
                    Calendar iCal = CalendarUtils.buildReplyCalendar(acct, inv, verb, replySubject);
                    System.out.println("GENERATED ICAL:\n"+iCal.toString());
                    
                    MimeMessage toSend = null;
                    
                    ParseMimeMessage.MimeMessageData parsedMessageData = new ParseMimeMessage.MimeMessageData();
                    
                    // did they specify a custom <m> message?  If so, then we don't have to build one...
                    Element msgElem = request.getOptionalElement(MailService.E_MSG);
                    if (msgElem != null) {
                        MimeBodyPart[] mbps = new MimeBodyPart[1];
                        mbps[0] = CalendarUtils.makeICalIntoMimePart(inv.getUid(), iCal);
                        
                        // the <inv> element is *NOT* allowed -- we always build it manually
                        // based on the params to the <SendInviteReply> and stick it in the 
                        // mbps (additionalParts) parameter...
                        toSend = ParseMimeMessage.parseMimeMsgSoap(octxt, mbox, msgElem, mbps, 
                                ParseMimeMessage.NO_INV_ALLOWED_PARSER, parsedMessageData);
                    } else {
                        // build a default "Accepted" response
                        toSend = createDefaultReply(acct, inv, replySubject, verb, iCal); 
                    }
                    
                    replyId = sendMimeMessage(octxt, mbox, acct, shouldSaveToSent(acct), parsedMessageData, 
                            toSend, inv.getMailItemId(), TYPE_REPLY);
                    
                }

                mbox.modifyInvitePartStat(octxt, apptId, inviteMsgId, compNum, false, verb.getXmlPartStat());
                    
                    /**
                     * MOVE the replied-to message into the calendar folder: then, depending on user
                     * prefs, copy it back into its original location....we do it this way so that we 
                     * don't have to walk all the Appointment DS's and modify them to point to the new
                     * copy (we REALLY want the "live" copy to be the one in Calendar folder)
                     **/

                if (acct.getBooleanAttr(Provisioning.A_zimbraPrefDeleteInviteOnReply, true)) {
                    mbox.move(octxt, inviteMsgId, MailItem.TYPE_MESSAGE, Mailbox.ID_FOLDER_TRASH);
                }
            }
            
            Element response = lc.createElement(MailService.SEND_INVITE_REPLY_RESPONSE);
            if (replyId != 0) {
                response.addAttribute(MailService.A_ID, replyId);
            }

            return response;
        } finally {
            sWatch.stop(startTime);
        }
    }
    
    String getReplySubject(ParsedVerb verb, Invite inv) {
        StringBuffer toRet = new StringBuffer(verb.toString());
        toRet.append(": ");
        toRet.append(inv.getName());
        
        return toRet.toString();
    }
    

    MimeMessage createDefaultReply(Account acct, Invite inv, 
            String replySubject, ParsedVerb verb, Calendar iCal) throws ServiceException 
    {
        /////////
        // Build the default text part and add it to the mime multipart
        StringBuffer replyText = new StringBuffer(acct.getName());
        replyText.append(" has ");
        replyText.append(verb.toString());
        replyText.append(" your invitation");
        
        return CalendarUtils.createDefaultCalendarMessage(acct, inv.getOrganizer().getCalAddress(), replySubject, 
                replyText.toString(), inv.getUid(), iCal);
    }
    
    protected final static class ParsedVerb {
        String name;
        String xmlPartStat;      // XML participant status
        String iCalPartStat;     // iCal participant status
        public ParsedVerb(String name, String xmlPartStat, String iCalPartStat) {
            this.name = name;
            this.xmlPartStat = xmlPartStat;
            this.iCalPartStat = iCalPartStat;
        }
        public String toString() { return name; }
        String getXmlPartStat() { return xmlPartStat; }
        String getICalPartStat() { return iCalPartStat; }
    }
    
    protected final static ParsedVerb VERB_ACCEPT =
        new ParsedVerb("ACCEPT",
                       IcalXmlStrMap.PARTSTAT_ACCEPTED,
                       PartStat.ACCEPTED.getValue());
    protected final static ParsedVerb VERB_DECLINE =
        new ParsedVerb("DECLINE",
                       IcalXmlStrMap.PARTSTAT_DECLINED,
                       PartStat.DECLINED.getValue());
    protected final static ParsedVerb VERB_TENTATIVE =
        new ParsedVerb("TENTATIVE",
                       IcalXmlStrMap.PARTSTAT_TENTATIVE,
                       PartStat.TENTATIVE.getValue());
    
    protected static HashMap /* string, parsedverb */ sVerbs;
    static {
        sVerbs = new HashMap();
        sVerbs.put(MailService.A_APPT_ACCEPT, VERB_ACCEPT); 
        sVerbs.put(MailService.A_APPT_DECLINE, VERB_DECLINE); 
        sVerbs.put(MailService.A_APPT_TENTATIVE, VERB_TENTATIVE); 
    }
    
    protected ParsedVerb parseVerb(String str) throws ServiceException
    {
        Object obj = sVerbs.get(str.toLowerCase());
        if (obj != null)
            return (ParsedVerb)obj;
        throw ServiceException.INVALID_REQUEST("Unknown Reply Verb: " + str, null);
    }
}

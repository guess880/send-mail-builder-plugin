package org.jenkinsci.plugins.send_mail_builder;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import hudson.model.FreeStyleProject;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;

import jenkins.model.JenkinsLocationConfiguration;

import org.jenkinsci.plugins.send_mail_builder.SendMailBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.mock_javamail.Mailbox;

public class SendMailBuilderTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testConfigurationRoundTrip() throws Exception {
        final FreeStyleProject p = j.createFreeStyleProject();
        final SendMailBuilder before = new SendMailBuilder(
                "tos", "ccs", "bccs", "replyTo", "subject", "text");
        p.getBuildersList().add(before);
        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));
        final SendMailBuilder after = p.getBuildersList().get(SendMailBuilder.class);
        j.assertEqualBeans(before, after, "tos,ccs,bccs,replyTo,subject,text");
    }

    @Test
    public void testMail() throws Exception {
        final String from = "from@send-mail-plugin.org";
        final String to = "to";
        final String cc = "cc";
        final String bcc = "bcc";
        final String replyTo = "replyTo";
        final String subject = "subject";
        final String text = "text";
        final Mailbox inbox = Mailbox.get(new InternetAddress(to));
        inbox.clear();
        JenkinsLocationConfiguration.get().setAdminAddress(from);
        final FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new SendMailBuilder(
                to, cc, bcc, replyTo, subject, text));
        p.scheduleBuild2(0).get();
        assertThat(inbox.size(), is(equalTo(1)));
        final Message message = inbox.get(0);
        verifyAddress(message.getFrom(), from);
        verifyAddress(message.getRecipients(RecipientType.TO), to);
        verifyAddress(message.getRecipients(RecipientType.CC), cc);
        verifyAddress(message.getRecipients(RecipientType.BCC), bcc);
        verifyAddress(message.getReplyTo(), replyTo);
        assertThat(message.getSubject(), is(equalTo(subject)));
        assertThat(message.isMimeType("text/plain"), is(equalTo(true)));
        assertThat((String) message.getContent(), is(equalTo(text)));
    }

    private void verifyAddress(final Address[] actual, final String expected) {
        assertThat(actual.length, is(equalTo(1)));
        assertThat(actual[0].toString(), is(equalTo(expected)));
    }

}

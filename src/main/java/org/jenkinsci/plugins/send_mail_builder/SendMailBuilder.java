/*
 * The MIT License
 *
 * Copyright (c) 2014-, guess880
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.send_mail_builder;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Mailer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import jenkins.model.JenkinsLocationConfiguration;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public class SendMailBuilder extends Builder {

    private final String tos;

    private final String ccs;

    private final String bccs;

    private final String replyTo;

    private final String subject;

    private final String text;

    @DataBoundConstructor
    public SendMailBuilder(final String tos, final String ccs,
            final String bccs, final String replyTo, final String subject, final String text) {
        super();
        this.tos = tos;
        this.ccs = ccs;
        this.bccs = bccs;
        this.replyTo = replyTo;
        this.subject = subject;
        this.text = text;
    }

    public final String getTos() {
        return tos;
    }

    public final String getCcs() {
        return ccs;
    }

    public final String getBccs() {
        return bccs;
    }

    public final String getReplyTo() {
        return replyTo;
    }

    public final String getSubject() {
        return subject;
    }

    public final String getText() {
        return text;
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build,
            final Launcher launcher, final BuildListener listener)
            throws IOException, InterruptedException {
        final EnvVars env = build.getEnvironment(listener);
        final String charset = Mailer.descriptor().getCharset();
        final MimeMessage msg = new MimeMessage(Mailer.descriptor().createSession());
        try {
            msg.setFrom(Mailer.StringToAddress(JenkinsLocationConfiguration.get().getAdminAddress(), charset));
            msg.setContent("", "text/plain");
            msg.setSentDate(new Date());
            String actualReplyTo = env.expand(replyTo);
            if (StringUtils.isBlank(actualReplyTo)) {
                actualReplyTo = Mailer.descriptor().getReplyToAddress();
            }
            if (StringUtils.isNotBlank(actualReplyTo)) {
                msg.setReplyTo(new Address[] { Mailer.StringToAddress(actualReplyTo, charset) });
            }
            msg.setRecipients(RecipientType.TO, toInternetAddresses(listener, env.expand(tos), charset));
            msg.setRecipients(RecipientType.CC, toInternetAddresses(listener, env.expand(ccs), charset));
            msg.setRecipients(RecipientType.BCC, toInternetAddresses(listener, env.expand(bccs), charset));
            msg.setSubject(env.expand(subject), charset);
            msg.setText(env.expand(text), charset);
            Transport.send(msg);
        } catch (final MessagingException e) {
            listener.getLogger().println(Messages.SendMail_FailedToSend());
            e.printStackTrace(listener.error(e.getMessage()));
            return false;
        }
        listener.getLogger().println(Messages.SendMail_SentSuccessfully());
        return true;
    }

    private static final InternetAddress[] toInternetAddresses(
            final BuildListener listener, final String addresses,
            final String charset) throws UnsupportedEncodingException {
        final String defaultSuffix = Mailer.descriptor().getDefaultSuffix();
        final Set<InternetAddress> rcp = new LinkedHashSet<InternetAddress>();
        final StringTokenizer tokens = new StringTokenizer(addresses);
        while (tokens.hasMoreTokens()) {
            String address = tokens.nextToken();
            // if not a valid address (i.e. no '@'), then try adding suffix
            if (!address.contains("@") && defaultSuffix != null && defaultSuffix.contains("@")) {
                address += defaultSuffix;
            }
            try {
                rcp.add(Mailer.StringToAddress(address, charset));
            } catch (final AddressException e) {
                // report bad address, but try to send to other addresses
                listener.getLogger().println("Unable to send to address: " + address);
                e.printStackTrace(listener.error(e.getMessage()));
            }
        }
        return rcp.toArray(new InternetAddress[0]);
    }

    @Extension
    public static final class DescriptorImpl extends
            BuildStepDescriptor<Builder> {

        @SuppressWarnings("rawtypes")
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.SendMail_DisplayName();
        }

    }

}

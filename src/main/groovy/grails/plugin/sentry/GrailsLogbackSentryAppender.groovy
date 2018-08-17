/*
 * Copyright 2016 Alan Rafael Fachini, authors, and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.sentry

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.status.ErrorStatus
import ch.qos.logback.core.status.Status
import grails.util.Environment
import grails.util.Metadata
import groovy.transform.CompileStatic
import io.sentry.Sentry
import io.sentry.SentryClient
import io.sentry.event.EventBuilder
import io.sentry.event.interfaces.ExceptionInterface
import io.sentry.event.interfaces.MessageInterface
import io.sentry.event.interfaces.StackTraceInterface
import io.sentry.logback.SentryAppender

@CompileStatic
class GrailsLogbackSentryAppender extends SentryAppender {

    private static final String TAG_GRAILS_APP_NAME = 'grails_app_name'
    private static final String TAG_GRAILS_VERSION = 'grails_version'

    SentryConfig config
    String release

    GrailsLogbackSentryAppender(SentryConfig config, String release = '') {
        super()
        this.config = config
        this.release = release
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!config.active) {
            return
        }

        if ((config.levels ?: SentryConfig.defaultLevels).contains(event.level)) {
            super.append(event)
        }
    }

    @Override
    protected EventBuilder createEventBuilder(ILoggingEvent iLoggingEvent) {
        EventBuilder eventBuilder = new EventBuilder()
                .withSdkIntegration('logback')
                .withTimestamp(new Date(iLoggingEvent.getTimeStamp()))
                .withMessage(iLoggingEvent.getFormattedMessage())
                .withLogger(iLoggingEvent.getLoggerName())
                .withLevel(formatLevel(iLoggingEvent.getLevel()))
                .withExtra(THREAD_NAME, iLoggingEvent.getThreadName())
                .withRelease(release)

        // remove trash from message
        if (iLoggingEvent.getFormattedMessage().contains(' Stacktrace follows:')) {
            eventBuilder.withMessage(iLoggingEvent.getFormattedMessage().replace(' Stacktrace follows:', ''))
        }

        // remove trash from message
        if (iLoggingEvent.getFormattedMessage().trim() == 'Full Stack Trace:') {
            eventBuilder.withMessage(iLoggingEvent.getFormattedMessage().trim().replace('Full Stack Trace:', ''))
        }

        if (iLoggingEvent.argumentArray) {
            eventBuilder.withSentryInterface(
                    new MessageInterface(iLoggingEvent.message, formatMessageParameters(iLoggingEvent.argumentArray))
            )
        }

        if (iLoggingEvent.getThrowableProxy() != null) {
            eventBuilder.withSentryInterface(new ExceptionInterface(extractExceptionQueue(iLoggingEvent)))
        } else if (iLoggingEvent.getCallerData().length > 0) {
            eventBuilder.withSentryInterface(new StackTraceInterface(iLoggingEvent.getCallerData()))
        }

        // override "grails.plugin.sentry.GrailsLogbackSentryAppender" as culprit by more concrete message
        if (iLoggingEvent.throwableProxy != null && iLoggingEvent.throwableProxy.cause != null &&
                iLoggingEvent.throwableProxy.cause.stackTraceElementProxyArray.length > 0) {
            eventBuilder.withCulprit(iLoggingEvent.throwableProxy.cause.stackTraceElementProxyArray[0].toString())
            eventBuilder.withLogger(iLoggingEvent.throwableProxy.cause.stackTraceElementProxyArray[0].stackTraceElement.className)
        } else if (iLoggingEvent.getCallerData().length > 0) {
            eventBuilder.withCulprit(iLoggingEvent.getCallerData()[0])
        } else {
            eventBuilder.withCulprit(iLoggingEvent.getLoggerName())
        }

        for (Map.Entry<String, String> contextEntry : iLoggingEvent.loggerContextVO.propertyMap.entrySet()) {
            eventBuilder.withExtra(contextEntry.key, contextEntry.value)
        }

        SentryClient client = Sentry.storedClient

        for (Map.Entry<String, String> mdcEntry : iLoggingEvent.getMDCPropertyMap().entrySet()) {
            if (client.mdcTags.contains(mdcEntry.key)) {
                eventBuilder.withTag(mdcEntry.key, mdcEntry.value)
            } else {
                eventBuilder.withExtra(mdcEntry.key, mdcEntry.value)
            }
        }

        if (iLoggingEvent.marker) {
            eventBuilder.withTag(LOGBACK_MARKER, iLoggingEvent.marker.name)
        }

        for (Map.Entry<String, String> tagEntry : client.tags.entrySet()) {
            eventBuilder.withTag(tagEntry.key, tagEntry.value)
        }

        Metadata metadata = Metadata.current
        eventBuilder.withTag(TAG_GRAILS_APP_NAME, metadata.getApplicationName())
        eventBuilder.withTag(TAG_GRAILS_VERSION, metadata.getGrailsVersion())

        if (config.tags) {
            config.tags.each { String key, String value ->
                eventBuilder.withTag(key, value)
            }
        }

        if (config.environment) {
            eventBuilder.withEnvironment(config.environment)
        } else {
            eventBuilder.withEnvironment(Environment.current.name)
        }

        // Custom
        if (config.serverName) {
            eventBuilder.withServerName(config.serverName)
        }

        return eventBuilder
    }

    @Override
    void addStatus(Status status) {
        if (status instanceof ErrorStatus) {
            // this error is otherwise completely swallowed
            status.throwable?.printStackTrace()
        }
        super.addStatus(status)
    }

}

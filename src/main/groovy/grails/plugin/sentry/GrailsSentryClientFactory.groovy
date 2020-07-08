package grails.plugin.sentry

import io.sentry.Sentry
import io.sentry.SentryClient

class GrailsSentryClientFactory {

    SentryClient createSentryClient(String dsn) {
        Sentry.init(dsn)
    }

}

package grails.plugin.sentry

import groovy.util.logging.Slf4j
import io.sentry.DefaultSentryClientFactory
import io.sentry.SentryClientFactory
import io.sentry.config.Lookup
import io.sentry.dsn.Dsn
import io.sentry.util.Util

@Slf4j
class SentryClientFactoryProvider {

    SentryClientFactory createFactory(Dsn realDsn) {
        String sentryClientFactoryName = Lookup.lookup('factory', realDsn)
        if (Util.isNullOrEmpty(sentryClientFactoryName)) {
            // no name specified, use the default factory
            return new DefaultSentryClientFactory()
        }

        try {
            // attempt to construct the user specified factory class
            Class<? extends SentryClientFactory> factoryClass = (Class<? extends SentryClientFactory>) Class.forName(sentryClientFactoryName)
            return factoryClass.newInstance()
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            log.error("Error creating SentryClient using factory class: '"
                    + sentryClientFactoryName + "'.", e);
            null
        }
    }

}

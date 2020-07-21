package grails.plugin.sentry

import ch.qos.logback.classic.Logger
import com.stehno.ersatz.ErsatzServer
import grails.testing.mixin.integration.Integration
import io.sentry.Sentry
import io.sentry.SentryClient
import io.sentry.SentryClientFactory
import io.sentry.servlet.SentryServletRequestListener
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.util.mop.ConfineMetaClassChanges

@Integration
class SanityIntegrationSpec extends Specification {

    GrailsLogbackSentryAppender sentryAppender
    SentryClient sentryClient
    SentryClientFactory sentryFactory
    SentryClientFactoryProvider sentryClientFactoryProvider
    SentryServletRequestListener sentryServletRequestListener

    @ConfineMetaClassChanges(GrailsLogbackSentryAppender)
    def "everything works"() {
        expect: "if everything is ok sentry then beans are injected"
            sentryAppender
            sentryClient
            sentryFactory
            sentryClientFactoryProvider
            sentryServletRequestListener
        when: "mock http server is started"
            ErsatzServer server = new ErsatzServer().expectations {
                post("/api/123/store/") {
                    called 1
                    responder {
                        code 200
                    }
                }
            }
            server.start()
        and: "mock server is used as Sentry endpoint"
            Sentry.init("http://foo:bar@localhost:${server.httpPort}/123?async=false")
            LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME).error("Test Me", new Exception("Failure!"))
        then: "event is send to the mock server"
            server.verify()
    }

}

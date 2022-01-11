package org.horiga.armeria

import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.SimpleDecoratingHttpService
import org.horiga.armeria.service.VerifyService
import org.slf4j.LoggerFactory
import java.util.function.Function

class AuthenticationService(
    delegate: HttpService,
    private val verifyService: VerifyService,
) : SimpleDecoratingHttpService(delegate) {

    companion object {
        val log = LoggerFactory.getLogger(AuthenticationService::class.java)!!

        fun newDecorator(verifyService: VerifyService) = Function<HttpService, AuthenticationService> {
            AuthenticationService(it, verifyService)
        }
    }

    override fun serve(ctx: ServiceRequestContext, req: HttpRequest): HttpResponse {
        return HttpResponse.from(
            ctx.makeContextAware(verifyService.verify(req)).thenApply { credentials ->
                ctx.setAttr(RequestContexts.AttributeKeys.credentials, credentials)
                unwrap().serve(ctx, req)
            }.exceptionally { err ->
                log.info("HANDLE ERROR IN DECORATOR. (exceptionally)", err)
                throw err
            }
        )
    }
}


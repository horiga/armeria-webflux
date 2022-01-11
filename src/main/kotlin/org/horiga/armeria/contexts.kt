package org.horiga.armeria

import com.linecorp.armeria.common.RequestContext
import com.linecorp.armeria.server.ServiceRequestContext
import io.netty.util.AttributeKey
import org.horiga.armeria.service.VerifyService

object RequestContexts {

    object AttributeKeys {
        val credentials: AttributeKey<VerifyService.RequestCredentials> =
            AttributeKey.valueOf("credentials")
    }

    fun credentials(ctx: ServiceRequestContext? = null): VerifyService.RequestCredentials? =
        ctx?.attr(AttributeKeys.credentials) ?: RequestContext.current<ServiceRequestContext>()
            .attr(AttributeKeys.credentials)
}
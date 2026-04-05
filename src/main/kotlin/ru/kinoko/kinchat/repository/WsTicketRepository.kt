package ru.kinoko.kinchat.repository

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import ru.kinoko.kinchat.jooq.tables.references.WS_TICKETS
import java.time.OffsetDateTime
import java.util.UUID

@Repository
class WsTicketRepository(
    private val dsl: DSLContext,
) {
    fun createWsTicket(userId: UUID, expiresAt: OffsetDateTime): UUID {
        val ticket = UUID.randomUUID()

        dsl.insertInto(WS_TICKETS)
            .set(WS_TICKETS.TICKET, ticket)
            .set(WS_TICKETS.USER_ID, userId)
            .set(WS_TICKETS.EXPIRES_AT, expiresAt)
            .set(WS_TICKETS.USED_AT, null as OffsetDateTime?)
            .execute()

        return ticket
    }

    fun consumeTicket(ticket: UUID, usedAt: OffsetDateTime): UUID? = dsl.transactionResult { configuration ->
        val tx = DSL.using(configuration)
        tx.update(WS_TICKETS)
            .set(WS_TICKETS.USED_AT, usedAt)
            .where(WS_TICKETS.TICKET.eq(ticket))
            .and(WS_TICKETS.USED_AT.isNull)
            .and(WS_TICKETS.EXPIRES_AT.gt(usedAt))
            .returning(WS_TICKETS.USER_ID)
            .fetchOne(WS_TICKETS.USER_ID)
    }
}

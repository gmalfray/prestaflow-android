package com.rebuildit.prestaflow.data.sav.mapper

import com.rebuildit.prestaflow.data.remote.dto.SavMessageDto
import com.rebuildit.prestaflow.data.remote.dto.SavThreadDto
import com.rebuildit.prestaflow.domain.sav.model.SavMessage
import com.rebuildit.prestaflow.domain.sav.model.SavMessageAuthor
import com.rebuildit.prestaflow.domain.sav.model.SavThread
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus

fun SavThreadDto.toDomain(): SavThread =
    SavThread(
        id = id,
        status = SavThreadStatus.fromApiValue(status),
        unread = unread,
        toProcess = toProcess,
        customerId = customer?.id,
        customerName = customer?.name,
        customerEmail = customer?.email,
        orderId = order?.id,
        orderReference = order?.reference,
        lastMessageAtIso = lastMessageAt,
        dateAddedIso = dateAdd,
        dateUpdatedIso = dateUpd,
    )

fun SavMessageDto.toDomain(): SavMessage =
    SavMessage(
        id = id,
        author = if (author == "employee") SavMessageAuthor.EMPLOYEE else SavMessageAuthor.CUSTOMER,
        employeeName = employeeName,
        message = message,
        private = private,
        read = read,
        dateAddedIso = dateAdd,
    )

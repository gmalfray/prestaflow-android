package com.rebuildit.prestaflow.data.reviews.mapper

import com.rebuildit.prestaflow.data.remote.dto.ReviewDto
import com.rebuildit.prestaflow.domain.reviews.model.Review

fun ReviewDto.toDomain(): Review =
    Review(
        id = id,
        productId = product?.id,
        productName = product?.name,
        authorName = author?.name.orEmpty(),
        authorEmail = author?.email,
        grade = grade,
        title = title,
        content = content,
        verifiedBuyer = verifiedBuyer,
        validated = validated,
        deleted = deleted,
        reply = reply,
        rejectionReason = rejectionReason,
        dateAddedIso = dateAdd,
    )

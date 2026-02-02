package com.fieldtrack.app.data.remote.dto

import com.fieldtrack.app.data.model.Report
import com.google.gson.annotations.SerializedName

data class ReportDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("date")
    val date: String,
    @SerializedName("summary")
    val summary: String,
    @SerializedName("optional_image_url")
    val optionalImageUrl: String?
) {
    fun toModel(): Report = Report(
        id = id,
        userId = userId,
        date = date,
        summary = summary,
        optionalImageUrl = optionalImageUrl
    )

    companion object {
        fun fromModel(model: Report): ReportDto = ReportDto(
            id = model.id,
            userId = model.userId,
            date = model.date,
            summary = model.summary,
            optionalImageUrl = model.optionalImageUrl
        )
    }
}

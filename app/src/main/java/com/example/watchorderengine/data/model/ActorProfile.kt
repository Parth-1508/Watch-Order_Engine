package com.example.watchorderengine.data.model

data class ActorSummary(
    val id: Int,
    val name: String,
    val profilePath: String?,
    val knownForDepartment: String?,
    val popularity: Float?
)

data class ActorDetail(
    val id: Int,
    val name: String,
    val biography: String,
    val profilePath: String?,
    val birthday: String?,
    val placeOfBirth: String?,
    val knownForDepartment: String?,
    val images: List<String>,
    val filmography: List<CreditItem>,
    val topWorks: List<CreditItem>
)

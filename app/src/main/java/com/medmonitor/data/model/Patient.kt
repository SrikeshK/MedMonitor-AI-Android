package com.medmonitor.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Patient(
    @DocumentId val id: String = "",
    val name: String = "",
    val phone: String = "",
    val age: Int = 0,
    val gender: String = "",
    val relation: String = "",
    val caregiverId: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

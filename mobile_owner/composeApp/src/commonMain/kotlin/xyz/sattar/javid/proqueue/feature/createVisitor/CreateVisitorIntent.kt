package xyz.sattar.javid.proqueue.feature.createVisitor

sealed class CreateVisitorIntent {
    data class CreateVisitor(
        val fullName: String,
        val phoneNumber: String,
        val id: Long = 0
    ) : CreateVisitorIntent()
    data class EditVisitor(
        val fullName: String,
        val phoneNumber: String,
        val visitorId: Long?
    ) : CreateVisitorIntent()
    
    data class LoadVisitor(val visitorId: Long) : CreateVisitorIntent()
    
    object BackPress : CreateVisitorIntent()
}

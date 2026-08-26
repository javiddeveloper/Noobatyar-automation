package xyz.sattar.javid.proqueue.domain.model.user

/**
 * Local cache shape of the owner's subscription — mirrors `SubscriptionEntity`
 * (Room, `roomMain`) field-for-field so [xyz.sattar.javid.proqueue.data.localDataSource.user.UserLocalSource]
 * can speak in domain terms instead. Not the same as [SubscriptionDto][xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto]:
 * that one carries the full [PlanDto][xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PlanDto],
 * this one only the plan's name (all the cache ever needed).
 */
data class Subscription(
    val id: Int = 1,
    val planName: String?,
    val startedAt: String?,
    val endsAt: String?,
    val isValid: Boolean
)

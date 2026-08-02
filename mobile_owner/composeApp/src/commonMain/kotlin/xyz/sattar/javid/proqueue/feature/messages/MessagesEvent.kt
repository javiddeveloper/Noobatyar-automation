package xyz.sattar.javid.proqueue.feature.messages

sealed interface MessagesEvent {
    /**
     * The owner asked for server-side reminders without the entitlement. The
     * add-ons screen is where the plan/credit upsell already lives, so we send
     * them there rather than growing a second purchase surface here.
     */
    data object NavigateToAddons : MessagesEvent
}

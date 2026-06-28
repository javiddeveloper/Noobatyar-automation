package xyz.sattar.javid.proqueue.di

import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import xyz.sattar.javid.proqueue.core.network.HttpClientFactory
import xyz.sattar.javid.proqueue.data.localDataSource.AppDatabase
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.UserApiService
import xyz.sattar.javid.proqueue.data.repository.appointment.AppointmentRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.business.BusinessRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.message.MessageRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.user.UserRepositoryImpl
import xyz.sattar.javid.proqueue.data.repository.visitor.VisitorRepositoryImpl
import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.MessageRepository
import xyz.sattar.javid.proqueue.domain.UserRepository
import xyz.sattar.javid.proqueue.domain.VisitorRepository
import xyz.sattar.javid.proqueue.domain.usecase.BusinessUpsertUseCase
import xyz.sattar.javid.proqueue.domain.usecase.CheckAppointmentConflictUseCase
import xyz.sattar.javid.proqueue.domain.usecase.CreateAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.DeleteBusinessUseCase
import xyz.sattar.javid.proqueue.domain.usecase.DeleteVisitorUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GenerateReminderMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAllVisitorsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAppointmentByIdUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAppointmentsForDateUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetTodayAppointmentsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetTodayStatsUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetVisitorByIdUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetWaitingQueueUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentCompletedUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentNoShowUseCase
import xyz.sattar.javid.proqueue.domain.usecase.RemoveAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SendMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.UpdateAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.UserLogoutUseCase
import xyz.sattar.javid.proqueue.domain.usecase.VisitorUpsertUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.CheckVersionUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.ClearTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetUserProfileUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.HasTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.LoginUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.RegisterUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.ResetPasswordUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.SendOTPUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.VerifyOTPUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMySubscriptionUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetCurrentUserUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetPlansUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.CreatePaymentUseCase
import xyz.sattar.javid.proqueue.feature.businessList.BusinessListViewModel
import xyz.sattar.javid.proqueue.feature.calendar.CalendarViewModel
import xyz.sattar.javid.proqueue.feature.createAppointment.CreateAppointmentViewModel
import xyz.sattar.javid.proqueue.feature.createBusiness.CreateBusinessState
import xyz.sattar.javid.proqueue.feature.createBusiness.CreateBusinessViewModel
import xyz.sattar.javid.proqueue.feature.createVisitor.CreateVisitorState
import xyz.sattar.javid.proqueue.feature.createVisitor.CreateVisitorViewModel
import xyz.sattar.javid.proqueue.feature.forgetPassword.resetPassword.ResetPasswordViewModel
import xyz.sattar.javid.proqueue.feature.forgetPassword.sendOTP.SendOTPViewModel
import xyz.sattar.javid.proqueue.feature.home.HomeViewModel
import xyz.sattar.javid.proqueue.feature.lastVisitors.LastVisitorsViewModel
import xyz.sattar.javid.proqueue.feature.login.LoginViewModel
import xyz.sattar.javid.proqueue.feature.messages.MessagesViewModel
import xyz.sattar.javid.proqueue.feature.notifications.NotificationsViewModel
import xyz.sattar.javid.proqueue.feature.profile.UserViewModel
import xyz.sattar.javid.proqueue.feature.register.RegisterViewModel
import xyz.sattar.javid.proqueue.feature.settings.SettingsViewModel
import xyz.sattar.javid.proqueue.feature.version.VersionViewModel
import xyz.sattar.javid.proqueue.feature.visitorDetails.VisitorDetailsViewModel
import xyz.sattar.javid.proqueue.feature.visitorSelection.VisitorSelectionViewModel

val appModule: Module = module {

    // --- Network ---
    single<HttpClient> { HttpClientFactory.create() }
    single { UserApiService(get()) }
    single { xyz.sattar.javid.proqueue.data.remoteDataSource.business.BusinessApiService(get()) }
    single { xyz.sattar.javid.proqueue.data.remoteDataSource.visitor.VisitorApiService(get()) }
    single { xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.AppointmentApiService(get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }

    // --- DAOs ---
    single { get<AppDatabase>().businessDao() }
    single { get<AppDatabase>().visitorDao() }
    single { get<AppDatabase>().appointmentDao() }
    single { get<AppDatabase>().messageDao() }
    single { get<AppDatabase>().userDao() }

    // --- Repositories ---
    single<BusinessRepository> { BusinessRepositoryImpl(get(),get()) }
    single<VisitorRepository> { VisitorRepositoryImpl(get(), get()) }
    single<AppointmentRepository> { AppointmentRepositoryImpl(get(), get(), get(), get()) }
    single<MessageRepository> { MessageRepositoryImpl(get()) }

    // --- User UseCases ---
    factory { RegisterUseCase(get()) }
    factory { LoginUseCase(get()) }
    factory { UserLogoutUseCase(get()) }
    factory { HasTokenUseCase() }
    factory { ClearTokenUseCase() }
    factory { CheckVersionUseCase(get()) }
    factory { GetUserProfileUseCase(get()) }
    factory { SendOTPUseCase(get()) }
    factory { VerifyOTPUseCase(get()) }
    factory { ResetPasswordUseCase(get()) }
    factory { GetMySubscriptionUseCase(get()) }
    factory { GetCurrentUserUseCase(get()) }
    factory { GetPlansUseCase(get()) }
    factory { CreatePaymentUseCase(get()) }

    // --- Business UseCases ---
    factory { xyz.sattar.javid.proqueue.domain.usecase.ObserveBusinessesUseCase(get()) }
    factory { xyz.sattar.javid.proqueue.domain.usecase.FetchBusinessesUseCase(get()) }
    factory { DeleteBusinessUseCase(get()) }
    factory { BusinessUpsertUseCase(get()) }

    // --- Appointment UseCases ---
    factory { GetWaitingQueueUseCase(get()) }
    factory { GetTodayAppointmentsUseCase(get()) }
    factory { CreateAppointmentUseCase(get(), get(), get(), get()) }
    factory { RemoveAppointmentUseCase(get()) }
    factory { MarkAppointmentCompletedUseCase(get()) }
    factory { MarkAppointmentNoShowUseCase(get()) }
    factory { GetTodayStatsUseCase(get()) }
    factory { GetAppointmentsForDateUseCase(get()) }
    factory { GetAppointmentByIdUseCase(get()) }
    factory { UpdateAppointmentUseCase(get()) }
    factory { CheckAppointmentConflictUseCase(get()) }
    factory { xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase(get()) }

    // --- Message UseCases ---
    factory { SendMessageUseCase(get()) }
    factory { GenerateReminderMessageUseCase() }

    // --- Visitor UseCases ---
    factory { VisitorUpsertUseCase(get()) }
    factory { GetAllVisitorsUseCase(get()) }
    factory { GetVisitorByIdUseCase(get()) }
    factory { DeleteVisitorUseCase(get(), get(), get()) }

    // --- States ---
    factory { CreateBusinessState() }
    factory { CreateVisitorState() }

    // --- ViewModels ---
     viewModel { CreateBusinessViewModel(get(), get(), get()) }
    viewModel { CreateVisitorViewModel(get(), get(), get()) }
    viewModel { CreateAppointmentViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel {
        HomeViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    viewModel {
        LastVisitorsViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    viewModel { VisitorSelectionViewModel(get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { VersionViewModel(get()) }
    viewModel { NotificationsViewModel(get(), get()) }
    viewModel { BusinessListViewModel(get(), get(), get()) }
    viewModel { VisitorDetailsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { MessagesViewModel(get()) }
    viewModel { CalendarViewModel(get(), get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { SendOTPViewModel(get(), get()) }
    viewModel { ResetPasswordViewModel(get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { UserViewModel(get(), get(), get(), get()) }
}

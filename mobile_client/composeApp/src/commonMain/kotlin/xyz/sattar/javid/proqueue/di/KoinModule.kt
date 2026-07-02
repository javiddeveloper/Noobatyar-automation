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
import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.MessageRepository
import xyz.sattar.javid.proqueue.domain.UserRepository

import xyz.sattar.javid.proqueue.domain.usecase.CreateAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetAppointmentByIdUseCase
import xyz.sattar.javid.proqueue.domain.usecase.RemoveAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.UpdateAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.UserLogoutUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.CheckVersionUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.ClearTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetUserProfileUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.HasTokenUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.LoginUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.RegisterUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.ResetPasswordUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.SendOTPUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.VerifyOTPUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetCurrentUserUseCase
import xyz.sattar.javid.proqueue.feature.businessList.BusinessListViewModel
import xyz.sattar.javid.proqueue.feature.calendar.CalendarViewModel
import xyz.sattar.javid.proqueue.feature.createAppointment.CreateAppointmentViewModel

import xyz.sattar.javid.proqueue.feature.login.LoginViewModel

import xyz.sattar.javid.proqueue.feature.notifications.NotificationsViewModel
import xyz.sattar.javid.proqueue.feature.profile.UserViewModel

import xyz.sattar.javid.proqueue.feature.settings.SettingsViewModel
import xyz.sattar.javid.proqueue.feature.version.VersionViewModel

val appModule: Module = module {

    // --- Network ---
    single<HttpClient> { HttpClientFactory.create() }
    single { UserApiService(get()) }
    single { xyz.sattar.javid.proqueue.data.remoteDataSource.business.BusinessApiService(get()) }
    single { xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.AppointmentApiService(get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }

    // --- DAOs ---
    single { get<AppDatabase>().businessDao() }
    single { get<AppDatabase>().appointmentDao() }
    single { get<AppDatabase>().messageDao() }
    single { get<AppDatabase>().userDao() }

    // --- Repositories ---
    single<BusinessRepository> { BusinessRepositoryImpl(get(),get()) }
    single<AppointmentRepository> { AppointmentRepositoryImpl(get(), get(), get()) }
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
    factory { xyz.sattar.javid.proqueue.domain.usecase.user.SendAuthOTPUseCase(get()) }
    factory { xyz.sattar.javid.proqueue.domain.usecase.user.VerifyAuthOTPUseCase(get()) }
    factory { ResetPasswordUseCase(get()) }
    factory { GetCurrentUserUseCase(get()) }

    // --- Business UseCases ---
    factory { xyz.sattar.javid.proqueue.domain.usecase.ObserveBusinessesUseCase(get()) }
    factory { xyz.sattar.javid.proqueue.domain.usecase.FetchBusinessesUseCase(get()) }


    // --- Appointment UseCases ---
    factory { xyz.sattar.javid.proqueue.domain.usecase.appointment.GetClientAppointmentsUseCase(get()) }
    factory { CreateAppointmentUseCase(get(), get(), get()) }
    factory { RemoveAppointmentUseCase(get()) }
    factory { GetAppointmentByIdUseCase(get()) }
    factory { UpdateAppointmentUseCase(get()) }
    factory { xyz.sattar.javid.proqueue.domain.usecase.UpdateAppointmentStatusUseCase(get()) }
    factory { xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase(get()) }

    // --- Message UseCases ---


    // --- States ---

    // --- ViewModels ---
    viewModel { xyz.sattar.javid.proqueue.feature.clientAppointments.ClientAppointmentsViewModel(get()) }
    viewModel { CreateAppointmentViewModel(get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel() }
    viewModel { VersionViewModel(get()) }
    viewModel { NotificationsViewModel(get(), get()) }
    viewModel { BusinessListViewModel(get(), get()) }
    viewModel { xyz.sattar.javid.proqueue.feature.businessDetail.ClientBusinessDetailViewModel(get(), get()) }

    viewModel { CalendarViewModel(get(), get()) }

    viewModel { LoginViewModel(get(), get(), get(), get()) }
    viewModel { UserViewModel(get(), get(), get()) }
}

package xyz.sattar.javid.proqueue.core.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.sattar.javid.proqueue.domain.model.business.Business

object BusinessStateHolder {
    private val _selectedBusiness = MutableStateFlow<Business?>(null)
    val selectedBusiness: StateFlow<Business?> = _selectedBusiness.asStateFlow()

    fun selectBusiness(business: Business) {
        _selectedBusiness.value = business
    }

    fun clearBusiness() {
        _selectedBusiness.value = null
    }
}

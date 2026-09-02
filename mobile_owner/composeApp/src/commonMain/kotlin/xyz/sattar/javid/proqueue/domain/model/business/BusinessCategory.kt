package xyz.sattar.javid.proqueue.domain.model.business

/**
 * Business categories, mirroring `Business.CATEGORY_CHOICES` in
 * backend/business/models.py — the backend is the source of truth, this enum
 * exists so the picker has labels without a round-trip.
 *
 * Order matches the backend's (health → beauty → professional →
 * education/sport → trades → other) so the picker and every server-rendered
 * dropdown list them the same way. [group] is presentation-only and has no
 * backend counterpart: with ~35 entries a flat picker is unusable, but the
 * wire value stays a single flat string either way.
 *
 * [fromString] falls back to [OTHER] rather than throwing, so a category
 * added on the server reaches an older build as "سایر" instead of crashing
 * the business list.
 */
enum class BusinessCategory(
    val value: String,
    val persianName: String,
    val group: BusinessCategoryGroup,
) {
    // ── سلامت و درمان ──
    DOCTOR("DOCTOR", "پزشک و کلینیک", BusinessCategoryGroup.HEALTH),
    DENTIST("DENTIST", "دندان‌پزشکی", BusinessCategoryGroup.HEALTH),
    PSYCHOLOGY("PSYCHOLOGY", "روان‌شناسی و روان‌پزشکی", BusinessCategoryGroup.HEALTH),
    PHYSIOTHERAPY("PHYSIOTHERAPY", "فیزیوتراپی و توان‌بخشی", BusinessCategoryGroup.HEALTH),
    NUTRITION("NUTRITION", "تغذیه و رژیم‌درمانی", BusinessCategoryGroup.HEALTH),
    LABORATORY("LABORATORY", "آزمایشگاه و تصویربرداری", BusinessCategoryGroup.HEALTH),
    OPTOMETRY("OPTOMETRY", "بینایی‌سنجی و عینک", BusinessCategoryGroup.HEALTH),
    SPEECH_THERAPY("SPEECH_THERAPY", "گفتاردرمانی", BusinessCategoryGroup.HEALTH),
    MIDWIFERY("MIDWIFERY", "مامایی و سلامت بانوان", BusinessCategoryGroup.HEALTH),
    VETERINARY("VETERINARY", "دامپزشکی", BusinessCategoryGroup.HEALTH),

    // ── زیبایی و آرایش ──
    BEAUTY_SALON("BEAUTY_SALON", "آرایشگاه و سالن زیبایی", BusinessCategoryGroup.BEAUTY),
    BARBERSHOP("BARBERSHOP", "آرایشگاه مردانه", BusinessCategoryGroup.BEAUTY),
    NAIL_SALON("NAIL_SALON", "سالن ناخن", BusinessCategoryGroup.BEAUTY),
    SKIN_LASER("SKIN_LASER", "پوست، مو و لیزر", BusinessCategoryGroup.BEAUTY),
    TATTOO("TATTOO", "تتو و میکروپیگمنتیشن", BusinessCategoryGroup.BEAUTY),
    MASSAGE_SPA("MASSAGE_SPA", "ماساژ و اسپا", BusinessCategoryGroup.BEAUTY),

    // ── خدمات حرفه‌ای ──
    CONSULTANT("CONSULTANT", "مشاوره", BusinessCategoryGroup.PROFESSIONAL),
    LAWYER("LAWYER", "وکالت و مشاوره حقوقی", BusinessCategoryGroup.PROFESSIONAL),
    ACCOUNTING("ACCOUNTING", "حسابداری و مالیات", BusinessCategoryGroup.PROFESSIONAL),
    REAL_ESTATE("REAL_ESTATE", "املاک و مستغلات", BusinessCategoryGroup.PROFESSIONAL),
    INSURANCE("INSURANCE", "بیمه", BusinessCategoryGroup.PROFESSIONAL),
    IMMIGRATION("IMMIGRATION", "مهاجرت و ویزا", BusinessCategoryGroup.PROFESSIONAL),

    // ── آموزش و ورزش ──
    TUTORING("TUTORING", "تدریس و کلاس خصوصی", BusinessCategoryGroup.EDUCATION),
    LANGUAGE_SCHOOL("LANGUAGE_SCHOOL", "آموزشگاه زبان", BusinessCategoryGroup.EDUCATION),
    MUSIC_SCHOOL("MUSIC_SCHOOL", "آموزش موسیقی", BusinessCategoryGroup.EDUCATION),
    GYM("GYM", "باشگاه ورزشی و مربی شخصی", BusinessCategoryGroup.EDUCATION),
    YOGA_PILATES("YOGA_PILATES", "یوگا و پیلاتس", BusinessCategoryGroup.EDUCATION),
    DRIVING_SCHOOL("DRIVING_SCHOOL", "آموزشگاه رانندگی", BusinessCategoryGroup.EDUCATION),

    // ── خدمات فنی و تعمیرات ──
    AUTO_SERVICE("AUTO_SERVICE", "تعمیرگاه و خدمات خودرو", BusinessCategoryGroup.TRADES),
    CAR_WASH("CAR_WASH", "کارواش و دیتیلینگ", BusinessCategoryGroup.TRADES),
    HOME_SERVICE("HOME_SERVICE", "خدمات و تعمیرات منزل", BusinessCategoryGroup.TRADES),
    DEVICE_REPAIR("DEVICE_REPAIR", "تعمیر موبایل و لوازم برقی", BusinessCategoryGroup.TRADES),
    TAILORING("TAILORING", "خیاطی و طراحی لباس", BusinessCategoryGroup.TRADES),

    // ── سایر ──
    PHOTOGRAPHY("PHOTOGRAPHY", "عکاسی و آتلیه", BusinessCategoryGroup.OTHER),
    EVENT_SERVICES("EVENT_SERVICES", "تالار و خدمات مراسم", BusinessCategoryGroup.OTHER),
    PET_GROOMING("PET_GROOMING", "آرایش و نگهداری حیوانات", BusinessCategoryGroup.OTHER),
    OTHER("OTHER", "سایر", BusinessCategoryGroup.OTHER);

    companion object {
        fun fromString(value: String?): BusinessCategory {
            return entries.find { it.value == value } ?: OTHER
        }

        /** Categories bucketed by [group], in declaration order — what a
         *  sectioned picker renders from. */
        fun grouped(): Map<BusinessCategoryGroup, List<BusinessCategory>> =
            entries.groupBy { it.group }
    }
}

/** Presentation-only bucket for the category picker; see [BusinessCategory]. */
enum class BusinessCategoryGroup(val persianName: String) {
    HEALTH("سلامت و درمان"),
    BEAUTY("زیبایی و آرایش"),
    PROFESSIONAL("خدمات حرفه‌ای"),
    EDUCATION("آموزش و ورزش"),
    TRADES("خدمات فنی و تعمیرات"),
    OTHER("سایر"),
}

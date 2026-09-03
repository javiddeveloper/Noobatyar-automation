package xyz.sattar.javid.proqueue.domain.model.business

/**
 * Business categories, mirroring `Business.CATEGORY_GROUPS` in
 * backend/business/models.py.
 *
 * This is the *offline fallback* only. The picker fetches the live vocabulary
 * from `GET business/categories/` (see BusinessRepository.getCategoryGroups)
 * and falls back here when the server is unreachable or predates that
 * endpoint, so a category added on the server no longer needs an app release.
 *
 * [fromString] falls back to [OTHER] rather than throwing, so a category this
 * build has never heard of degrades to "سایر" instead of crashing.
 */
enum class BusinessCategory(
    val value: String,
    val persianName: String,
    val group: BusinessCategoryGroup,
) {
    // ── پزشکی و تخصص‌ها ──
    DOCTOR("DOCTOR", "پزشک و کلینیک", BusinessCategoryGroup.MEDICAL),
    GENERAL_PRACTITIONER("GENERAL_PRACTITIONER", "پزشک عمومی", BusinessCategoryGroup.MEDICAL),
    INTERNAL_MEDICINE("INTERNAL_MEDICINE", "داخلی", BusinessCategoryGroup.MEDICAL),
    CARDIOLOGY("CARDIOLOGY", "قلب و عروق", BusinessCategoryGroup.MEDICAL),
    DERMATOLOGY("DERMATOLOGY", "پوست، مو و زیبایی (پزشک)", BusinessCategoryGroup.MEDICAL),
    ORTHOPEDICS("ORTHOPEDICS", "ارتوپدی", BusinessCategoryGroup.MEDICAL),
    PEDIATRICS("PEDIATRICS", "اطفال و کودکان", BusinessCategoryGroup.MEDICAL),
    GYNECOLOGY("GYNECOLOGY", "زنان و زایمان", BusinessCategoryGroup.MEDICAL),
    ENT("ENT", "گوش، حلق و بینی", BusinessCategoryGroup.MEDICAL),
    OPHTHALMOLOGY("OPHTHALMOLOGY", "چشم‌پزشکی", BusinessCategoryGroup.MEDICAL),
    NEUROLOGY("NEUROLOGY", "مغز و اعصاب", BusinessCategoryGroup.MEDICAL),
    UROLOGY("UROLOGY", "اورولوژی", BusinessCategoryGroup.MEDICAL),
    ENDOCRINOLOGY("ENDOCRINOLOGY", "غدد و دیابت", BusinessCategoryGroup.MEDICAL),
    GASTROENTEROLOGY("GASTROENTEROLOGY", "گوارش و کبد", BusinessCategoryGroup.MEDICAL),
    ONCOLOGY("ONCOLOGY", "انکولوژی", BusinessCategoryGroup.MEDICAL),
    SURGERY("SURGERY", "جراحی", BusinessCategoryGroup.MEDICAL),
    PSYCHIATRY("PSYCHIATRY", "روان‌پزشکی", BusinessCategoryGroup.MEDICAL),
    INFERTILITY("INFERTILITY", "ناباروری و IVF", BusinessCategoryGroup.MEDICAL),
    // ── دندان‌پزشکی ──
    DENTIST("DENTIST", "دندان‌پزشکی", BusinessCategoryGroup.DENTAL),
    ORTHODONTICS("ORTHODONTICS", "ارتودنسی", BusinessCategoryGroup.DENTAL),
    DENTAL_IMPLANT("DENTAL_IMPLANT", "ایمپلنت و جراحی دهان", BusinessCategoryGroup.DENTAL),
    PEDIATRIC_DENTISTRY("PEDIATRIC_DENTISTRY", "دندان‌پزشکی کودکان", BusinessCategoryGroup.DENTAL),
    // ── سلامت و درمان ──
    PSYCHOLOGY("PSYCHOLOGY", "روان‌شناسی و مشاوره", BusinessCategoryGroup.HEALTH),
    PHYSIOTHERAPY("PHYSIOTHERAPY", "فیزیوتراپی و توان‌بخشی", BusinessCategoryGroup.HEALTH),
    NUTRITION("NUTRITION", "تغذیه و رژیم‌درمانی", BusinessCategoryGroup.HEALTH),
    LABORATORY("LABORATORY", "آزمایشگاه", BusinessCategoryGroup.HEALTH),
    RADIOLOGY("RADIOLOGY", "رادیولوژی و سونوگرافی", BusinessCategoryGroup.HEALTH),
    OPTOMETRY("OPTOMETRY", "بینایی‌سنجی و عینک", BusinessCategoryGroup.HEALTH),
    SPEECH_THERAPY("SPEECH_THERAPY", "گفتاردرمانی", BusinessCategoryGroup.HEALTH),
    OCCUPATIONAL_THERAPY("OCCUPATIONAL_THERAPY", "کاردرمانی", BusinessCategoryGroup.HEALTH),
    MIDWIFERY("MIDWIFERY", "مامایی و سلامت بانوان", BusinessCategoryGroup.HEALTH),
    NURSING("NURSING", "پرستاری و خدمات در منزل", BusinessCategoryGroup.HEALTH),
    PHARMACY("PHARMACY", "داروخانه", BusinessCategoryGroup.HEALTH),
    VETERINARY("VETERINARY", "دامپزشکی", BusinessCategoryGroup.HEALTH),
    // ── آرایش و زیبایی ──
    BEAUTY_SALON("BEAUTY_SALON", "آرایشگاه و سالن زیبایی", BusinessCategoryGroup.BEAUTY),
    WOMENS_SALON("WOMENS_SALON", "سالن زیبایی زنانه", BusinessCategoryGroup.BEAUTY),
    BARBERSHOP("BARBERSHOP", "آرایشگاه مردانه", BusinessCategoryGroup.BEAUTY),
    HAIR_SALON("HAIR_SALON", "سالن مو (رنگ و کراتین)", BusinessCategoryGroup.BEAUTY),
    HAIR_TRANSPLANT("HAIR_TRANSPLANT", "کاشت مو و ابرو", BusinessCategoryGroup.BEAUTY),
    MAKEUP("MAKEUP", "میکاپ و گریم", BusinessCategoryGroup.BEAUTY),
    BRIDAL("BRIDAL", "عروس و خدمات مجلسی", BusinessCategoryGroup.BEAUTY),
    NAIL_SALON("NAIL_SALON", "سالن ناخن", BusinessCategoryGroup.BEAUTY),
    EYEBROW_LASH("EYEBROW_LASH", "ابرو، مژه و میکروبلیدینگ", BusinessCategoryGroup.BEAUTY),
    SKIN_LASER("SKIN_LASER", "پوست و لیزر", BusinessCategoryGroup.BEAUTY),
    TATTOO("TATTOO", "تتو و میکروپیگمنتیشن", BusinessCategoryGroup.BEAUTY),
    MASSAGE_SPA("MASSAGE_SPA", "ماساژ و اسپا", BusinessCategoryGroup.BEAUTY),
    SLIMMING("SLIMMING", "لاغری و تناسب اندام", BusinessCategoryGroup.BEAUTY),
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

        /** Categories bucketed by [group], in declaration order. */
        fun grouped(): Map<BusinessCategoryGroup, List<BusinessCategory>> =
            entries.groupBy { it.group }
    }
}

/** Presentation-only bucket for the category picker; see [BusinessCategory]. */
enum class BusinessCategoryGroup(val persianName: String) {
    MEDICAL("پزشکی و تخصص‌ها"),
    DENTAL("دندان‌پزشکی"),
    HEALTH("سلامت و درمان"),
    BEAUTY("آرایش و زیبایی"),
    PROFESSIONAL("خدمات حرفه‌ای"),
    EDUCATION("آموزش و ورزش"),
    TRADES("خدمات فنی و تعمیرات"),
    OTHER("سایر"),
}

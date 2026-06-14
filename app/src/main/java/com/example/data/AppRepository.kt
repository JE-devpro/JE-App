package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppRepository(private val appDao: AppDao) {

    val allActivities: Flow<List<EcoActivity>> = appDao.getAllActivities()
    val carnetProfile: Flow<CarnetProfile?> = appDao.getProfileFlow()
    val allArticles: Flow<List<EcoArticle>> = appDao.getAllArticles()
    val allMembers: Flow<List<Member>> = appDao.getAllMembers()
    val allEnrollments: Flow<List<EcoEnrollment>> = appDao.getAllEnrollments()
    val allNotifications: Flow<List<EcoNotification>> = appDao.getAllNotifications()

    fun getMemberByEmailFlow(email: String): Flow<Member?> = appDao.getMemberByEmailFlow(email)
    suspend fun getMemberByEmailDirect(email: String): Member? = appDao.getMemberByEmailDirect(email)
    suspend fun insertMember(member: Member) = appDao.insertMember(member)
    suspend fun updateMember(member: Member) = appDao.updateMember(member)
    suspend fun deleteMemberByEmail(email: String) = appDao.deleteMemberByEmail(email)

    fun getEnrollmentsByActivityFlow(activityId: Int): Flow<List<EcoEnrollment>> = appDao.getEnrollmentsByActivityFlow(activityId)
    suspend fun getEnrollmentDirect(activityId: Int, memberEmail: String): EcoEnrollment? = appDao.getEnrollmentDirect(activityId, memberEmail)
    suspend fun insertEnrollment(enrollment: EcoEnrollment) = appDao.insertEnrollment(enrollment)
    suspend fun deleteEnrollment(activityId: Int, memberEmail: String) = appDao.deleteEnrollment(activityId, memberEmail)

    suspend fun insertNotification(notification: EcoNotification) = appDao.insertNotification(notification)
    suspend fun deleteNotificationById(id: Int) = appDao.deleteNotificationById(id)
    suspend fun deleteExpiredNotifications(currentTime: Long) = appDao.deleteExpiredNotifications(currentTime)
    suspend fun markAllNotificationsAsRead() = appDao.markAllNotificationsAsRead()
    suspend fun clearAllNotifications() = appDao.clearAllNotifications()
    suspend fun clearNonGlobalNotifications() = appDao.clearNonGlobalNotifications()

    suspend fun insertActivity(activity: EcoActivity) = appDao.insertActivity(activity)
    
    suspend fun updateActivity(activity: EcoActivity) = appDao.updateActivity(activity)

    suspend fun deleteActivity(id: Int) = appDao.deleteActivityById(id)

    suspend fun updateProfile(profile: CarnetProfile) = appDao.insertOrUpdateProfile(profile)

    suspend fun insertArticle(article: EcoArticle) = appDao.insertArticle(article)

    suspend fun deleteArticle(id: Int) = appDao.deleteArticleById(id)

    suspend fun insertActivities(activities: List<EcoActivity>) = appDao.insertActivities(activities)
    suspend fun insertMembers(members: List<Member>) = appDao.insertMembers(members)
    suspend fun insertEnrollments(enrollments: List<EcoEnrollment>) = appDao.insertEnrollments(enrollments)

    suspend fun overwriteActivities(list: List<EcoActivity>) {
        appDao.clearAllActivities()
        appDao.insertActivities(list)
    }

    suspend fun overwriteMembers(list: List<Member>) {
        appDao.clearAllMembers()
        appDao.insertMembers(list)
    }

    suspend fun overwriteNotifications(list: List<EcoNotification>) {
        appDao.clearAllNotifications()
        if (list.isNotEmpty()) {
            list.forEach { appDao.insertNotification(it) }
        }
    }

    suspend fun overwriteEnrollments(list: List<EcoEnrollment>) {
        appDao.clearAllEnrollments()
        appDao.insertEnrollments(list)
    }

    suspend fun overwriteArticles(list: List<EcoArticle>) {
        appDao.clearAllArticles()
        appDao.insertArticles(list)
    }

    suspend fun checkAndSeedData() {
        // 1. Seed Profile if empty (deprecated fallback, Member table is the primary now)
        val currentProfile = appDao.getProfileDirect()
        if (currentProfile == null) {
            val defaultProfile = CarnetProfile(
                id = 1,
                fullName = "Sofía Moreno",
                association = "Jóvenes y Ecosistemas Latinoamérica",
                role = "Coordinadora Regional",
                country = "Colombia",
                emojiAvatar = "🐆", // Jaguar
                points = 120,
                credentialId = "LA-JE-44910",
                joinDate = "15/04/2026"
            )
            appDao.insertOrUpdateProfile(defaultProfile)
        }

        // 1.5 Seed Members table if empty
        val currentMembers = appDao.getAllMembers().firstOrNull() ?: emptyList()
        if (currentMembers.isEmpty()) {
            val seedMembers = listOf(
                Member(
                    email = "coordinador@je.org",
                    password = "JE2026",
                    fullName = "Director General JE-App",
                    role = "Director General",
                    association = "Central Jóvenes y Ecosistemas",
                    country = "América Latina",
                    emojiAvatar = "🦅",
                    points = 500,
                    credentialId = "JE-DIR-01",
                    isAdmin = true
                ),
                Member(
                    email = "miembro1@je.org",
                    password = "miembro1",
                    fullName = "Sofía Moreno",
                    role = "Coordinadora Regional",
                    association = "Jóvenes y Ecosistemas Latinoamérica",
                    country = "Colombia",
                    emojiAvatar = "🐆",
                    points = 120,
                    credentialId = "LA-JE-44910"
                ),
                Member(
                    email = "miembro2@je.org",
                    password = "miembro2",
                    fullName = "Mateo Silva",
                    role = "Embajador JE-RAM",
                    association = "Jóvenes y Ecosistemas Latinoamérica",
                    country = "Perú",
                    emojiAvatar = "🐸",
                    points = 50,
                    credentialId = "LA-ECO-88741"
                ),
                Member(
                    email = "miembro3@je.org",
                    password = "miembro3",
                    fullName = "Lucía Paz",
                    role = "Líder JE-Mental",
                    association = "Jóvenes y Ecosistemas Latinoamérica",
                    country = "México",
                    emojiAvatar = "🦉",
                    points = 75,
                    credentialId = "LA-ECO-12345"
                )
            )
            appDao.insertMembers(seedMembers)
        }

        // 2. Seed Activities if empty
        val currentActivities = allActivities.firstOrNull() ?: emptyList()
        if (currentActivities.isEmpty()) {
            val seedActivities = listOf(
                EcoActivity(
                    title = "Gran Siembratón Yungas del Amazonas",
                    description = "Únete a un esfuerzo transfronterizo de restauración forestal en la transición andino-amazónica. Plantaremos más de 300 especies nativas (cedros, palmas e ingas) para restablecer corredores biológicos clave de aves y mamíferos.",
                    date = "2026-06-05",
                    location = "Yungas, Bolivia & Perú",
                    country = "Región Amazónica",
                    category = "JE-Ambiente",
                    organizer = "Jóvenes por la Selva",
                    eventType = "Voluntariado"
                ),
                EcoActivity(
                    title = "Campaña de Limpieza de Manglares y Microplásticos",
                    description = "Restauración de zonas de anidación acuática en el Golfo de Darién. Los manglares capturan inmensas cantidades de carbono y cobijan a alevines de peces comerciales. Retiraremos redes fantasma y plásticos de un solo uso.",
                    date = "2026-06-14",
                    location = "Bahía Caimán, Urabá",
                    country = "Colombia",
                    category = "JE-Ambiente",
                    organizer = "JE-Latinoamérica",
                    eventType = "Voluntariado"
                ),
                EcoActivity(
                    title = "Taller: Mapeo Social de Conflictos Ambientales",
                    description = "Aprende herramientas de Sistemas de Información Geográfica (SIG) libres y metodologías participativas para que las comunidades de jóvenes puedan documentar y denunciar la deforestación ilegal.",
                    date = "2026-06-20",
                    location = "Virtual (Plataforma Zoom)",
                    country = "América Latina (Online)",
                    category = "JE-Visual",
                    organizer = "JE-Latinoamérica",
                    eventType = "Actividad"
                ),
                EcoActivity(
                    title = "Charla: Restauración de Humedales Altoandinos",
                    description = "Encuentro virtual educativo donde expertos compartirán las mejores prácticas para conservar y restaurar las turberas y humedales de altura que regulan el caudal de agua andina.",
                    date = "2026-06-10",
                    location = "Virtual (Plataforma Zoom)",
                    country = "América Latina (Online)",
                    category = "JE-Ambiente",
                    organizer = "JE-Latinoamérica",
                    eventType = "Educación"
                ),
                EcoActivity(
                    title = "Feria Educativa de Eco-Alternativas Urbanas",
                    description = "Taller práctico sobre compostaje doméstico, recolección de agua pluvial y huertos urbanos comunitarios para jóvenes activistas en entornos de alta densidad urbana.",
                    date = "2026-06-25",
                    location = "Parque del Centenario",
                    country = "México",
                    category = "JE-Ambiente",
                    organizer = "Colectivo Semillas",
                    eventType = "Educación"
                ),
                EcoActivity(
                    title = "Foro Latinoamericano: Leyes de Cambio Climático",
                    description = "Foro interactivo sobre los avances parlamentarios y los acuerdos internacionales para la protección de la biodiversidad local del cono sur.",
                    date = "2026-07-02",
                    location = "Aula Magna Forestal",
                    country = "Chile",
                    category = "JE-Ambiente",
                    organizer = "Líderes Ambientales Cono Sur",
                    eventType = "Asamblea general"
                )
            )
            appDao.insertActivities(seedActivities)
        }

        // 3. Seed Articles / Novedades supporting "Una Sola Salud" and JE projects
        val currentArticles = allArticles.firstOrNull() ?: emptyList()
        if (currentArticles.isEmpty()) {
            val seedArticles = listOf(
                EcoArticle(
                    title = "Resistencia a los Antimicrobianos (JE-RAM) y la Salud Ecosistémica",
                    content = "La resistencia a los antimicrobianos (RAM) es una de las mayores amenazas silenciosas para la salud global. En JE-RAM analizamos cómo la descarga de antibióticos en aguas residuales y el uso desmedido en la ganadería aceleran la aparición de superbacterias resistentes. Una Sola Salud nos recuerda que la salud de los humanos, animales y ecosistemas están indisolublemente vinculadas; debemos cuidar la pureza de nuestros ríos para evitar la propagación de genes de resistencia bacteriana.",
                    category = "JE-RAM",
                    region = "América Latina",
                    publishDate = "22/05/2026",
                    isFeatured = true
                ),
                EcoArticle(
                    title = "Bienestar Emocional en el Activismo: JE-Mental y la Eco-Ansiedad",
                    content = "En JE-Mental promovemos la canalización de la eco-ansiedad en acción colectiva y constructiva. Estar en contacto activo con la naturaleza reduce de forma comprobada el cortisol, atenúa la presión arterial y mejora nuestra resiliencia cognitiva. Dado que el activismo en primera línea puede generar agotamiento y frustración, realizamos círculos de apoyo emocional constantes para cuidar la salud mental de las y los líderes eco-sociales de nuestra américa.",
                    category = "JE-Mental",
                    region = "México & Colombia",
                    publishDate = "23/05/2026",
                    isFeatured = true
                ),
                EcoArticle(
                    title = "Sintoniza One Health: JE-Podcast Episodio 12 ya está al Aire",
                    content = "¡Hemos lanzado un nuevo capítulo del JE-Podcast! En esta ocasión conversamos con investigadores andinos sobre la virología ambiental y el impacto de la deforestación de selvas tropicales en los brotes zoonóticos. Sintoniza el podcast en Spotify, YouTube o directo en nuestro portal, y comparte estos conocimientos interactivos.",
                    category = "JE-Podcast",
                    region = "Andina",
                    publishDate = "21/05/2026",
                    isFeatured = false
                ),
                EcoArticle(
                    title = "JE-Ambiente en Acción: Restauración Ecológica y Monitoreo Hídrico",
                    content = "Las brigadas juveniles del proyecto JE-Ambiente ya se encuentran en campo realizando muestreos microbiológicos de agua dulce en zonas rurales vulnerables. Al empoderar a las comunidades para evaluar la calidad de su agua y plantar especies arbóreas protectoras, defendemos la salud de toda la cuenca hidrográfica contra contaminantes emergentes.",
                    category = "JE-Ambiente",
                    region = "Centroamérica",
                    publishDate = "20/05/2026",
                    isFeatured = false
                ),
                EcoArticle(
                    title = "Creatividad Colectiva con JE-Visual: Mensajes de Impacto Real",
                    content = "La rama JE-Visual lanza la nueva caja de herramientas creativas: plantillas interactivas, infografías sobre biodiversidad terrestre y videos movilizadores para su uso libre por colectivos juveniles. Traducimos hallazgos científicos abstractos de One Health en arte visual, permitiendo generar campañas educativas de alto impacto que inspiran cambios de comportamiento.",
                    category = "JE-Visual",
                    region = "Cono Sur",
                    publishDate = "19/05/2026",
                    isFeatured = false
                )
            )
            appDao.insertArticles(seedArticles)
        }
    }
}

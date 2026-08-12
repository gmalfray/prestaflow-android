package com.rebuildit.prestaflow.data.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.notifications.FcmRegistrationManager
import com.rebuildit.prestaflow.core.notifications.NotificationChannels
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/** Masque pour dériver un ID de notification positif (Int) depuis un timestamp epoch ms. */
private const val NOTIFICATION_ID_MASK = 0x7FFFFFFFL

/**
 * Décalage réservant un sous-espace d'ID de notification disjoint aux fils SAV et aux avis
 * (cf. showNotification) — largement au-delà des identifiants de commande/produit d'une petite
 * boutique, tout en restant loin d'Int.MAX_VALUE.
 */
private const val SECONDARY_ID_OFFSET = 1_000_000_000L

@AndroidEntryPoint
class PrestaFlowFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var ordersRepository: OrdersRepository

    @Inject
    lateinit var registrationManager: FcmRegistrationManager

    private val job = SupervisorJob()

    @Suppress("InjectDispatcher") // FirebaseMessagingService est un service Android non injectable par Hilt constructor
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("New FCM token received")
        // Registration is routed through the notifications/devices endpoint.
        registrationManager.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("Message received from: ${message.from}")

        val event = message.data["event"]
        val orderId = message.data["order_id"]?.toLongOrNull()
        val productId = message.data["product_id"]?.toLongOrNull()
        val threadId = message.data["thread_id"]?.toLongOrNull()
        val reviewId = message.data["review_id"]?.toLongOrNull()

        // Rafraîchit la commande référencée par le push (si présente).
        if (orderId != null) {
            scope.launch {
                try {
                    ordersRepository.refreshOrder(orderId)
                } catch (e: IOException) {
                    Timber.e(e, "Network error refreshing order $orderId from push")
                } catch (e: HttpException) {
                    Timber.e(e, "HTTP error refreshing order $orderId from push (code=${e.code()})")
                }
            }
        }

        // Affichage foreground : routage par event → canal (et donc son) adapté.
        // En arrière-plan, le système gère lui-même via default_notification_channel_id (manifeste).
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (title != null || body != null) {
            showNotification(
                event = event,
                title = title,
                body = body,
                orderId = orderId,
                productId = productId,
                threadId = threadId,
                reviewId = reviewId,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    // ── Affichage ──────────────────────────────────────────────────────────────

    // 7 paramètres : event + title + body + les 4 identifiants de destination mutuellement
    // exclusifs (order/product/thread/review), un seul non-null par push selon l'event reçu.
    @Suppress("LongParameterList")
    private fun showNotification(
        event: String?,
        title: String?,
        body: String?,
        orderId: Long?,
        productId: Long? = null,
        threadId: Long? = null,
        reviewId: Long? = null,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val channelId = NotificationChannels.channelForEvent(event)

        val builder =
            NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title ?: applicationContext.getString(R.string.notif_sale_default_title))
                .setContentText(body)
                .setAutoCancel(true)
        applyChannelPresentation(builder, channelId)

        // Tap sur la notification → ouvre directement la bonne destination (foreground uniquement ;
        // les notifs background sont gérées par le système sans passer par onMessageReceived). Un
        // push commande référence order_id, un push stock faible product_id, un push message SAV
        // thread_id, un push avis à modérer review_id — un seul de ces quatre par notification.
        applyContentIntent(builder, orderId, productId, threadId, reviewId)

        val notificationId = computeNotificationId(orderId, productId, threadId, reviewId)
        NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
    }

    /** Priorité et son explicite (Android < 8) selon le canal — cf. showNotification. */
    private fun applyChannelPresentation(
        builder: NotificationCompat.Builder,
        channelId: String,
    ) {
        when (channelId) {
            NotificationChannels.CHANNEL_SALES ->
                builder
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    // Son explicite pour Android < 8 (sur 8+ c'est le canal qui décide).
                    .setSound(NotificationChannels.cashRegisterSoundUri(applicationContext))
            NotificationChannels.CHANNEL_PAYMENT_ERROR ->
                // Panne d'encaissement : priorité haute et son dédié — surtout pas la caisse, qui
                // signifie « vente encaissée », l'inverse du message.
                builder
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSound(NotificationChannels.paymentAlertSoundUri(applicationContext))
            NotificationChannels.CHANNEL_SAV_MESSAGE ->
                // Une cliente attend une réponse : priorité haute et son dédié — surtout pas la
                // caisse, qui signifie « vente encaissée », l'inverse du message.
                builder
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSound(NotificationChannels.savMessageAlertSoundUri(applicationContext))
            else ->
                builder
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    // Son système par défaut pour Android < 8.
                    .setDefaults(NotificationCompat.DEFAULT_SOUND)
        }
    }

    /** Sélectionne le deep link à porter par le [PendingIntent] selon l'identifiant présent. */
    @Suppress("LongParameterList") // 4 identifiants mutuellement exclusifs, cf. showNotification
    private fun applyContentIntent(
        builder: NotificationCompat.Builder,
        orderId: Long?,
        productId: Long?,
        threadId: Long?,
        reviewId: Long?,
    ) {
        when {
            orderId != null -> builder.setContentIntent(buildOrderDeepLinkIntent(orderId))
            productId != null -> builder.setContentIntent(buildProductDeepLinkIntent(productId))
            threadId != null -> builder.setContentIntent(buildSavThreadDeepLinkIntent(threadId))
            reviewId != null -> builder.setContentIntent(buildReviewsDeepLinkIntent())
        }
    }

    /**
     * Commande, produit, fil SAV et avis ont chacun leur propre espace d'ID côté boutique ; on les
     * répartit dans des sous-espaces disjoints pour que leurs notifications tray restent
     * indépendantes et remplaçables individuellement (jamais deux entités différentes qui se
     * recouvrent parce qu'elles partagent le même entier) : commande = tel quel, produit = négatif,
     * fil SAV = décalé d'un grand offset, avis = décalé du même offset et négatif.
     */
    private fun computeNotificationId(
        orderId: Long?,
        productId: Long?,
        threadId: Long?,
        reviewId: Long?,
    ): Int =
        orderId?.toInt()
            ?: productId?.let { -it.toInt() }
            ?: threadId?.let { (SECONDARY_ID_OFFSET + it).toInt() }
            ?: reviewId?.let { -(SECONDARY_ID_OFFSET + it).toInt() }
            ?: (System.currentTimeMillis() and NOTIFICATION_ID_MASK).toInt()

    /**
     * Construit un [PendingIntent] qui ouvre [MainActivity] sur le détail de la commande [orderId].
     *
     * L'URI `prestaflow://orders/{orderId}?fromNotification=true` est déclarée comme deep link dans
     * [PrestaFlowNavGraph] et dans le manifeste (la partie query n'affecte pas le matching du
     * manifeste, qui ne filtre que scheme/host). Le flag `fromNotification=true` permet à
     * `OrderDetailViewModel` de marquer SEULE cette commande comme vue sans avancer le repère de la
     * pastille (cf. son Javadoc) — toute ouverture via ce PendingIntent vient forcément d'une
     * notification. Le flag [Intent.FLAG_ACTIVITY_SINGLE_TOP] garantit que si l'Activity est déjà
     * active, `onNewIntent` est appelé plutôt qu'une nouvelle instance.
     */
    private fun buildOrderDeepLinkIntent(orderId: Long): PendingIntent {
        val uri = Uri.parse(orderDeepLinkUriString(orderId))
        val intent =
            Intent(Intent.ACTION_VIEW, uri, applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        return PendingIntent.getActivity(
            applicationContext,
            orderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Construit un [PendingIntent] qui ouvre [MainActivity] sur la fiche du produit [productId].
     *
     * L'URI `prestaflow://products/{productId}` est déclarée comme deep link dans
     * [PrestaFlowNavGraph] et dans le manifeste. Utilisé pour les pushs "stock faible"
     * (`event = stock.low`), qui référencent un produit et non une commande.
     */
    private fun buildProductDeepLinkIntent(productId: Long): PendingIntent {
        val uri = Uri.parse(productDeepLinkUriString(productId))
        val intent =
            Intent(Intent.ACTION_VIEW, uri, applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        return PendingIntent.getActivity(
            applicationContext,
            -productId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Construit un [PendingIntent] qui ouvre [MainActivity] sur le fil SAV [threadId].
     *
     * L'URI `prestaflow://sav/{threadId}` est déclarée comme deep link dans [PrestaFlowNavGraph]
     * et dans le manifeste. Utilisé pour les pushs "nouveau message SAV" (`event = sav.message`),
     * qui référencent le fil sur lequel la cliente vient d'écrire.
     */
    private fun buildSavThreadDeepLinkIntent(threadId: Long): PendingIntent {
        val uri = Uri.parse(savThreadDeepLinkUriString(threadId))
        val intent =
            Intent(Intent.ACTION_VIEW, uri, applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        return PendingIntent.getActivity(
            applicationContext,
            threadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Construit un [PendingIntent] qui ouvre [MainActivity] sur le sous-onglet Avis de l'onglet
     * Clients (file de modération).
     *
     * L'URI `prestaflow://clients?section=reviews` est déclarée comme deep link dans
     * [PrestaFlowNavGraph] et dans le manifeste. Utilisé pour les pushs "avis à modérer"
     * (`event = review.pending`) — pas d'écran dédié par avis : la file de modération elle-même
     * est la destination, comme dans l'app.
     */
    private fun buildReviewsDeepLinkIntent(): PendingIntent {
        val uri = Uri.parse(reviewsDeepLinkUriString())
        val intent =
            Intent(Intent.ACTION_VIEW, uri, applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        return PendingIntent.getActivity(
            applicationContext,
            REVIEWS_DEEP_LINK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/**
 * Code de requête du [PendingIntent] "avis à modérer" — constant car cette destination ne porte
 * pas d'identifiant individuel (cf. [PrestaFlowFirebaseMessagingService.buildReviewsDeepLinkIntent]).
 */
private const val REVIEWS_DEEP_LINK_REQUEST_CODE = -2

// ── URI de deep link (fonctions pures, testables sans Robolectric) ────────────────────────────
// Source unique de vérité pour les URI construites par les build*DeepLinkIntent ci-dessus. DOIVENT
// rester synchrones avec les navDeepLink déclarés dans PrestaFlowNavGraph et les intent-filters du
// manifeste — cf. PrestaFlowFirebaseMessagingServiceDeepLinkTest.

internal fun orderDeepLinkUriString(orderId: Long): String = "prestaflow://orders/$orderId?fromNotification=true"

internal fun productDeepLinkUriString(productId: Long): String = "prestaflow://products/$productId"

internal fun savThreadDeepLinkUriString(threadId: Long): String = "prestaflow://sav/$threadId"

// Reste une fonction (plutôt qu'une constante) pour rester au même patron que les trois autres
// ci-dessus, même sans paramètre : "avis à modérer" ne référence aucun identifiant individuel.
@Suppress("FunctionOnlyReturningConstant")
internal fun reviewsDeepLinkUriString(): String = "prestaflow://clients?section=reviews"

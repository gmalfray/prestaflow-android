package com.rebuildit.prestaflow.ui.dashboard

/** État de chargement d'un widget à données réseau propres, indépendant des autres. */
sealed interface WidgetLoad<out T> {
    /** Pas encore demandé (widget masqué depuis le début de la session). */
    data object Idle : WidgetLoad<Nothing>

    data object Loading : WidgetLoad<Nothing>

    data object Error : WidgetLoad<Nothing>

    /** Fonctionnalité absente de la boutique (ex. module d'avis, HTTP 409) : le widget ne s'affiche pas. */
    data object Unavailable : WidgetLoad<Nothing>

    data class Data<T>(val value: T) : WidgetLoad<T>
}

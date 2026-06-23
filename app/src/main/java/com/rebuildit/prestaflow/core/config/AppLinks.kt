package com.rebuildit.prestaflow.core.config

/**
 * Liens externes centralisés de l'application.
 *
 * Ces URLs sont des points d'entrée vers des ressources web liées à PrestaFlow.
 * Les modifier ici suffit pour les répercuter partout dans l'app.
 */
object AppLinks {
    /**
     * Page des releases GitHub du module Rebuild Connector.
     * Le fichier `rebuildconnector.zip` est joint à chaque tag via le workflow CI.
     */
    const val MODULE_INSTALL_URL = "https://github.com/gmalfray/rebuild-connector/releases/latest"
}
